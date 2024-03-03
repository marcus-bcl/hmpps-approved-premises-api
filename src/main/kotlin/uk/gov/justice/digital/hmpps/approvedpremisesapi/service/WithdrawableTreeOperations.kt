package uk.gov.justice.digital.hmpps.approvedpremisesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.AuthorisableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.CasResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.ValidatableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.extractMessage
import java.time.LocalDate

@Service
class WithdrawableTreeOperations(
  // Added Lazy annotations here to prevent circular dependency issues
  @Lazy private val placementRequestService: PlacementRequestService,
  @Lazy private val bookingService: BookingService,
  private val bookingRepository: BookingRepository,
  @Lazy private val placementApplicationService: PlacementApplicationService,
) {
  var log: Logger = LoggerFactory.getLogger(this::class.java)

  fun withdrawDescendantsOfRootNode(
    rootNode: WithdrawableTreeNode,
    withdrawalContext: WithdrawalContext,
  ) {
    if (log.isDebugEnabled) {
      log.debug("Tree for withdrawing descendants of ${withdrawalContext.triggeringEntityType} is $rootNode")
    }

    rootNode.collectDescendants().forEach {
      if (it.status.withdrawable) {
        withdraw(it, withdrawalContext)
      }
    }
  }

  private fun withdraw(
    node: WithdrawableTreeNode,
    context: WithdrawalContext,
  ) {
    when (node.entityType) {
      WithdrawableEntityType.Application -> Unit
      WithdrawableEntityType.PlacementRequest -> {
        val result = placementRequestService.withdrawPlacementRequest(
          placementRequestId = node.entityId,
          userProvidedReason = null,
          context,
        )

        when (result) {
          is AuthorisableActionResult.Success -> Unit
          else -> log.error(
            "Failed to automatically withdraw PlacementRequest ${node.entityId} " +
              "when withdrawing ${context.triggeringEntityType} ${context.triggeringEntityId} " +
              "with error type ${result::class}",
          )
        }
      }
      WithdrawableEntityType.PlacementApplication -> {
        val result = placementApplicationService.withdrawPlacementApplication(
          id = node.entityId,
          userProvidedReason = null,
          context,
        )

        when (result) {
          is CasResult.Success -> Unit
          else -> log.error(
            "Failed to automatically withdraw PlacementApplication ${node.entityId} " +
              "when withdrawing ${context.triggeringEntityType} ${context.triggeringEntityId} " +
              "with error type ${result::class}",
          )
        }
      }
      WithdrawableEntityType.Booking -> {
        val booking = bookingRepository.findByIdOrNull(node.entityId)!!

        val bookingCancellationResult = bookingService.createCas1Cancellation(
          booking = booking,
          cancelledAt = LocalDate.now(),
          userProvidedReason = null,
          notes = "Automatically withdrawn as ${context.triggeringEntityType.label} was withdrawn",
          withdrawalContext = context,
        )

        when (bookingCancellationResult) {
          is ValidatableActionResult.Success -> Unit
          else -> log.error(
            "Failed to automatically withdraw Booking ${booking.id} " +
              "when withdrawing ${context.triggeringEntityType} ${context.triggeringEntityId} " +
              "with message ${extractMessage(bookingCancellationResult)}",
          )
        }
      }
    }
  }
}
