package uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.cas2

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas2StatusUpdate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2StatusUpdateEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.ExternalUserTransformer

@Component("Cas2StatusUpdateTransformer")
class StatusUpdateTransformer(
  private val externalUserTransformer: ExternalUserTransformer,
) {

  fun transformJpaToApi(
    jpa: Cas2StatusUpdateEntity,
  ):
    Cas2StatusUpdate {
    return Cas2StatusUpdate(
      id = jpa.id,
      name = jpa.status().name,
      label = jpa.label,
      description = jpa.description,
      updatedBy = externalUserTransformer.transformJpaToApi(jpa.assessor),
      updatedAt = jpa.createdAt?.toInstant(),
    )
  }
}