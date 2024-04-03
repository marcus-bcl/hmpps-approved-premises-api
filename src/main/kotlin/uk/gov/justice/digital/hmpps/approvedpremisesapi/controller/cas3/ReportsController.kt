package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller.cas3

import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.cas3.ReportsCas3Delegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas3ReportType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas3ReportType.bedOccupancy
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas3ReportType.bedUsage
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas3ReportType.booking
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas3ReportType.referral
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.problem.BadRequestProblem
import uk.gov.justice.digital.hmpps.approvedpremisesapi.problem.ForbiddenProblem
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BedUsageReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BedUtilisationReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BookingsReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.TransitionalAccommodationReferralReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserAccessService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas3.Cas3ReportService
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val MAXIMUM_REPORT_DURATION_IN_MONTHS = 3

@Service("Cas3ReportsController")
class ReportsController(
  private val userAccessService: UserAccessService,
  private val cas3ReportService: Cas3ReportService,
) : ReportsCas3Delegate {

  override fun reportsReferralsGet(
    xServiceName: ServiceName,
    year: Int,
    month: Int,
    probationRegionId: UUID?,
  ): ResponseEntity<Resource> {
    if (!userAccessService.currentUserCanViewReport()) {
      throw ForbiddenProblem()
    }
    validateParameters(probationRegionId, month)

    val startDate = LocalDate.of(year, month, 1)
    val endDate = LocalDate.of(year, month, startDate.month.length(startDate.isLeapYear))
    val properties = TransitionalAccommodationReferralReportProperties(probationRegionId, startDate, endDate)
    val outputStream = ByteArrayOutputStream()

    when (xServiceName) {
      ServiceName.temporaryAccommodation -> {
        cas3ReportService.createCas3ApplicationReferralsReport(properties, outputStream)
      }
      else -> throw UnsupportedOperationException("Only supported for CAS3")
    }

    return ResponseEntity.ok(InputStreamResource(outputStream.toByteArray().inputStream()))
  }

  override fun reportsReportNameGet(
    reportName: Cas3ReportType,
    startDate: LocalDate,
    endDate: LocalDate,
    probationRegionId: UUID?,
  ): ResponseEntity<Resource> {
    if (!userAccessService.currentUserCanViewReport()) {
      throw ForbiddenProblem()
    }
    validateRequestParameters(probationRegionId, startDate, endDate)
    val outputStream = ByteArrayOutputStream()

    when (reportName) {
      referral -> cas3ReportService.createCas3ApplicationReferralsReport(
        TransitionalAccommodationReferralReportProperties(
          startDate = startDate,
          endDate = endDate,
          probationRegionId = probationRegionId,
        ),
        outputStream,
      )

      booking -> cas3ReportService.createBookingsReport(
        BookingsReportProperties(
          ServiceName.temporaryAccommodation,
          startDate = startDate,
          endDate = endDate,
          probationRegionId = probationRegionId,
        ),
        outputStream,
      )
      bedUsage -> cas3ReportService.createBedUsageReport(
        BedUsageReportProperties(
          ServiceName.temporaryAccommodation,
          startDate = startDate,
          endDate = endDate,
          probationRegionId = probationRegionId,
        ),
        outputStream,
      )

      bedOccupancy -> cas3ReportService.createBedUtilisationReport(
        BedUtilisationReportProperties(
          ServiceName.temporaryAccommodation,
          startDate = startDate,
          endDate = endDate,
          probationRegionId = probationRegionId,
        ),
        outputStream,
      )
    }
    return ResponseEntity.ok(InputStreamResource(outputStream.toByteArray().inputStream()))
  }

  private fun validateParameters(probationRegionId: UUID?, month: Int) {
    validateUserAccessibility(probationRegionId)

    if (month < 1 || month > 12) {
      throw BadRequestProblem(errorDetail = "month must be between 1 and 12")
    }
  }

  private fun validateRequestParameters(probationRegionId: UUID?, startDate: LocalDate, endDate: LocalDate) {
    validateUserAccessibility(probationRegionId)
    validateRequestedDates(startDate, endDate)
  }

  private fun validateRequestedDates(startDate: LocalDate, endDate: LocalDate) {
    when {
      startDate.isAfter(endDate) -> throw BadRequestProblem(errorDetail = "Start Date $startDate cannot be after End Date $endDate")
      ChronoUnit.MONTHS.between(startDate, endDate)
        .toInt() > MAXIMUM_REPORT_DURATION_IN_MONTHS -> throw BadRequestProblem(errorDetail = "End Date $endDate cannot be more than 3 months after Start Date $startDate")
    }
  }

  private fun validateUserAccessibility(probationRegionId: UUID?) {
    when {
      probationRegionId == null && !userAccessService.currentUserHasAllRegionsAccess() -> throw ForbiddenProblem()
      probationRegionId != null && !userAccessService.currentUserCanAccessRegion(probationRegionId) -> throw ForbiddenProblem()
    }
  }
}
