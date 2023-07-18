package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.transformer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesUser
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.AssessmentDecision
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.BookingSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cancellation
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Person
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PersonRisks
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementRequest
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApAreaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesApplicationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.AssessmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BookingEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CharacteristicEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LocalAuthorityEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PlacementRequestEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PlacementRequirementsEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ProbationRegionEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CancellationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequestEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.OffenderDetailSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.prisonsapi.InmateDetail
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.BookingSummaryTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.CancellationTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.PersonTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.PlacementRequestDetailTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.PlacementRequestTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.RisksTransformer
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.UserTransformer
import java.time.OffsetDateTime

class PlacementRequestDetailTransformerTest {
  private val mockPlacementRequestTransformer = mockk<PlacementRequestTransformer>()
  private val mockCancellationTransformer = mockk<CancellationTransformer>()
  private val mockBookingSummaryTransformer = mockk<BookingSummaryTransformer>()

  private val placementRequestDetailTransformer = PlacementRequestDetailTransformer(
    mockPlacementRequestTransformer,
    mockCancellationTransformer,
    mockBookingSummaryTransformer,
  )

  private val mockCancellation = mockk<Cancellation>()

  private val mockPlacementRequestEntity = mockk<PlacementRequestEntity>()
  private val mockOffenderDetailSummary = mockk<OffenderDetailSummary>()
  private val mockInmateDetail = mockk<InmateDetail>()
  private val mockBookingSummary = mockk<BookingSummary>()
  private val mockCancellationEntities = listOf(
    mockk<CancellationEntity>(),
    mockk<CancellationEntity>(),
  )

  @Test
  fun `it returns a PlacementRequestDetail object`() {
    val transformedPlacementRequest = getTransformedPlacementRequest()

    every { mockPlacementRequestEntity.booking } returns null

    every { mockCancellationTransformer.transformJpaToApi(any<CancellationEntity>()) } returns mockCancellation
    every { mockPlacementRequestTransformer.transformJpaToApi(mockPlacementRequestEntity, mockOffenderDetailSummary, mockInmateDetail) } returns transformedPlacementRequest

    val result = placementRequestDetailTransformer.transformJpaToApi(mockPlacementRequestEntity, mockOffenderDetailSummary, mockInmateDetail, mockCancellationEntities)

    assertThat(result.id).isEqualTo(transformedPlacementRequest.id)
    assertThat(result.gender).isEqualTo(transformedPlacementRequest.gender)
    assertThat(result.type).isEqualTo(transformedPlacementRequest.type)
    assertThat(result.expectedArrival).isEqualTo(transformedPlacementRequest.expectedArrival)
    assertThat(result.duration).isEqualTo(transformedPlacementRequest.duration)
    assertThat(result.location).isEqualTo(transformedPlacementRequest.location)
    assertThat(result.radius).isEqualTo(transformedPlacementRequest.radius)
    assertThat(result.essentialCriteria).isEqualTo(transformedPlacementRequest.essentialCriteria)
    assertThat(result.desirableCriteria).isEqualTo(transformedPlacementRequest.desirableCriteria)
    assertThat(result.person).isEqualTo(transformedPlacementRequest.person)
    assertThat(result.risks).isEqualTo(transformedPlacementRequest.risks)
    assertThat(result.applicationId).isEqualTo(transformedPlacementRequest.applicationId)
    assertThat(result.assessmentId).isEqualTo(transformedPlacementRequest.assessmentId)
    assertThat(result.releaseType).isEqualTo(transformedPlacementRequest.releaseType)
    assertThat(result.status).isEqualTo(transformedPlacementRequest.status)
    assertThat(result.assessmentDecision).isEqualTo(transformedPlacementRequest.assessmentDecision)
    assertThat(result.assessmentDate).isEqualTo(transformedPlacementRequest.assessmentDate)
    assertThat(result.assessor).isEqualTo(transformedPlacementRequest.assessor)
    assertThat(result.notes).isEqualTo(transformedPlacementRequest.notes)
    assertThat(result.cancellations).isEqualTo(listOf(mockCancellation, mockCancellation))
    assertThat(result.booking).isNull()

    verify(exactly = 1) {
      mockPlacementRequestTransformer.transformJpaToApi(mockPlacementRequestEntity, mockOffenderDetailSummary, mockInmateDetail)
    }

    mockCancellationEntities.forEach {
      verify(exactly = 1) {
        mockCancellationTransformer.transformJpaToApi(it)
      }
    }
  }

  @Test
  fun `it returns a PlacementRequestDetail object with a booking`() {
    val booking = BookingEntityFactory()
      .withServiceName(ServiceName.approvedPremises)
      .withYieldedPremises {
        ApprovedPremisesEntityFactory()
          .withYieldedProbationRegion {
            ProbationRegionEntityFactory()
              .withYieldedApArea { ApAreaEntityFactory().produce() }
              .produce()
          }
          .withLocalAuthorityArea(LocalAuthorityEntityFactory().produce())
          .produce()
      }
      .produce()

    val transformedPlacementRequest = getTransformedPlacementRequest()

    every { mockPlacementRequestEntity.booking } returns booking

    every { mockCancellationTransformer.transformJpaToApi(any<CancellationEntity>()) } returns mockCancellation
    every { mockPlacementRequestTransformer.transformJpaToApi(mockPlacementRequestEntity, mockOffenderDetailSummary, mockInmateDetail) } returns transformedPlacementRequest
    every { mockBookingSummaryTransformer.transformJpaToApi(booking) } returns mockBookingSummary

    val result = placementRequestDetailTransformer.transformJpaToApi(mockPlacementRequestEntity, mockOffenderDetailSummary, mockInmateDetail, mockCancellationEntities)

    assertThat(result.booking).isEqualTo(mockBookingSummary)

    verify(exactly = 1) {
      mockBookingSummaryTransformer.transformJpaToApi(booking)
    }
  }

  private fun getTransformedPlacementRequest(): PlacementRequest {
    val user = UserEntityFactory()
      .withUnitTestControlProbationRegion()
      .produce()

    val application = ApprovedPremisesApplicationEntityFactory()
      .withReleaseType("licence")
      .withCreatedByUser(user)
      .produce()

    val submittedAt = OffsetDateTime.now()

    val assessment = AssessmentEntityFactory()
      .withAllocatedToUser(user)
      .withApplication(application)
      .withSubmittedAt(submittedAt)
      .produce()

    val placementRequirementsEntity = PlacementRequirementsEntityFactory()
      .withApplication(application)
      .withAssessment(assessment)
      .withEssentialCriteria(
        listOf(
          CharacteristicEntityFactory().withPropertyName("isSemiSpecialistMentalHealth").produce(),
        ),
      )
      .withDesirableCriteria(
        listOf(
          CharacteristicEntityFactory().withPropertyName("isWheelchairDesignated").produce(),
        ),
      )
      .produce()

    val placementRequestEntity = PlacementRequestEntityFactory()
      .withApplication(application)
      .withAssessment(assessment)
      .withAllocatedToUser(user)
      .withPlacementRequirements(placementRequirementsEntity)
      .produce()

    val mockAssessmentTransformer = mockk<AssessmentTransformer>()
    val mockPersonTransformer = mockk<PersonTransformer>()
    val mockRisksTransformer = mockk<RisksTransformer>()
    val mockUserTransformer = mockk<UserTransformer>()

    val realPlacementRequestTransformer = PlacementRequestTransformer(
      mockPersonTransformer,
      mockRisksTransformer,
      mockAssessmentTransformer,
      mockUserTransformer,
    )

    every { mockAssessmentTransformer.transformJpaDecisionToApi(assessment.decision) } returns AssessmentDecision.accepted
    every { mockPersonTransformer.transformModelToApi(mockOffenderDetailSummary, mockInmateDetail) } returns mockk<Person>()
    every { mockRisksTransformer.transformDomainToApi(application.riskRatings!!, application.crn) } returns mockk<PersonRisks>()
    every { mockUserTransformer.transformJpaToApi(user, ServiceName.approvedPremises) } returns mockk<ApprovedPremisesUser>()

    return realPlacementRequestTransformer.transformJpaToApi(placementRequestEntity, mockOffenderDetailSummary, mockInmateDetail)
  }
}
