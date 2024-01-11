package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesAssessment
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesAssessmentStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesAssessmentSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesUser
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Person
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TemporaryAccommodationApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TemporaryAccommodationAssessment
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TemporaryAccommodationAssessmentStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TemporaryAccommodationAssessmentSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.TemporaryAccommodationUser
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApAreaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesAssessmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.AssessmentClarificationNoteEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PersonRisksFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ProbationRegionEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TemporaryAccommodationAssessmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesAssessmentJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainAssessmentSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainAssessmentSummaryStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationAssessmentJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.ApplicationsTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentClarificationNoteTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentReferralHistoryNoteTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.PersonTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.RisksTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.UserTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomDateTimeBefore
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringMultiCaseWithNumbers
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.stream.Stream
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.AssessmentDecision as ApiAssessmentDecision
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentDecision as JpaAssessmentDecision

class AssessmentTransformerTest {
  private val mockApplicationsTransformer = mockk<ApplicationsTransformer>()
  private val mockAssessmentClarificationNoteTransformer = mockk<AssessmentClarificationNoteTransformer>()
  private val mockAssessmentReferralHistoryNoteTransformer = mockk<AssessmentReferralHistoryNoteTransformer>()
  private val mockUserTransformer = mockk<UserTransformer>()
  private val mockPersonTransformer = mockk<PersonTransformer>()
  private val risksTransformer = RisksTransformer()
  private val objectMapper = ObjectMapper().apply {
    registerModule(Jdk8Module())
    registerModule(JavaTimeModule())
    registerKotlinModule()
  }

  companion object {
    @JvmStatic
    fun assessmentDecisionPairs(): Stream<Arguments> = Stream.of(
      of("ACCEPTED", ApiAssessmentDecision.accepted),
      of("REJECTED", ApiAssessmentDecision.rejected),
      of(null, null),
    )
  }

  private val assessmentTransformer = AssessmentTransformer(
    objectMapper,
    mockApplicationsTransformer,
    mockAssessmentClarificationNoteTransformer,
    mockAssessmentReferralHistoryNoteTransformer,
    mockUserTransformer,
    mockPersonTransformer,
    risksTransformer,
  )

  private val allocatedToUser = UserEntityFactory()
    .withYieldedProbationRegion {
      ProbationRegionEntityFactory()
        .withYieldedApArea { ApAreaEntityFactory().produce() }
        .produce()
    }
    .produce()

  private val approvedPremisesAssessmentFactory = ApprovedPremisesAssessmentEntityFactory()
    .withApplication(mockk<ApprovedPremisesApplicationEntity>())
    .withId(UUID.fromString("7d0d3b38-5bc3-45c7-95eb-4d714cbd0db1"))
    .withAssessmentSchema(
      ApprovedPremisesAssessmentJsonSchemaEntity(
        id = UUID.fromString("aeeb6992-6485-4600-9c35-19479819c544"),
        addedAt = OffsetDateTime.now(),
        schema = "{}",
      ),
    )
    .withDecision(JpaAssessmentDecision.REJECTED)
    .withRejectionRationale("reasoning")
    .withData("{\"data\": \"something\"}")
    .withCreatedAt(OffsetDateTime.parse("2022-12-14T12:05:00Z"))
    .withSubmittedAt(OffsetDateTime.parse("2022-12-14T12:06:00Z"))
    .withAllocatedToUser(allocatedToUser)

  private val temporaryAccommodationAssessmentFactory = TemporaryAccommodationAssessmentEntityFactory()
    .withApplication(mockk<TemporaryAccommodationApplicationEntity>())
    .withId(UUID.fromString("7d0d3b38-5bc3-45c7-95eb-4d714cbd0db1"))
    .withAssessmentSchema(
      TemporaryAccommodationAssessmentJsonSchemaEntity(
        id = UUID.fromString("aeeb6992-6485-4600-9c35-19479819c544"),
        addedAt = OffsetDateTime.now(),
        schema = "{}",
      ),
    )
    .withDecision(JpaAssessmentDecision.REJECTED)
    .withRejectionRationale("reasoning")
    .withData("{\"data\": \"something\"}")
    .withCreatedAt(OffsetDateTime.parse("2022-12-14T12:05:00Z"))
    .withSubmittedAt(OffsetDateTime.parse("2022-12-14T12:06:00Z"))
    .withAllocatedToUser(allocatedToUser)

  private val approvedPremisesUser = mockk<ApprovedPremisesUser>()
  private val temporaryAccommodationUser = mockk<TemporaryAccommodationUser>()

  @BeforeEach
  fun setup() {
    every { mockApplicationsTransformer.transformJpaToApi(any<ApplicationEntity>(), any()) } answers {
      when (it.invocation.args[0]) {
        is ApprovedPremisesApplicationEntity -> mockk<ApprovedPremisesApplication>()
        is TemporaryAccommodationApplicationEntity -> mockk<TemporaryAccommodationApplication>()
        else -> fail("Unknown application entity type")
      }
    }
    every { mockAssessmentClarificationNoteTransformer.transformJpaToApi(any()) } returns mockk()
    every { mockAssessmentReferralHistoryNoteTransformer.transformJpaToApi(any()) } returns mockk()
    every { mockUserTransformer.transformJpaToApi(any(), ServiceName.approvedPremises) } returns approvedPremisesUser
    every { mockUserTransformer.transformJpaToApi(any(), ServiceName.temporaryAccommodation) } returns temporaryAccommodationUser
  }

  @Test
  fun `transformJpaToApi transforms correctly`() {
    val assessment = approvedPremisesAssessmentFactory.produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk()) as ApprovedPremisesAssessment

    assertThat(result.id).isEqualTo(UUID.fromString("7d0d3b38-5bc3-45c7-95eb-4d714cbd0db1"))
    assertThat(result.schemaVersion).isEqualTo(UUID.fromString("aeeb6992-6485-4600-9c35-19479819c544"))
    assertThat(result.decision).isEqualTo(ApiAssessmentDecision.rejected)
    assertThat(result.rejectionRationale).isEqualTo("reasoning")
    assertThat(result.createdAt).isEqualTo(Instant.parse("2022-12-14T12:05:00Z"))
    assertThat(result.submittedAt).isEqualTo(Instant.parse("2022-12-14T12:06:00Z"))
    assertThat(result.allocatedToStaffMember).isEqualTo(approvedPremisesUser)

    verify { mockUserTransformer.transformJpaToApi(allocatedToUser, ServiceName.approvedPremises) }
  }

  @Test
  fun `transformJpaToApi for Approved Premises sets a pending status when there is a clarification note with no response`() {
    val assessment = approvedPremisesAssessmentFactory.withDecision(null).produce()

    val clarificationNotes = mutableListOf(
      AssessmentClarificationNoteEntityFactory()
        .withAssessment(assessment)
        .withResponse("Some text")
        .withCreatedBy(
          UserEntityFactory()
            .withYieldedProbationRegion {
              ProbationRegionEntityFactory()
                .withYieldedApArea { ApAreaEntityFactory().produce() }
                .produce()
            }
            .produce(),
        )
        .produce(),
      AssessmentClarificationNoteEntityFactory()
        .withAssessment(assessment)
        .withCreatedBy(
          UserEntityFactory()
            .withYieldedProbationRegion {
              ProbationRegionEntityFactory()
                .withYieldedApArea { ApAreaEntityFactory().produce() }
                .produce()
            }
            .produce(),
        )
        .produce(),
    )

    assessment.clarificationNotes = clarificationNotes

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(ApprovedPremisesAssessment::class.java)
    result as ApprovedPremisesAssessment
    assertThat(result.status).isEqualTo(ApprovedPremisesAssessmentStatus.awaitingResponse)
  }

  @Test
  fun `transformJpaToApi for Approved Premises sets a completed status when there is a decision`() {
    val assessment = approvedPremisesAssessmentFactory
      .withDecision(JpaAssessmentDecision.ACCEPTED)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(ApprovedPremisesAssessment::class.java)
    result as ApprovedPremisesAssessment
    assertThat(result.status).isEqualTo(ApprovedPremisesAssessmentStatus.completed)
  }

  @Test
  fun `transformJpaToApi for Approved Premises sets a deallocated status when there is a deallocated timestamp`() {
    val assessment = approvedPremisesAssessmentFactory
      .withDecision(null)
      .withReallocatedAt(OffsetDateTime.now())
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(ApprovedPremisesAssessment::class.java)
    result as ApprovedPremisesAssessment
    assertThat(result.status).isEqualTo(ApprovedPremisesAssessmentStatus.reallocated)
  }

  @Test
  fun `transformJpaToApi for Approved Premises sets an inProgress status when there is no decision and the assessment has data`() {
    val assessment = approvedPremisesAssessmentFactory
      .withData("{\"data\": \"something\"}")
      .withDecision(null)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(ApprovedPremisesAssessment::class.java)
    result as ApprovedPremisesAssessment
    assertThat(result.status).isEqualTo(ApprovedPremisesAssessmentStatus.inProgress)
  }

  @Test
  fun `transformJpaToApi for Approved Premises sets a notStarted status when there is no decision and the assessment has no data`() {
    val assessment = approvedPremisesAssessmentFactory
      .withData(null)
      .withDecision(null)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(ApprovedPremisesAssessment::class.java)
    result as ApprovedPremisesAssessment
    assertThat(result.status).isEqualTo(ApprovedPremisesAssessmentStatus.notStarted)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation sets an unallocated status when there is no allocated user`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withDecision(null)
      .withoutAllocatedToUser()
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.status).isEqualTo(TemporaryAccommodationAssessmentStatus.unallocated)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation sets an inReview status when there is an allocated user`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withDecision(null)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.status).isEqualTo(TemporaryAccommodationAssessmentStatus.inReview)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation sets a readyToPlace status when the assessment is approved`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withDecision(JpaAssessmentDecision.ACCEPTED)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.status).isEqualTo(TemporaryAccommodationAssessmentStatus.readyToPlace)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation sets a closed status when the assessment is approved and has been completed`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withDecision(JpaAssessmentDecision.ACCEPTED)
      .withCompletedAt(OffsetDateTime.now())
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.status).isEqualTo(TemporaryAccommodationAssessmentStatus.closed)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation sets a rejected status when the assessment is rejected`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withDecision(JpaAssessmentDecision.REJECTED)
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.status).isEqualTo(TemporaryAccommodationAssessmentStatus.rejected)
  }

  @Test
  fun `transformJpaToApi for Temporary Accommodation serializes the summary data blob correctly`() {
    val assessment = temporaryAccommodationAssessmentFactory
      .withSummaryData("{\"num\": 50, \"text\": \"Hello world!\"}")
      .produce()

    val result = assessmentTransformer.transformJpaToApi(assessment, mockk())

    assertThat(result).isInstanceOf(TemporaryAccommodationAssessment::class.java)
    result as TemporaryAccommodationAssessment
    assertThat(result.summaryData).isEqualTo(
      objectMapper.valueToTree(
        object {
          val num = 50
          val text = "Hello world!"
        },
      ),
    )
  }

  @Test
  fun `transform domain to api summary - temporary application`() {
    val domainSummary = DomainAssessmentSummary(
      type = "temporary-accommodation",
      id = UUID.randomUUID(),
      applicationId = UUID.randomUUID(),
      createdAt = OffsetDateTime.now(),
      riskRatings = null,
      arrivalDate = null,
      completed = false,
      decision = null,
      crn = randomStringMultiCaseWithNumbers(6),
      isAllocated = true,
      status = null,
    )

    every { mockPersonTransformer.transformModelToPersonApi(any()) } returns mockk<Person>()
    val apiSummary = assessmentTransformer.transformDomainToApiSummary(domainSummary, mockk())

    assertThat(apiSummary).isInstanceOf(TemporaryAccommodationAssessmentSummary::class.java)
    apiSummary as TemporaryAccommodationAssessmentSummary
    assertThat(apiSummary.id).isEqualTo(domainSummary.id)
    assertThat(apiSummary.applicationId).isEqualTo(domainSummary.applicationId)
    assertThat(apiSummary.createdAt).isEqualTo(domainSummary.createdAt.toInstant())
    assertThat(apiSummary.status).isEqualTo(TemporaryAccommodationAssessmentStatus.inReview)
    assertThat(apiSummary.decision).isNull()
    assertThat(apiSummary.risks).isNull()
    assertThat(apiSummary.person).isNotNull
  }

  @Test
  fun `transform domain to api summary - approved premises`() {
    val personRisks = PersonRisksFactory().produce()
    val domainSummary = DomainAssessmentSummary(
      type = "approved-premises",
      id = UUID.randomUUID(),
      applicationId = UUID.randomUUID(),
      createdAt = OffsetDateTime.now(),
      riskRatings = objectMapper.writeValueAsString(personRisks),
      arrivalDate = OffsetDateTime.now().randomDateTimeBefore(),
      completed = false,
      decision = "ACCEPTED",
      crn = randomStringMultiCaseWithNumbers(6),
      isAllocated = true,
      status = DomainAssessmentSummaryStatus.AWAITING_RESPONSE.name,
    )

    every { mockPersonTransformer.transformModelToPersonApi(any()) } returns mockk<Person>()
    val apiSummary = assessmentTransformer.transformDomainToApiSummary(domainSummary, mockk())

    assertThat(apiSummary).isInstanceOf(ApprovedPremisesAssessmentSummary::class.java)
    apiSummary as ApprovedPremisesAssessmentSummary
    assertThat(apiSummary.id).isEqualTo(domainSummary.id)
    assertThat(apiSummary.applicationId).isEqualTo(domainSummary.applicationId)
    assertThat(apiSummary.createdAt).isEqualTo(domainSummary.createdAt.toInstant())
    assertThat(apiSummary.arrivalDate).isEqualTo(domainSummary.arrivalDate?.toInstant())
    assertThat(apiSummary.status).isEqualTo(ApprovedPremisesAssessmentStatus.awaitingResponse)
    assertThat(apiSummary.risks).isEqualTo(risksTransformer.transformDomainToApi(personRisks, domainSummary.crn))
    assertThat(apiSummary.person).isNotNull
  }
}
