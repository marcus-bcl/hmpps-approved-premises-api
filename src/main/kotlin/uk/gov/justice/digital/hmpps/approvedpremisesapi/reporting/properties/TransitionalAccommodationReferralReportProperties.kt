package uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties

import java.time.LocalDate
import java.util.UUID

data class TransitionalAccommodationReferralReportProperties(
  val probationRegionId: UUID?,
  val startDate: LocalDate,
  val endDate: LocalDate,
)
