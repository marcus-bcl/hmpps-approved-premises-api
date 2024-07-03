package uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApArea
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesUser
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.AssessmentTask
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.FullPersonSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PersonSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PersonSummaryDiscriminator
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementApplicationTask
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementDates
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementRequestTask
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.RestrictedPersonSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TaskStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TaskType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UnknownPersonSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesAssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequestEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.ApprovedPremisesApplicationStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.PersonSummaryInfoResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.getNameFromPersonSummaryInfoResult
import java.time.OffsetDateTime
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementType as ApiPlacementType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementType as JpaPlacementType

@Component
class TaskTransformer(
  private val userTransformer: UserTransformer,
  private val risksTransformer: RisksTransformer,
  private val placementRequestTransformer: PlacementRequestTransformer,
  private val apAreaTransformer: ApAreaTransformer,
  private val assessmentTransformer: AssessmentTransformer,
) {

  fun transformAssessmentToTask(assessment: AssessmentEntity, offenderSummaries: List<PersonSummaryInfoResult>) = AssessmentTask(
    id = assessment.id,
    applicationId = assessment.application.id,
    personName = getPersonNameFromApplication(assessment.application, offenderSummaries),
    personSummary = getPersonSummaryFromApplication(assessment.application, offenderSummaries),
    crn = assessment.application.crn,
    dueDate = transformDueAtToDate(assessment.dueAt),
    dueAt = transformDueAtToInstant(assessment.dueAt),
    allocatedToStaffMember = transformUserOrNull(assessment.allocatedToUser),
    status = getAssessmentStatus(assessment),
    taskType = TaskType.assessment,
    apArea = getApArea(assessment.application),
    createdFromAppeal = when (assessment) {
      is ApprovedPremisesAssessmentEntity -> assessment.createdFromAppeal
      else -> false
    },
    outcomeRecordedAt = assessment.submittedAt?.toInstant(),
    outcome = assessment.decision?.let { assessmentTransformer.transformJpaDecisionToApi(assessment.decision) },
  )

  fun transformPlacementRequestToTask(placementRequest: PlacementRequestEntity, offenderSummaries: List<PersonSummaryInfoResult>): PlacementRequestTask {
    val (outcomeRecordedAt, outcome) = placementRequest.getOutcomeDetails()
    return PlacementRequestTask(
      id = placementRequest.id,
      applicationId = placementRequest.application.id,
      personName = getPersonNameFromApplication(placementRequest.application, offenderSummaries),
      personSummary = getPersonSummaryFromApplication(placementRequest.application, offenderSummaries),
      crn = placementRequest.application.crn,
      dueDate = transformDueAtToDate(placementRequest.dueAt),
      dueAt = transformDueAtToInstant(placementRequest.dueAt),
      allocatedToStaffMember = transformUserOrNull(placementRequest.allocatedToUser),
      status = getPlacementRequestStatus(placementRequest),
      taskType = TaskType.placementRequest,
      tier = risksTransformer.transformTierDomainToApi(placementRequest.application.riskRatings!!.tier),
      expectedArrival = placementRequest.expectedArrival,
      duration = placementRequest.duration,
      placementRequestStatus = placementRequestTransformer.getStatus(placementRequest),
      releaseType = placementRequestTransformer.getReleaseType(placementRequest.application.releaseType),
      apArea = getApArea(placementRequest.application),
      outcomeRecordedAt = outcomeRecordedAt?.toInstant(),
      outcome = outcome,
    )
  }

  fun transformPlacementApplicationToTask(placementApplication: PlacementApplicationEntity, offenderSummaries: List<PersonSummaryInfoResult>) = PlacementApplicationTask(
    id = placementApplication.id,
    applicationId = placementApplication.application.id,
    personName = getPersonNameFromApplication(placementApplication.application, offenderSummaries),
    personSummary = getPersonSummaryFromApplication(placementApplication.application, offenderSummaries),
    crn = placementApplication.application.crn,
    dueDate = transformDueAtToDate(placementApplication.dueAt),
    dueAt = transformDueAtToInstant(placementApplication.dueAt),
    allocatedToStaffMember = transformUserOrNull(placementApplication.allocatedToUser),
    status = getPlacementApplicationStatus(placementApplication),
    taskType = TaskType.placementApplication,
    tier = risksTransformer.transformTierDomainToApi(placementApplication.application.riskRatings!!.tier),
    placementDates = placementApplication.placementDates.map {
      PlacementDates(
        expectedArrival = it.expectedArrival,
        duration = it.duration,
      )
    },
    releaseType = placementRequestTransformer.getReleaseType(placementApplication.application.releaseType),
    placementType = getPlacementType(placementApplication.placementType!!),
    apArea = getApArea(placementApplication.application),
    outcomeRecordedAt = placementApplication.decisionMadeAt?.toInstant(),
    outcome = placementApplication.decision?.apiValue,
  )

  private fun getPersonNameFromApplication(
    application: ApplicationEntity,
    offenderSummaries: List<PersonSummaryInfoResult>,
  ): String {
    val crn = application.crn
    val offenderSummary = offenderSummaries.first { it.crn == crn }
    return getNameFromPersonSummaryInfoResult(offenderSummary)
  }

  private fun getPersonSummaryFromApplication(
    application: ApplicationEntity,
    offenderSummaries: List<PersonSummaryInfoResult>,
  ): PersonSummary {
    val offenderSummary = offenderSummaries.first { it.crn == application.crn }
    when (offenderSummary) {
      is PersonSummaryInfoResult.Success.Full -> {
        return FullPersonSummary(
          crn = application.crn,
          personType = PersonSummaryDiscriminator.fullPersonSummary,
          name = getNameFromPersonSummaryInfoResult(offenderSummary),
        )
      }
      is PersonSummaryInfoResult.Success.Restricted -> {
        return RestrictedPersonSummary(
          crn = application.crn,
          personType = PersonSummaryDiscriminator.restrictedPersonSummary,
        )
      }
      is PersonSummaryInfoResult.NotFound, is PersonSummaryInfoResult.Unknown -> {
        return UnknownPersonSummary(
          crn = application.crn,
          personType = PersonSummaryDiscriminator.unknownPersonSummary,
        )
      }
    }
  }

  private fun getApArea(application: ApplicationEntity): ApArea? {
    return (application as ApprovedPremisesApplicationEntity).apArea?.let { apAreaTransformer.transformJpaToApi(it) }
  }

  private fun getPlacementType(placementType: JpaPlacementType): ApiPlacementType = when (placementType) {
    JpaPlacementType.ROTL -> ApiPlacementType.rotl
    JpaPlacementType.ADDITIONAL_PLACEMENT -> ApiPlacementType.additionalPlacement
    JpaPlacementType.RELEASE_FOLLOWING_DECISION -> ApiPlacementType.releaseFollowingDecision
  }

  private fun getPlacementApplicationStatus(entity: PlacementApplicationEntity): TaskStatus = when {
    entity.data.isNullOrEmpty() -> TaskStatus.notStarted
    entity.decision !== null -> TaskStatus.complete
    else -> TaskStatus.inProgress
  }

  private fun getAssessmentStatus(entity: AssessmentEntity): TaskStatus = when {
    entity.data.isNullOrEmpty() -> TaskStatus.notStarted
    entity.decision !== null -> TaskStatus.complete
    (entity.application as ApprovedPremisesApplicationEntity).status == ApprovedPremisesApplicationStatus.REQUESTED_FURTHER_INFORMATION -> TaskStatus.infoRequested
    else -> TaskStatus.inProgress
  }

  private fun getPlacementRequestStatus(entity: PlacementRequestEntity): TaskStatus = when {
    entity.booking !== null -> TaskStatus.complete
    else -> TaskStatus.notStarted
  }

  private fun transformUserOrNull(userEntity: UserEntity?): ApprovedPremisesUser? {
    return if (userEntity == null) {
      null
    } else {
      userTransformer.transformJpaToApi(userEntity, ServiceName.approvedPremises) as ApprovedPremisesUser
    }
  }

  // Use the sure operator here as entities will definitely have a `dueAt` value by the time they're surfaced as tasks
  private fun transformDueAtToDate(dueAt: OffsetDateTime?) = dueAt!!.toLocalDate()

  private fun transformDueAtToInstant(dueAt: OffsetDateTime?) = dueAt!!.toInstant()
}
