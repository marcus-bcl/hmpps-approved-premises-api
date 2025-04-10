package uk.gov.justice.digital.hmpps.approvedpremisesapi.controller.cas1

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.cas1.AssessmentsCas1Delegate
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1Assessment
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1AssessmentSortField
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1AssessmentStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1AssessmentSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1ClarificationNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1NewClarificationNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1UpdateAssessment
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas1UpdatedClarificationNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.SortDirection
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesAssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainAssessmentSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.PersonInfoResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.AssessmentService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.LaoStrategy
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.OffenderService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas1LaoStrategy
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentClarificationNoteTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.PageCriteria
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.extractEntityFromCasResult
import java.util.UUID

@Service
class Cas1AssessmentsController(
  private val assessmentService: AssessmentService,
  private val userService: UserService,
  private val offenderService: OffenderService,
  private val assessmentTransformer: AssessmentTransformer,
  private val objectMapper: ObjectMapper,
  private val assessmentClarificationNoteTransformer: AssessmentClarificationNoteTransformer,
) : AssessmentsCas1Delegate {

  override fun getAssessmentsForUser(
    sortDirection: SortDirection?,
    sortBy: Cas1AssessmentSortField?,
    statuses: List<Cas1AssessmentStatus>?,
    crnOrName: String?,
    page: Int?,
    perPage: Int?,
  ): ResponseEntity<List<Cas1AssessmentSummary>> {
    val user = userService.getUserForRequest()
    val resolvedSortDirection = sortDirection ?: SortDirection.asc
    val resolvedSortBy = sortBy ?: Cas1AssessmentSortField.assessmentArrivalDate

    val domainSummaryStatuses =
      statuses?.map { assessmentTransformer.transformCas1AssessmentStatusToDomainSummaryState(it) } ?: emptyList()
    val (summaries, metadata) = assessmentService.findApprovedPremisesAssessmentSummariesNotReallocatedForUser(
      user,
      domainSummaryStatuses,
      PageCriteria(resolvedSortBy, resolvedSortDirection, page, perPage),
    )

    val transformedSummaries = transformDomainToApi(summaries, user.cas1LaoStrategy())

    return ResponseEntity.ok()
      .headers(metadata?.toHeaders())
      .body(transformedSummaries)
  }

  override fun getAssessment(assessmentId: UUID): ResponseEntity<Cas1Assessment> {
    val user = userService.getUserForRequest()

    val assessment = extractEntityFromCasResult(
      assessmentService.getAssessmentAndValidate(
        user,
        assessmentId,
      ),
    ) as ApprovedPremisesAssessmentEntity

    val personInfo = offenderService.getPersonInfoResult(assessment.application.crn, user.cas1LaoStrategy())

    val transformedResponse = assessmentTransformer.transformJpaToCas1Assessment(assessment, personInfo)

    return ResponseEntity.ok(transformedResponse)
  }

  override fun updateAssessment(
    assessmentId: UUID,
    cas1UpdateAssessment: Cas1UpdateAssessment,
  ): ResponseEntity<Cas1Assessment> {
    val user = userService.getUserForRequest()

    val assessment = extractEntityFromCasResult(
      assessmentService
        .updateAssessment(
          user,
          assessmentId,
          objectMapper.writeValueAsString(cas1UpdateAssessment.data),
        ),
    ) as ApprovedPremisesAssessmentEntity

    val personInfo = offenderService.getPersonInfoResult(assessment.application.crn, user.cas1LaoStrategy())

    return ResponseEntity.ok(
      assessmentTransformer.transformJpaToCas1Assessment(assessment, personInfo),
    )
  }

  override fun addClarificationNoteToAssessment(
    assessmentId: UUID,
    cas1NewClarificationNote: Cas1NewClarificationNote,
  ): ResponseEntity<Cas1ClarificationNote> {
    val user = userService.getUserForRequest()

    val clarificationNoteResult =
      assessmentService.addAssessmentClarificationNote(user, assessmentId, cas1NewClarificationNote.query)

    return ResponseEntity.ok(
      assessmentClarificationNoteTransformer.transformJpaToCas1ClarificationNote(
        extractEntityFromCasResult(
          clarificationNoteResult,
        ),
      ),
    )
  }

  override fun updateAssessmentClarificationNote(
    assessmentId: UUID,
    noteId: UUID,
    cas1UpdatedClarificationNote: Cas1UpdatedClarificationNote,
  ): ResponseEntity<Cas1ClarificationNote> {
    val user = userService.getUserForRequest()
    val clarificationNoteResult = assessmentService.updateAssessmentClarificationNote(
      user,
      assessmentId,
      noteId,
      cas1UpdatedClarificationNote.response,
      cas1UpdatedClarificationNote.responseReceivedOn,
    )

    return ResponseEntity.ok(
      assessmentClarificationNoteTransformer.transformJpaToCas1ClarificationNote(extractEntityFromCasResult(clarificationNoteResult)),
    )
  }

  private fun transformDomainToApi(
    summaries: List<DomainAssessmentSummary>,
    laoStrategy: LaoStrategy,
  ): List<Cas1AssessmentSummary> {
    val crns = summaries.map { it.crn }
    val personInfoResults = offenderService.getPersonInfoResults(crns.toSet(), laoStrategy)

    return summaries.map {
      val crn = it.crn
      assessmentTransformer.transformDomainToCas1AssessmentSummary(
        it,
        personInfoResults.firstOrNull { it.crn == crn } ?: PersonInfoResult.Unknown(crn),
      )
    }
  }
}
