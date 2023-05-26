package uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.generator

import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BedEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedsRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationPremisesEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.model.BedUtilisationReportRow
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BedUtilisationReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.WorkingDayCountService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.earliestDateOf
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.getDaysUntilInclusive
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.latestDateOf
import java.time.LocalDate
import java.time.YearMonth

class BedUtilisationReportGenerator(
  private val bookingRepository: BookingRepository,
  private val lostBedsRepository: LostBedsRepository,
  private val workingDayCountService: WorkingDayCountService,
) : ReportGenerator<BedEntity, BedUtilisationReportRow, BedUtilisationReportProperties>(BedUtilisationReportRow::class) {
  override fun filter(properties: BedUtilisationReportProperties): (BedEntity) -> Boolean = {
    checkServiceType(properties.serviceName, it.room.premises) &&
      (properties.probationRegionId == null || it.room.premises.probationRegion.id == properties.probationRegionId)
  }

  override val convert: BedEntity.(properties: BedUtilisationReportProperties) -> List<BedUtilisationReportRow> = { properties ->
    val startOfMonth = LocalDate.of(properties.year, properties.month, 1)
    val endOfMonth = LocalDate.of(properties.year, properties.month, startOfMonth.month.length(startOfMonth.isLeapYear))

    var bookedDaysActiveAndClosed = 0
    var confirmedDays = 0
    var provisionalDays = 0
    var scheduledTurnaroundDays = 0
    var effectiveTurnaroundDays = 0
    var voidDays = 0

    val nonCancelledBookings = bookingRepository.findAllByOverlappingDateForBed(startOfMonth, endOfMonth, this)
      .filter { it.cancellation == null }

    val nonCancelledVoids = lostBedsRepository.findAllByOverlappingDateForBed(startOfMonth, endOfMonth, this)
      .filter { it.cancellation == null }

    val premises = this.room.premises

    nonCancelledBookings
      .forEach { booking ->
        val daysOfBookingInMonth = latestDateOf(booking.arrivalDate, startOfMonth)
          .getDaysUntilInclusive(earliestDateOf(booking.departureDate, endOfMonth))
          .count()

        when {
          booking.arrival != null -> bookedDaysActiveAndClosed += daysOfBookingInMonth
          booking.confirmation != null && booking.arrival == null -> confirmedDays += daysOfBookingInMonth
          booking.confirmation == null -> provisionalDays += daysOfBookingInMonth
        }

        if (booking.turnaround != null) {
          val turnaroundStartDate = booking.departureDate.plusDays(1)
          val turnaroundEndDate = workingDayCountService.addWorkingDays(booking.departureDate, booking.turnaround!!.workingDayCount)
          val firstDayOfTurnaroundInMonth = latestDateOf(turnaroundStartDate, startOfMonth)
          val lastDayOfTurnaroundInMonth = earliestDateOf(turnaroundEndDate, endOfMonth)

          scheduledTurnaroundDays += workingDayCountService.getWorkingDaysCount(firstDayOfTurnaroundInMonth, lastDayOfTurnaroundInMonth)
          effectiveTurnaroundDays += firstDayOfTurnaroundInMonth
            .getDaysUntilInclusive(lastDayOfTurnaroundInMonth)
            .count()
        }
      }

    nonCancelledVoids.forEach { void ->
      val daysOfVoidInMonth = latestDateOf(void.startDate, startOfMonth)
        .getDaysUntilInclusive(earliestDateOf(void.endDate, endOfMonth))
        .count()

      voidDays += daysOfVoidInMonth
    }

    val totalBookedDays = bookedDaysActiveAndClosed + confirmedDays + provisionalDays + effectiveTurnaroundDays + voidDays
    val daysInMonth = YearMonth.of(properties.year, properties.month).lengthOfMonth()

    listOf(
      BedUtilisationReportRow(
        pdu = (premises as? TemporaryAccommodationPremisesEntity)?.probationDeliveryUnit?.name,
        propertyRef = premises.name,
        addressLine1 = premises.addressLine1,
        bedspaceRef = this.room.name,
        bookedDaysActiveAndClosed = bookedDaysActiveAndClosed,
        confirmedDays = confirmedDays,
        provisionalDays = provisionalDays,
        scheduledTurnaroundDays = scheduledTurnaroundDays,
        effectiveTurnaroundDays = effectiveTurnaroundDays,
        voidDays = voidDays,
        totalBookedDays = totalBookedDays,
        totalDaysInTheMonth = daysInMonth,
        occupancyRate = totalBookedDays.toDouble() / daysInMonth,
      ),
    )
  }
}
