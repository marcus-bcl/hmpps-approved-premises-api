package uk.gov.justice.digital.hmpps.approvedpremisesapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesAssessmentJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentClarificationNoteEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentClarificationNoteRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentDecision
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualification
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRole
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.ValidationErrors
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.AuthorisableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.ValidatableActionResult
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AssessmentService(
  private val userRepository: UserRepository,
  private val assessmentRepository: AssessmentRepository,
  private val assessmentClarificationNoteRepository: AssessmentClarificationNoteRepository,
  private val jsonSchemaService: JsonSchemaService,
  private val applicationRepository: ApplicationRepository,
  private val userService: UserService
) {
  fun getVisibleAssessmentsForUser(user: UserEntity): List<AssessmentEntity> {
    // TODO: Potentially needs LAO enforcing too: https://trello.com/c/alNxpm9e/856-investigate-whether-assessors-will-have-access-to-limited-access-offenders

    val latestSchema = jsonSchemaService.getNewestSchema(ApprovedPremisesAssessmentJsonSchemaEntity::class.java)

    val assessments = if (user.hasRole(UserRole.WORKFLOW_MANAGER)) {
      assessmentRepository.findAll()
    } else {
      assessmentRepository.findAllByAllocatedToUser_Id(user.id)
    }

    assessments.forEach {
      it.schemaUpToDate = it.schemaVersion.id == latestSchema.id
    }

    return assessments
  }

  fun getAssessmentForUser(user: UserEntity, assessmentId: UUID): AuthorisableActionResult<AssessmentEntity> {
    // TODO: Potentially needs LAO enforcing too: https://trello.com/c/alNxpm9e/856-investigate-whether-assessors-will-have-access-to-limited-access-offenders

    val latestSchema = jsonSchemaService.getNewestSchema(ApprovedPremisesAssessmentJsonSchemaEntity::class.java)

    val assessment = assessmentRepository.findByIdOrNull(assessmentId)
      ?: return AuthorisableActionResult.NotFound()

    if (!user.hasRole(UserRole.WORKFLOW_MANAGER) && assessment.allocatedToUser != user) {
      return AuthorisableActionResult.Unauthorised()
    }

    assessment.schemaUpToDate = assessment.schemaVersion.id == latestSchema.id

    return AuthorisableActionResult.Success(assessment)
  }

  fun createAssessment(application: ApplicationEntity): AssessmentEntity {
    if (application !is ApprovedPremisesApplicationEntity) {
      throw RuntimeException("Only CAS1 Applications are currently supported")
    }

    val requiredQualifications = getRequiredQualificationsForApprovedPremisesApplication(application)

    // Might want to handle this more robustly in future if it emerges this is more common than initially thought
    val allocatedUser = getUserForAllocation(requiredQualifications)
      ?: throw RuntimeException("No Users with all of required qualifications (${requiredQualifications.joinToString(", ")}) could be found")

    val dateTimeNow = OffsetDateTime.now()

    return assessmentRepository.save(
      AssessmentEntity(
        id = UUID.randomUUID(), application = application,
        data = null, document = null, schemaVersion = jsonSchemaService.getNewestSchema(ApprovedPremisesAssessmentJsonSchemaEntity::class.java),
        allocatedToUser = allocatedUser,
        allocatedAt = dateTimeNow,
        reallocatedAt = null,
        createdAt = dateTimeNow,
        submittedAt = null,
        decision = null,
        schemaUpToDate = true,
        rejectionRationale = null,
        clarificationNotes = mutableListOf()
      )
    )
  }

  fun updateAssessment(user: UserEntity, assessmentId: UUID, data: String?): AuthorisableActionResult<ValidatableActionResult<AssessmentEntity>> {
    val assessmentResult = getAssessmentForUser(user, assessmentId)
    val assessment = when (assessmentResult) {
      is AuthorisableActionResult.Success -> assessmentResult.entity
      is AuthorisableActionResult.Unauthorised -> return AuthorisableActionResult.Unauthorised()
      is AuthorisableActionResult.NotFound -> return AuthorisableActionResult.NotFound()
    }

    if (!assessment.schemaUpToDate) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The schema version is outdated")
      )
    }

    if (assessment.submittedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("A decision has already been taken on this assessment")
      )
    }

    if (assessment.reallocatedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The application has been reallocated, this assessment is read only")
      )
    }

    assessment.data = data

    val savedAssessment = assessmentRepository.save(assessment)

    return AuthorisableActionResult.Success(
      ValidatableActionResult.Success(savedAssessment)
    )
  }

  fun acceptAssessment(user: UserEntity, assessmentId: UUID, document: String?): AuthorisableActionResult<ValidatableActionResult<AssessmentEntity>> {
    val assessmentResult = getAssessmentForUser(user, assessmentId)
    val assessment = when (assessmentResult) {
      is AuthorisableActionResult.Success -> assessmentResult.entity
      is AuthorisableActionResult.Unauthorised -> return AuthorisableActionResult.Unauthorised()
      is AuthorisableActionResult.NotFound -> return AuthorisableActionResult.NotFound()
    }

    if (!assessment.schemaUpToDate) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The schema version is outdated")
      )
    }

    if (assessment.submittedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("A decision has already been taken on this assessment")
      )
    }

    if (assessment.reallocatedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The application has been reallocated, this assessment is read only")
      )
    }

    val validationErrors = ValidationErrors()
    val assessmentData = assessment.data

    if (assessmentData == null) {
      validationErrors["$.data"] = "empty"
    } else if (!jsonSchemaService.validate(assessment.schemaVersion, assessmentData)) {
      validationErrors["$.data"] = "invalid"
    }

    if (validationErrors.any()) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.FieldValidationError(validationErrors)
      )
    }

    assessment.document = document
    assessment.submittedAt = OffsetDateTime.now()
    assessment.decision = AssessmentDecision.ACCEPTED

    val savedAssessment = assessmentRepository.save(assessment)

    return AuthorisableActionResult.Success(
      ValidatableActionResult.Success(savedAssessment)
    )
  }

  fun rejectAssessment(user: UserEntity, assessmentId: UUID, document: String?, rejectionRationale: String): AuthorisableActionResult<ValidatableActionResult<AssessmentEntity>> {
    val assessmentResult = getAssessmentForUser(user, assessmentId)
    val assessment = when (assessmentResult) {
      is AuthorisableActionResult.Success -> assessmentResult.entity
      is AuthorisableActionResult.Unauthorised -> return AuthorisableActionResult.Unauthorised()
      is AuthorisableActionResult.NotFound -> return AuthorisableActionResult.NotFound()
    }

    if (!assessment.schemaUpToDate) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The schema version is outdated")
      )
    }

    if (assessment.submittedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("A decision has already been taken on this assessment")
      )
    }

    if (assessment.reallocatedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("The application has been reallocated, this assessment is read only")
      )
    }

    val validationErrors = ValidationErrors()
    val assessmentData = assessment.data

    if (assessmentData == null) {
      validationErrors["$.data"] = "empty"
    } else if (!jsonSchemaService.validate(assessment.schemaVersion, assessmentData)) {
      validationErrors["$.data"] = "invalid"
    }

    if (validationErrors.any()) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.FieldValidationError(validationErrors)
      )
    }

    assessment.document = document
    assessment.submittedAt = OffsetDateTime.now()
    assessment.decision = AssessmentDecision.REJECTED
    assessment.rejectionRationale = rejectionRationale

    val savedAssessment = assessmentRepository.save(assessment)

    return AuthorisableActionResult.Success(
      ValidatableActionResult.Success(savedAssessment)
    )
  }

  fun reallocateAssessment(requestUser: UserEntity, userToAllocateToId: UUID, applicationId: UUID): AuthorisableActionResult<ValidatableActionResult<AssessmentEntity>> {
    if (! requestUser.hasRole(UserRole.WORKFLOW_MANAGER)) {
      return AuthorisableActionResult.Unauthorised()
    }

    val assigneeUserResult = userService.getUserForId(userToAllocateToId)

    val assigneeUser = when (assigneeUserResult) {
      is AuthorisableActionResult.Success -> assigneeUserResult.entity
      else -> return AuthorisableActionResult.NotFound()
    }

    val application = applicationRepository.findByIdOrNull(applicationId)
      ?: return AuthorisableActionResult.NotFound()

    if (application !is ApprovedPremisesApplicationEntity) {
      throw RuntimeException("Only CAS1 Applications are currently supported")
    }

    val currentAssessment = assessmentRepository.findByApplication_IdAndReallocatedAtNull(applicationId)

    if (currentAssessment.submittedAt != null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("A decision has already been taken on this assessment")
      )
    }

    val requiredQualifications = getRequiredQualificationsForApprovedPremisesApplication(application)

    if (! assigneeUser.hasRole(UserRole.ASSESSOR)) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.FieldValidationError(ValidationErrors().apply { this["$.userId"] = "lackingAssessorRole" })
      )
    }

    if (! assigneeUser.hasAllQualifications(requiredQualifications)) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.FieldValidationError(ValidationErrors().apply { this["$.userId"] = "lackingQualifications" })
      )
    }

    currentAssessment.reallocatedAt = OffsetDateTime.now()
    assessmentRepository.save(currentAssessment)

    // Make the timestamp precision less precise, so we don't have any issues with microsecond resolution in tests
    val dateTimeNow = OffsetDateTime.now().withNano(0)

    val newAssessment = assessmentRepository.save(
      AssessmentEntity(
        id = UUID.randomUUID(),
        application = application,
        data = null,
        document = null,
        schemaVersion = jsonSchemaService.getNewestSchema(ApprovedPremisesAssessmentJsonSchemaEntity::class.java),
        allocatedToUser = assigneeUser,
        allocatedAt = dateTimeNow,
        reallocatedAt = null,
        createdAt = dateTimeNow,
        submittedAt = null,
        decision = null,
        schemaUpToDate = true,
        rejectionRationale = null,
        clarificationNotes = mutableListOf()
      )
    )

    return AuthorisableActionResult.Success(
      ValidatableActionResult.Success(
        newAssessment
      )
    )
  }

  fun addAssessmentClarificationNote(user: UserEntity, assessmentId: UUID, text: String): AuthorisableActionResult<AssessmentClarificationNoteEntity> {
    val assessmentResult = getAssessmentForUser(user, assessmentId)
    val assessment = when (assessmentResult) {
      is AuthorisableActionResult.Success -> assessmentResult.entity
      is AuthorisableActionResult.Unauthorised -> return AuthorisableActionResult.Unauthorised()
      is AuthorisableActionResult.NotFound -> return AuthorisableActionResult.NotFound()
    }

    val clarificationNoteEntity = assessmentClarificationNoteRepository.save(
      AssessmentClarificationNoteEntity(
        id = UUID.randomUUID(),
        assessment = assessment,
        createdByUser = user,
        createdAt = OffsetDateTime.now(),
        query = text,
        response = null,
        responseReceivedOn = null,
      )
    )

    return AuthorisableActionResult.Success(clarificationNoteEntity)
  }

  fun updateAssessmentClarificationNote(user: UserEntity, assessmentId: UUID, id: UUID, response: String, responseReceivedOn: LocalDate): AuthorisableActionResult<ValidatableActionResult<AssessmentClarificationNoteEntity>> {
    val assessment = when (val assessmentResult = getAssessmentForUser(user, assessmentId)) {
      is AuthorisableActionResult.Success -> assessmentResult.entity
      is AuthorisableActionResult.Unauthorised -> return AuthorisableActionResult.Unauthorised()
      is AuthorisableActionResult.NotFound -> return AuthorisableActionResult.NotFound()
    }

    var clarificationNoteEntity = assessmentClarificationNoteRepository.findByAssessmentIdAndId(assessment.id, id)

    if (clarificationNoteEntity === null) {
      return AuthorisableActionResult.NotFound()
    }

    if (clarificationNoteEntity.createdByUser.id !== user.id) {
      return AuthorisableActionResult.Unauthorised()
    }

    if (clarificationNoteEntity.response !== null) {
      return AuthorisableActionResult.Success(
        ValidatableActionResult.GeneralValidationError("A response has already been added to this note")
      )
    }

    clarificationNoteEntity.response = response
    clarificationNoteEntity.responseReceivedOn = responseReceivedOn

    val savedNote = assessmentClarificationNoteRepository.save(clarificationNoteEntity)

    return AuthorisableActionResult.Success(
      ValidatableActionResult.Success(savedNote)
    )
  }

  private fun getUserForAllocation(qualifications: List<UserQualification>): UserEntity? = userRepository.findQualifiedAssessorWithLeastPendingAllocations(qualifications.map(UserQualification::toString), qualifications.size.toLong())
  private fun getRequiredQualificationsForApprovedPremisesApplication(application: ApprovedPremisesApplicationEntity): List<UserQualification> {
    val requiredQualifications = mutableListOf<UserQualification>()

    if (application.isPipeApplication == true) {
      requiredQualifications += UserQualification.PIPE
    }

    if (application.isWomensApplication == true) {
      requiredQualifications += UserQualification.WOMENS
    }

    return requiredQualifications
  }
}
