package uk.gov.justice.digital.hmpps.approvedpremisesapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedReasonRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedsEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedsRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PremisesEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PremisesRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.Availability
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.ValidatableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.ValidationErrors
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.getDaysUntilExclusiveEnd
import java.time.LocalDate
import java.util.UUID

@Service
class PremisesService(
  private val premisesRepository: PremisesRepository,
  private val lostBedsRepository: LostBedsRepository,
  private val bookingRepository: BookingRepository,
  private val lostBedReasonRepository: LostBedReasonRepository
) {
  fun getAllPremises(): List<PremisesEntity> = premisesRepository.findAll()
  fun getPremises(premisesId: UUID): PremisesEntity? = premisesRepository.findByIdOrNull(premisesId)

  fun getLastBookingDate(premises: PremisesEntity) = bookingRepository.getHighestBookingDate(premises.id)
  fun getLastLostBedsDate(premises: PremisesEntity) = lostBedsRepository.getHighestBookingDate(premises.id)

  fun getAvailabilityForRange(premises: PremisesEntity, startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Availability> {
    if (endDate.isBefore(startDate)) throw RuntimeException("startDate must be before endDate when calculating availability for range")

    val bookings = bookingRepository.findAllByPremisesIdAndOverlappingDate(premises.id, startDate, endDate)
    val lostBeds = lostBedsRepository.findAllByPremisesIdAndOverlappingDate(premises.id, startDate, endDate)

    return startDate.getDaysUntilExclusiveEnd(endDate).map { date ->
      val bookingsOnDay = bookings.filter { booking -> booking.arrivalDate <= date && booking.departureDate > date }
      val lostBedsOnDay = lostBeds.filter { lostBed -> lostBed.startDate <= date && lostBed.endDate > date }

      Availability(
        date = date,
        pendingBookings = bookingsOnDay.count { it.arrival == null && it.nonArrival == null && it.cancellation == null },
        arrivedBookings = bookingsOnDay.count { it.arrival != null },
        nonArrivedBookings = bookingsOnDay.count { it.nonArrival != null },
        cancelledBookings = bookingsOnDay.count { it.cancellation != null },
        lostBeds = lostBedsOnDay.sumOf { it.numberOfBeds }
      )
    }.associateBy { it.date }
  }

  fun createLostBeds(
    premises: PremisesEntity,
    startDate: LocalDate,
    endDate: LocalDate,
    numberOfBeds: Int,
    reasonId: UUID,
    referenceNumber: String?,
    notes: String?
  ): ValidatableActionResult<LostBedsEntity> {
    val validationIssues = ValidationErrors()
    if (endDate.isBefore(startDate)) {
      validationIssues["$.endDate"] = "Cannot be before startDate"
    }

    if (numberOfBeds <= 0) {
      validationIssues["$.numberOfBeds"] = "Must be greater than 0"
    }

    val reason = lostBedReasonRepository.findByIdOrNull(reasonId)
    if (reason == null) {
      validationIssues["$.reason"] = "This reason does not exist"
    }

    if (validationIssues.any()) {
      return ValidatableActionResult.FieldValidationError(validationIssues)
    }

    val lostBedsEntity = lostBedsRepository.save(
      LostBedsEntity(
        id = UUID.randomUUID(),
        premises = premises,
        startDate = startDate,
        endDate = endDate,
        numberOfBeds = numberOfBeds,
        reason = reason!!,
        referenceNumber = referenceNumber,
        notes = notes
      )
    )

    return ValidatableActionResult.Success(lostBedsEntity)
  }
}
