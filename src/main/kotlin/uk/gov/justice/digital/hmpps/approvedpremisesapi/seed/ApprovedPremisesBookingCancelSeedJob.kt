package uk.gov.justice.digital.hmpps.approvedpremisesapi.seed

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.ValidatableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.BookingService
import java.time.LocalDate
import java.util.UUID

class ApprovedPremisesBookingCancelSeedJob(
  fileName: String,
  private val bookingService: BookingService,
) : SeedJob<CancelBookingSeedCsvRow>(
  fileName = fileName,
  requiredHeaders = setOf(
    "id",
  ),
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun deserializeRow(columns: Map<String, String>) = CancelBookingSeedCsvRow(
    id = UUID.fromString(columns["id"]!!),
  )

  override fun processRow(row: CancelBookingSeedCsvRow) {
    val errorInBookingDetailsCancellationReason = UUID.fromString("7c310cfd-3952-456d-b0ee-0f7817afe64a")

    val booking = bookingService.getBooking(row.id)
      ?: throw RuntimeException("No Booking with Id of ${row.id} exists")

    if (booking.service != ServiceName.approvedPremises.value) {
      throw RuntimeException("Booking ${row.id} is not an Approved Premises Booking")
    }

    val validationResult = bookingService.createCancellation(
      booking = booking,
      date = LocalDate.now(),
      reasonId = errorInBookingDetailsCancellationReason,
      notes = null,
    )

    when (validationResult) {
      is ValidatableActionResult.ConflictError -> throw RuntimeException("Conflict trying to create Cancellation: ${validationResult.message}")
      is ValidatableActionResult.FieldValidationError -> throw RuntimeException("Field error trying to create Cancellation: ${validationResult.validationMessages}")
      is ValidatableActionResult.GeneralValidationError -> throw RuntimeException("General error trying to create Cancellation: ${validationResult.message}")
      is ValidatableActionResult.Success -> log.info("Cancelled Booking ${row.id}")
    }
  }
}

data class CancelBookingSeedCsvRow(
  val id: UUID,
)