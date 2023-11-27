package uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.cas2

import com.microsoft.applicationinsights.boot.dependencies.apachecommons.io.FileUtils
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.NomisUserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.NomisUserRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.SeedJob
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas2.JsonSchemaService
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID

class Cas2ApplicationsSeedJob(
  fileName: String,
  private val repository: Cas2ApplicationRepository,
  private val userRepository: NomisUserRepository,
  private val jsonSchemaService: JsonSchemaService,
) : SeedJob<Cas2ApplicationSeedCsvRow>(
  id = UUID.randomUUID(),
  fileName = fileName,
  requiredHeaders = setOf("id", "nomsNumber", "crn", "state", "createdBy", "createdAt", "submittedAt", "statusUpdates", "location"),
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun deserializeRow(columns: Map<String, String>) = Cas2ApplicationSeedCsvRow(
    id = UUID.fromString(columns["id"]!!.trim()),
    nomsNumber = columns["nomsNumber"]!!.trim(),
    crn = columns["crn"]!!.trim(),
    state = columns["state"]!!.trim(),
    createdBy = columns["createdBy"]!!.trim(),
    createdAt = OffsetDateTime.parse(columns["createdAt"]),
    submittedAt = parseDateIfNotNull(emptyToNull(columns["submittedAt"])),
    statusUpdates = columns["statusUpdates"]!!.trim(),
    location = columns["location"]!!.trim(),
  )

  override fun processRow(row: Cas2ApplicationSeedCsvRow) {
    log.info("Setting up Application id ${row.id}")

    if (repository.findById(row.id).isPresent()) {
      return log.info("Skipping ${row.id}: already seeded")
    }

    val applicant = userRepository.findByNomisUsername(row.createdBy) ?: throw RuntimeException("Could not find applicant with nomisUsername ${row.createdBy}")

    try {
      createApplication(row, applicant)
    } catch (exception: Exception) {
      throw RuntimeException("Could not create application ${row.id}", exception)
    }
  }

  private fun createApplication(row: Cas2ApplicationSeedCsvRow, applicant: NomisUserEntity) {
    repository.save(
      Cas2ApplicationEntity(
        id = row.id,
        crn = row.crn,
        nomsNumber = row.nomsNumber,
        createdAt = row.createdAt,
        createdByUser = applicant,
        data = dataFor(row.state),
        document = "{}",
        submittedAt = row.submittedAt,
        schemaVersion = jsonSchemaService.getNewestSchema(Cas2ApplicationJsonSchemaEntity::class.java),
        schemaUpToDate = true,
      ),
    )
  }

  private fun dataFor(state: String): String {
    if (state != "NOT_STARTED") {
      return randomDataFixture()
    }
    return "{}"
  }

  private fun randomDataFixture(): String {
    val path = "src/main/resources/db/seed/local+dev+test/cas2_application_data"
    val randomNumber = 1
    return FileUtils.readFileToString(
      File("$path/data_$randomNumber.json"),
      "UTF-8",
    )
  }

  private fun emptyToNull(value: String?) = value?.ifBlank { null }
  private fun parseDateIfNotNull(date: String?) = date?.let { OffsetDateTime.parse(it) }
}

data class Cas2ApplicationSeedCsvRow(
  val id: UUID,
  val nomsNumber: String,
  val crn: String,
  val state: String, // NOT_STARTED | IN-PROGRESS | SUBMITTED | IN_REVIEW
  val createdBy: String,
  val createdAt: OffsetDateTime,
  val submittedAt: OffsetDateTime?,
  val statusUpdates: String,
  val location: String,
)
