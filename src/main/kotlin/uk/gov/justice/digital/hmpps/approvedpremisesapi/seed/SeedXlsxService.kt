package uk.gov.justice.digital.hmpps.approvedpremisesapi.seed

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.SeedFromExcelFileType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.config.SeedConfig
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.cas1.Cas1SeedPremisesFromSiteSurveyXlsxJob
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.cas1.Cas1SeedRoomsFromSiteSurveyXlsxJob
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

@Service
class SeedXlsxService(
  private val seedConfig: SeedConfig,
  private val applicationContext: ApplicationContext,
  private val transactionTemplate: TransactionTemplate,
  private val seedLogger: SeedLogger,
) {

  val filePrefix = seedConfig.filePrefix

  @SuppressWarnings("TooGenericExceptionThrown", "TooGenericExceptionCaught")
  fun seedFile(excelSeedFileType: SeedFromExcelFileType, filename: String) {
    try {
      seedLogger.info("Starting seed request: $excelSeedFileType - $filename")

      validateFileName(filename)

      val file = File("${seedConfig.filePrefix}/$filename")

      val job = when (excelSeedFileType) {
        SeedFromExcelFileType.CAS1_IMPORT_SITE_SURVEY_ROOMS -> getBean(Cas1SeedRoomsFromSiteSurveyXlsxJob::class)
        SeedFromExcelFileType.CAS1_IMPORT_SITE_SURVEY_PREMISES -> getBean(Cas1SeedPremisesFromSiteSurveyXlsxJob::class)
      }

      val seedStarted = LocalDateTime.now()

      transactionTemplate.executeWithoutResult { processExcelJob(job, file) }

      val timeTaken = ChronoUnit.MILLIS.between(seedStarted, LocalDateTime.now())
      seedLogger.info("Excel seed request complete. Took $timeTaken millis")
    } catch (exception: Throwable) {
      seedLogger.error("Unable to complete Excel seed job for $filename", exception)
    }
  }

  private fun validateFileName(filename: String) {
    if (filename.contains("/") || filename.contains("\\")) {
      error("Invalid path. Should be the name of a file/directory within $filePrefix. Sub directories are not allowed")
    }
  }

  private fun <T : Any> getBean(clazz: KClass<T>) = applicationContext.getBean(clazz.java)

  @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
  private fun processExcelJob(job: ExcelSeedJob, file: File) {
    seedLogger.info("Processing XLSX file ${file.absolutePath}")
    try {
      job.processXlsx(file)
    } catch (exception: Exception) {
      throw RuntimeException("Unable to process XLSX file", exception)
    }
  }
}
