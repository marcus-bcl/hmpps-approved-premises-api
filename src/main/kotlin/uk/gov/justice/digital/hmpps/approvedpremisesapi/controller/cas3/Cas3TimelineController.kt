package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller.cas3

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.cas3.TimelineCas3Delegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ReferralHistoryNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationAssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRole
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.AssessmentService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas3.Cas3DomainEventService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.extractEntityFromCasResult

@Service
class Cas3TimelineController(
  private val userService: UserService,
  private val assessmentService: AssessmentService,
  private val cas3DomainEventService: Cas3DomainEventService,
  private val assessmentTransformer: AssessmentTransformer,
) : TimelineCas3Delegate {
  override fun getTimelineEntries(assessmentId: java.util.UUID): ResponseEntity<List<ReferralHistoryNote>> {
    val user = userService.getUserForRequest()
    val assessmentResult = assessmentService.getAssessmentAndValidate(user, assessmentId, forTimeline = true)
    val assessment = extractEntityFromCasResult(assessmentResult) as TemporaryAccommodationAssessmentEntity
    val domainEventNotes = cas3DomainEventService.getAssessmentUpdatedEvents(assessmentId = assessment.id)
    val timelineEntries = assessmentTransformer.getSortedReferralHistoryNotes(
      assessment,
      domainEventNotes,
      includeUserNotes = user.hasAnyRole(UserRole.CAS3_ASSESSOR),
    )

    return ResponseEntity.ok(timelineEntries)
  }
}
