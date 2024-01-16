package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller.cas2

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.cas2.ReportsCas2Delegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas2.ReportsService
import java.io.ByteArrayOutputStream

@Service("Cas2ReportsController")
class ReportsController(private val reportService: ReportsService) : ReportsCas2Delegate {

  override fun reportsExampleReportGet(): ResponseEntity<Resource> {
    val outputStream = ByteArrayOutputStream()
    reportService.createCas2ExampleReport(outputStream)
    return ResponseEntity.ok(InputStreamResource(outputStream.toByteArray().inputStream()))
  }

  override fun reportsReportNameGet(reportName: String): ResponseEntity<Resource> {
    val outputStream = ByteArrayOutputStream()
    when (reportName) {
      "submitted-applications" -> reportService.createSubmittedApplicationsReport(outputStream)
      "application-status-updates" -> reportService.createApplicationStatusUpdatesReport(outputStream)
      "unsubmitted-applications" -> reportService.createUnsubmittedApplicationsReport(outputStream)
    }
    return ResponseEntity.ok(InputStreamResource(outputStream.toByteArray().inputStream()))
  }
}
