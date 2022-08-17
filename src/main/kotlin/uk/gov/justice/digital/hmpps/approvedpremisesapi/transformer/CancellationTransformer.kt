package uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.approvedpremisesapi.health.api.model.Cancellation
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CancellationEntity

@Component
class CancellationTransformer(private val cancellationReasonTransformer: CancellationReasonTransformer) {
  fun transformJpaToApi(jpa: CancellationEntity?) = jpa?.let {
    Cancellation(
      bookingId = jpa.booking.id,
      date = jpa.date,
      reason = cancellationReasonTransformer.transformJpaToApi(jpa.reason),
      notes = jpa.notes
    )
  }
}
