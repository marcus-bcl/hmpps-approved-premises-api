package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.PlacementApplicationsApiDelegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.NewPlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementApplicationDecisionEnvelope
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.SubmitPlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UpdatePlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.ApplicationService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.PlacementApplicationService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.PlacementApplicationTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.extractEntityFromAuthorisableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.extractEntityFromValidatableActionResult
import java.util.UUID

@Service
class PlacementApplicationsController(
  private val userService: UserService,
  private val applicationService: ApplicationService,
  private val placementApplicationService: PlacementApplicationService,
  private val placementApplicationTransformer: PlacementApplicationTransformer,
  private val objectMapper: ObjectMapper,
) : PlacementApplicationsApiDelegate {
  override fun placementApplicationsPost(newPlacementApplication: NewPlacementApplication): ResponseEntity<PlacementApplication> {
    val user = userService.getUserForRequest()

    val application = extractEntityFromAuthorisableActionResult(
      applicationService.getApplicationForUsername(newPlacementApplication.applicationId, user.deliusUsername),
    )

    if (application !is ApprovedPremisesApplicationEntity) {
      throw RuntimeException("Only CAS1 Applications are currently supported")
    }

    val placementApplication = extractEntityFromValidatableActionResult(
      placementApplicationService.createApplication(application, user),
    )

    return ResponseEntity.ok(placementApplicationTransformer.transformJpaToApi(placementApplication))
  }

  override fun placementApplicationsIdGet(id: UUID): ResponseEntity<PlacementApplication> {
    val result = placementApplicationService.getApplication(id)
    val placementApplication = extractEntityFromAuthorisableActionResult(result)

    return ResponseEntity.ok(placementApplicationTransformer.transformJpaToApi(placementApplication))
  }

  override fun placementApplicationsIdPut(
    id: UUID,
    updatePlacementApplication: UpdatePlacementApplication,
  ): ResponseEntity<PlacementApplication> {
    val serializedData = objectMapper.writeValueAsString(updatePlacementApplication.data)

    val result = placementApplicationService.updateApplication(id, serializedData)

    val validationResult = extractEntityFromAuthorisableActionResult(result)
    val placementApplication = extractEntityFromValidatableActionResult(validationResult)

    return ResponseEntity.ok(placementApplicationTransformer.transformJpaToApi(placementApplication))
  }

  override fun placementApplicationsIdSubmissionPost(
    id: UUID,
    submitPlacementApplication: SubmitPlacementApplication,
  ): ResponseEntity<PlacementApplication> {
    val serializedData = objectMapper.writeValueAsString(submitPlacementApplication.translatedDocument)

    val result = placementApplicationService.submitApplication(id, serializedData, submitPlacementApplication.placementType, submitPlacementApplication.placementDates)

    val validationResult = extractEntityFromAuthorisableActionResult(result)
    val placementApplication = extractEntityFromValidatableActionResult(validationResult)

    return ResponseEntity.ok(placementApplicationTransformer.transformJpaToApi(placementApplication))
  }

  override fun placementApplicationsIdDecisionPost(
    id: UUID,
    placementApplicationDecisionEnvelope: PlacementApplicationDecisionEnvelope,
  ): ResponseEntity<PlacementApplication> {
    val result = placementApplicationService.recordDecision(id, placementApplicationDecisionEnvelope)

    val validationResult = extractEntityFromAuthorisableActionResult(result)
    val placementApplication = extractEntityFromValidatableActionResult(validationResult)

    return ResponseEntity.ok(placementApplicationTransformer.transformJpaToApi(placementApplication))
  }
}