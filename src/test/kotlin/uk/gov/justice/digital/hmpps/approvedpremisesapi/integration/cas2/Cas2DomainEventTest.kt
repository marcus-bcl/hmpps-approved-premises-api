package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.cas2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.cas2.model.Cas2ApplicationSubmittedEvent
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.cas2.model.EventType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.events.cas2.Cas2ApplicationSubmittedEventDetailsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainEventType
import java.time.Instant
import java.util.UUID

class Cas2DomainEventTest : IntegrationTestBase() {
  @Test
  fun `Get CAS2 Application Submitted Event without JWT returns 401`() {
    webTestClient.get()
      .uri("/events/cas2/application-submitted/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Get CAS2 Application Submitted Event without ROLE_CAS2_EVENTS returns 403`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
    )

    webTestClient.get()
      .uri("/events/cas2/application-submitted/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `Get CAS2 Application Submitted Event with only ROLE_APPROVED_PREMISES_EVENTS returns 403`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
      roles = listOf("ROLE_APPROVED_PREMISES_EVENTS"),
    )

    webTestClient.get()
      .uri("/events/cas2/application-submitted/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `Get CAS2 Application Submitted Event returns 200 with correct body`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
      roles = listOf("ROLE_CAS2_EVENTS"),
    )

    val eventId = UUID.randomUUID()

    val eventToSave = Cas2ApplicationSubmittedEvent(
      id = eventId,
      timestamp = Instant.now(),
      eventType = EventType.applicationSubmitted,
      eventDetails = Cas2ApplicationSubmittedEventDetailsFactory().produce(),
    )

    val event = domainEventFactory.produceAndPersist {
      withId(eventId)
      withType(DomainEventType.CAS2_APPLICATION_SUBMITTED)
      withData(objectMapper.writeValueAsString(eventToSave))
    }

    val response = webTestClient.get()
      .uri("/events/cas2/application-submitted/${event.id}")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(Cas2ApplicationSubmittedEvent::class.java)
      .returnResult()

    assertThat(response.responseBody).isEqualTo(eventToSave)
  }
}