package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.service.cas2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ApOASysContextApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ClientResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ClientResult.Failure.StatusCode
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.CommunityApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.PrisonsApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.InmateDetailFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.OffenderDetailsSummaryFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.RoshRatingsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.PersonInfoResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.RiskStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.oasyscontext.RiskLevel
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.oasyscontext.RoshRatings
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.prisonsapi.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.prisonsapi.InOutStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.prisonsapi.InmateDetail
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.AuthorisableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas2.OffenderService
import java.time.LocalDate
import java.time.OffsetDateTime

class OffenderServiceTest {
  private val mockCommunityApiClient = mockk<CommunityApiClient>()
  private val mockPrisonsApiClient = mockk<PrisonsApiClient>()
  private val mockApOASysContextApiClient = mockk<ApOASysContextApiClient>()

  private val objectMapper = ObjectMapper().apply {
    registerModule(Jdk8Module())
    registerModule(JavaTimeModule())
    registerKotlinModule()
  }

  private val offenderService = OffenderService(
    mockCommunityApiClient,
    mockPrisonsApiClient,
    mockApOASysContextApiClient,
  )

  @Nested
  inner class GetRisksByCrn {
    // Note that Tier, Mappa and Flags are all hardcoded to NotFound
    // and these unused 'envelopes' will be removed.

    @Test
    fun `returns NotFound result when Community API Client returns 404`() {
      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait("a-crn") } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/a-crn", HttpStatus.NOT_FOUND, null)

      assertThat(offenderService.getRiskByCrn("a-crn") is AuthorisableActionResult.NotFound).isTrue
    }

    @Test
    fun `throws when Community API Client returns other non-2xx status code`() {
      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait("a-crn") } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/a-crn", HttpStatus.BAD_REQUEST, null)

      val exception = assertThrows<RuntimeException> { offenderService.getRiskByCrn("a-crn") }
      assertThat(exception.message).isEqualTo("Unable to complete GET request to /secure/offenders/crn/a-crn: 400 BAD_REQUEST")
    }

    @Test
    fun `returns NotFound envelope for RoSH when client returns 404`() {
      val crn = "a-crn"

      mockExistingNonLaoOffender()
      mock404RoSH(crn)

      val result = offenderService.getRiskByCrn(crn)
      assertThat(result is AuthorisableActionResult.Success).isTrue
      result as AuthorisableActionResult.Success
      assertThat(result.entity.roshRisks.status).isEqualTo(RiskStatus.NotFound)

      assertThat(result.entity.tier.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.mappa.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.flags.status).isEqualTo(RiskStatus.NotFound)
    }

    @Test
    fun `returns Error envelope for RoSH when client returns 500`() {
      val crn = "a-crn"

      mockExistingNonLaoOffender()
      mock500RoSH(crn)

      val result = offenderService.getRiskByCrn(crn)
      assertThat(result is AuthorisableActionResult.Success).isTrue
      result as AuthorisableActionResult.Success
      assertThat(result.entity.roshRisks.status).isEqualTo(RiskStatus.Error)

      assertThat(result.entity.tier.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.mappa.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.flags.status).isEqualTo(RiskStatus.NotFound)
    }

    @Test
    fun `returns Retrieved envelopes with expected contents for RoSH when client returns 200`() {
      val crn = "a-crn"

      mockExistingNonLaoOffender()

      mock200RoSH(
        crn,
        RoshRatingsFactory().apply {
          withDateCompleted(OffsetDateTime.parse("2022-09-06T13:45:00Z"))
          withAssessmentId(34853487)
          withRiskChildrenCommunity(RiskLevel.LOW)
          withRiskPublicCommunity(RiskLevel.MEDIUM)
          withRiskKnownAdultCommunity(RiskLevel.HIGH)
          withRiskStaffCommunity(RiskLevel.VERY_HIGH)
        }.produce(),
      )

      val result = offenderService.getRiskByCrn(crn)
      assertThat(result is AuthorisableActionResult.Success).isTrue
      result as AuthorisableActionResult.Success

      assertThat(result.entity.roshRisks.status).isEqualTo(RiskStatus.Retrieved)
      result.entity.roshRisks.value!!.let {
        assertThat(it.lastUpdated).isEqualTo(LocalDate.parse("2022-09-06"))
        assertThat(it.overallRisk).isEqualTo("Very High")
        assertThat(it.riskToChildren).isEqualTo("Low")
        assertThat(it.riskToPublic).isEqualTo("Medium")
        assertThat(it.riskToKnownAdult).isEqualTo("High")
        assertThat(it.riskToStaff).isEqualTo("Very High")
      }

      assertThat(result.entity.tier.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.tier.value).isNull()

      assertThat(result.entity.mappa.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.mappa.value).isNull()

      assertThat(result.entity.flags.status).isEqualTo(RiskStatus.NotFound)
      assertThat(result.entity.flags.value).isNull()
    }
  }

  @Nested
  inner class GetOffenderByCrn {
    @Test
    fun `returns NotFound result when Client returns 404`() {
      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait("a-crn") } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/a-crn", HttpStatus.NOT_FOUND, null)

      assertThat(offenderService.getOffenderByCrn("a-crn") is AuthorisableActionResult.NotFound).isTrue
    }

    @Test
    fun `throws when Client returns other non-2xx status code except 403`() {
      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait("a-crn") } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/a-crn", HttpStatus.BAD_REQUEST, null)

      val exception = assertThrows<RuntimeException> { offenderService.getOffenderByCrn("a-crn") }
      assertThat(exception.message).isEqualTo("Unable to complete GET request to /secure/offenders/crn/a-crn: 400 BAD_REQUEST")
    }
  }

  @Nested
  inner class GetInmateDetailByNomsNumber {
    @Test
    fun `returns not found result when for Offender without Application or Booking and Client responds with 404`() {
      val crn = "CRN123"
      val nomsNumber = "NOMS321"

      every { mockPrisonsApiClient.getInmateDetailsWithWait(nomsNumber) } returns StatusCode(HttpMethod.GET, "/api/offenders/$nomsNumber", HttpStatus.NOT_FOUND, null)

      val result = offenderService.getInmateDetailByNomsNumber(crn, nomsNumber)

      assertThat(result is AuthorisableActionResult.NotFound).isTrue
    }

    @Test
    fun `returns not found result when for Offender with Application or Booking and Client responds with 404`() {
      val crn = "CRN123"
      val nomsNumber = "NOMS321"

      every { mockPrisonsApiClient.getInmateDetailsWithWait(nomsNumber) } returns StatusCode(HttpMethod.GET, "/api/offenders/$nomsNumber", HttpStatus.NOT_FOUND, null)

      val result = offenderService.getInmateDetailByNomsNumber(crn, nomsNumber)

      assertThat(result is AuthorisableActionResult.NotFound).isTrue
    }

    @Test
    fun `returns unauthorised result when Client responds with 403`() {
      val crn = "CRN123"
      val nomsNumber = "NOMS321"

      every { mockPrisonsApiClient.getInmateDetailsWithWait(nomsNumber) } returns StatusCode(HttpMethod.GET, "/api/offenders/$nomsNumber", HttpStatus.FORBIDDEN, null)

      val result = offenderService.getInmateDetailByNomsNumber(crn, nomsNumber)

      assertThat(result is AuthorisableActionResult.Unauthorised).isTrue
    }

    @Test
    fun `returns successfully when Client responds with 200`() {
      val crn = "CRN123"
      val nomsNumber = "NOMS321"

      every { mockPrisonsApiClient.getInmateDetailsWithWait(nomsNumber) } returns ClientResult.Success(
        HttpStatus.OK,
        InmateDetail(
          offenderNo = nomsNumber,
          inOutStatus = InOutStatus.IN,
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "AGY",
            locationId = 89,
            description = "AGENCY DESCRIPTION",
            agencyName = "AGENCY NAME",
          ),
        ),
      )

      val result = offenderService.getInmateDetailByNomsNumber(crn, nomsNumber)

      assertThat(result is AuthorisableActionResult.Success)
      result as AuthorisableActionResult.Success
      assertThat(result.entity).isNotNull
      assertThat(result.entity!!.offenderNo).isEqualTo(nomsNumber)
      assertThat(result.entity!!.inOutStatus).isEqualTo(InOutStatus.IN)
      assertThat(result.entity!!.assignedLivingUnit).isEqualTo(
        AssignedLivingUnit(
          agencyId = "AGY",
          locationId = 89,
          description = "AGENCY DESCRIPTION",
          agencyName = "AGENCY NAME",
        ),
      )
    }
  }

  @Nested
  inner class GetInfoForPerson {
    @Test
    fun `returns NotFound if Community API responds with a 404`() {
      val crn = "ABC123"
      val nomisUsername = "USER"

      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait(crn) } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/ABC123", HttpStatus.NOT_FOUND, null, true)

      val result = offenderService.getInfoForPerson(crn, nomisUsername)

      assertThat(result is PersonInfoResult.NotFound).isTrue
    }

    @Test
    fun `returns Unknown if Community API responds with a 500`() {
      val crn = "ABC123"
      val nomisUsername = "USER"

      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait(crn) } returns StatusCode(HttpMethod.GET, "/secure/offenders/crn/ABC123", HttpStatus.INTERNAL_SERVER_ERROR, null, true)

      val result = offenderService.getInfoForPerson(crn, nomisUsername)

      assertThat(result is PersonInfoResult.Unknown).isTrue
      result as PersonInfoResult.Unknown
      assertThat(result.throwable).isNotNull()
    }

    @Test
    fun `returns Full for CRN with both Community API and Prison API data where Community API links to Prison API`() {
      val crn = "ABC123"
      val nomsNumber = "NOMSABC"
      val nomisUsername = "USER"

      val offenderDetails = OffenderDetailsSummaryFactory()
        .withCrn(crn)
        .withNomsNumber(nomsNumber)
        .produce()

      every { mockCommunityApiClient.getOffenderDetailSummaryWithWait(crn) } returns ClientResult.Success(
        status = HttpStatus.OK,
        body = offenderDetails,
      )

      val inmateDetail = InmateDetailFactory()
        .withOffenderNo(nomsNumber)
        .produce()

      every { mockPrisonsApiClient.getInmateDetailsWithWait(nomsNumber) } returns ClientResult.Success(
        status = HttpStatus.OK,
        body = inmateDetail,
      )

      val result = offenderService.getInfoForPerson(crn, nomisUsername)

      assertThat(result is PersonInfoResult.Success.Full).isTrue
      result as PersonInfoResult.Success.Full
      assertThat(result.crn).isEqualTo(crn)
      assertThat(result.offenderDetailSummary).isEqualTo(offenderDetails)
      assertThat(result.inmateDetail).isEqualTo(inmateDetail)
    }
  }

  private fun mockExistingNonLaoOffender() {
    val resultBody = OffenderDetailsSummaryFactory()
      .withCrn("a-crn")
      .withFirstName("Bob")
      .withLastName("Doe")
      .withCurrentRestriction(false)
      .withCurrentExclusion(false)
      .produce()

    every { mockCommunityApiClient.getOffenderDetailSummaryWithWait("a-crn") } returns ClientResult.Success(HttpStatus.OK, resultBody)
  }

  private fun mock404RoSH(crn: String) = every { mockApOASysContextApiClient.getRoshRatings(crn) } returns StatusCode(HttpMethod.GET, "/rosh/a-crn", HttpStatus.NOT_FOUND, body = null)

  private fun mock500RoSH(crn: String) = every { mockApOASysContextApiClient.getRoshRatings(crn) } returns StatusCode(HttpMethod.GET, "/rosh/a-crn", HttpStatus.INTERNAL_SERVER_ERROR, body = null)

  private fun mock200RoSH(crn: String, body: RoshRatings) = every { mockApOASysContextApiClient.getRoshRatings(crn) } returns ClientResult.Success(HttpStatus.OK, body = body)
}