package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller.cas2v2

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.cas2v2.ReferenceDataCas2v2Delegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas2v2ApplicationStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.reference.Cas2PersistedApplicationStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.reference.Cas2v2PersistedApplicationStatusFinder
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.cas2.ApplicationStatusTransformer

@Service
class Cas2v2ReferenceDataController(
  private val statusTransformer: ApplicationStatusTransformer,
  private val statusFinder: Cas2v2PersistedApplicationStatusFinder,
) : ReferenceDataCas2v2Delegate {
  override fun referenceDataApplicationStatusGet(): ResponseEntity<List<Cas2v2ApplicationStatus>> = ResponseEntity.ok(transformToApi(statusFinder.active()))

  private fun transformToApi(statusList: List<Cas2PersistedApplicationStatus>): List<Cas2v2ApplicationStatus> = statusList.map { status -> statusTransformer.transformV2ModelToApi(status) }
}
