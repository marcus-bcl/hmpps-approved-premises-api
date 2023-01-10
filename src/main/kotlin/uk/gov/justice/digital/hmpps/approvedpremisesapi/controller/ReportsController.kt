package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.ReportsApiDelegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BookingsReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.ReportService
import java.io.ByteArrayOutputStream
import java.util.UUID

@Service
class ReportsController(
  private val reportService: ReportService,
) : ReportsApiDelegate {
  override fun reportsBookingsGet(xServiceName: ServiceName, probationRegionId: UUID?): ResponseEntity<Resource> {
    val properties = BookingsReportProperties(xServiceName, probationRegionId)
    val outputStream = ByteArrayOutputStream()

    reportService.createBookingsReport(properties, outputStream)

    return ResponseEntity.ok(InputStreamResource(outputStream.toByteArray().inputStream()))
  }
}