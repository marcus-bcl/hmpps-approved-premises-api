package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.datasource

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ApDeliusContextApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ClientResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.datasource.ApDeliusContextApiOffenderDetailsDataSource
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CaseAccessFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CaseSummaryFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.OffenderDetailSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.UserOffenderAccess
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.CaseSummaries
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.UserAccess
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.asCaseAccess
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.asCaseSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.asOffenderDetailSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.asUserOffenderAccess
import java.util.stream.Stream

class ApDeliusContextApiOffenderDetailsDataSourceTest {
  private val mockApDeliusContextApiClient = mockk<ApDeliusContextApiClient>()

  private val apDeliusContextApiOffenderDetailsDataSource = ApDeliusContextApiOffenderDetailsDataSource(mockApDeliusContextApiClient)

  @ParameterizedTest
  @MethodSource("cacheableOffenderDetailSummaryClientResults")
  fun `getOffenderDetailSummary returns response from AP Delius Context API call`(
    expectedResult: ClientResult<OffenderDetailSummary>,
  ) {
    every { mockApDeliusContextApiClient.getSummariesForCrns(listOf("SOME-CRN")) } returns
      expectedResult.map { CaseSummaries(listOf(it.asCaseSummary())) }

    val result = apDeliusContextApiOffenderDetailsDataSource.getOffenderDetailSummary("SOME-CRN")

    assertThat(result).isEqualTo(expectedResult)
  }

  @Test
  fun `getOffenderDetailSummaries returns transformed response from AP Delius Context API call`() {
    val crns = listOf("CRN-A", "CRN-B", "CRN-C")

    val caseSummaries = listOf(
      CaseSummaryFactory()
        .withCrn("CRN-A")
        .produce(),
      CaseSummaryFactory()
        .withCrn("CRN-B")
        .produce(),
      CaseSummaryFactory()
        .withCrn("CRN-C")
        .produce(),
    )

    val expectedResults = caseSummaries.map { ClientResult.Success(HttpStatus.OK, it.asOffenderDetailSummary(), false) }

    every { mockApDeliusContextApiClient.getSummariesForCrns(crns) } returns ClientResult.Success(
      HttpStatus.OK,
      CaseSummaries(caseSummaries),
      false,
    )

    val results = apDeliusContextApiOffenderDetailsDataSource.getOffenderDetailSummaries(crns)

    assertThat(results).isEqualTo(expectedResults)
  }

  @ParameterizedTest
  @MethodSource("userOffenderAccessClientResults")
  fun `getUserAccessForOffenderCrn returns transformed response from AP Delius Context API call`(
    expectedResult: ClientResult<UserOffenderAccess>,
  ) {
    every { mockApDeliusContextApiClient.getUserAccessForCrns("DELIUS-USER", listOf("SOME-CRN")) } returns
      expectedResult.map { UserAccess(listOf(it.asCaseAccess("SOME-CRN"))) }

    val result = apDeliusContextApiOffenderDetailsDataSource.getUserAccessForOffenderCrn("DELIUS-USER", "SOME-CRN")

    assertThat(result).isEqualTo(expectedResult)
  }

  @Test
  fun `getUserAccessForOffenderCrns returns transformed response from AP Delius Context API`() {
    val crns = listOf("CRN-A", "CRN-B", "CRN-C")
    val caseAccesses = listOf(
      CaseAccessFactory()
        .withCrn("CRN-A")
        .produce(),
      CaseAccessFactory()
        .withCrn("CRN-B")
        .produce(),
      CaseAccessFactory()
        .withCrn("CRN-C")
        .produce(),
    )
    val expectedResults = caseAccesses.map { ClientResult.Success(HttpStatus.OK, it.asUserOffenderAccess(), false) }

    every { mockApDeliusContextApiClient.getUserAccessForCrns("DELIUS-USER", crns) } returns ClientResult.Success(
      HttpStatus.OK,
      UserAccess(caseAccesses),
      false,
    )

    val results = apDeliusContextApiOffenderDetailsDataSource.getUserAccessForOffenderCrns("DELIUS-USER", crns)

    assertThat(results).isEqualTo(expectedResults)
  }

  private companion object {
    @JvmStatic
    fun cacheableOffenderDetailSummaryClientResults(): Stream<Arguments> {
      val successBody = CaseSummaryFactory()
        .withCrn("SOME-CRN")
        .produce()
        .asOffenderDetailSummary()

      return allClientResults(successBody)
        .filter { it !is ClientResult.Failure.PreemptiveCacheTimeout }
        .intoArgumentStream()
    }

    @JvmStatic
    fun <T> cacheTimeoutClientResult() =
      ClientResult.Failure.PreemptiveCacheTimeout<T>("some-cache", "some-cache-key", 1000)

    @JvmStatic
    fun userOffenderAccessClientResults(): Stream<Arguments> {
      val successBody = UserOffenderAccess(
        userRestricted = false,
        userExcluded = false,
        restrictionMessage = null,
      )

      return allClientResults(successBody).intoArgumentStream()
    }

    private fun <T> allClientResults(successBody: T): List<ClientResult<T>> = listOf(
      ClientResult.Failure.CachedValueUnavailable("some-cache-key"),
      ClientResult.Failure.StatusCode(
        HttpMethod.GET,
        "/",
        HttpStatus.NOT_FOUND,
        null,
        false,
      ),
      ClientResult.Failure.Other(
        HttpMethod.POST,
        "/",
        RuntimeException("Some error"),
      ),
      cacheTimeoutClientResult(),
      ClientResult.Success(HttpStatus.OK, successBody, true),
    )

    private fun <T> List<ClientResult<T>>.intoArgumentStream(): Stream<Arguments> = this.stream().map { Arguments.of(it) }
  }
}