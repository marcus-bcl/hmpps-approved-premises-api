package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.cas3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.cas3.model.CAS3PersonArrivedEvent
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.cas3.model.CAS3PersonDepartedEvent
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.cas3.model.EventType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.events.cas3.CAS3PersonArrivedEventDetailsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.events.cas3.CAS3PersonDepartedEventDetailsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainEventType
import java.time.Instant
import java.util.UUID

class DomainEventTest : IntegrationTestBase() {
  @Test
  fun `Get 'person arrived' event without JWT returns 401`() {
    webTestClient.get()
      .uri("/events/cas3/person-arrived/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Get 'person arrived' event without ROLE_APPROVED_PREMISES_EVENTS returns 403`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
    )

    webTestClient.get()
      .uri("/events/cas3/person-arrived/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `Get 'person arrived' event returns 200 with correct body`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
      roles = listOf("ROLE_APPROVED_PREMISES_EVENTS"),
    )

    val eventId = UUID.randomUUID()

    val envelopedData = CAS3PersonArrivedEvent(
      id = eventId,
      timestamp = Instant.now(),
      eventType = EventType.personArrived,
      eventDetails = CAS3PersonArrivedEventDetailsFactory().produce(),
    )

    val event = domainEventFactory.produceAndPersist {
      withId(eventId)
      withType(DomainEventType.CAS3_PERSON_ARRIVED)
      withData(objectMapper.writeValueAsString(envelopedData))
    }

    val response = webTestClient.get()
      .uri("/events/cas3/person-arrived/${event.id}")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CAS3PersonArrivedEvent::class.java)
      .returnResult()

    assertThat(response.responseBody).isEqualTo(envelopedData)
  }

  @Test
  fun `Get 'person departed' event without JWT returns 401`() {
    webTestClient.get()
      .uri("/events/cas3/person-departed/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Get 'person departed' event without ROLE_APPROVED_PREMISES_EVENTS returns 403`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
    )

    webTestClient.get()
      .uri("/events/cas3/person-departed/e4b004f8-bdb2-4bf6-9958-db602be71ed3")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `Get 'person departed' event returns 200 with correct body`() {
    val jwt = jwtAuthHelper.createClientCredentialsJwt(
      username = "username",
      roles = listOf("ROLE_APPROVED_PREMISES_EVENTS"),
    )

    val eventId = UUID.randomUUID()

    val envelopedData = CAS3PersonDepartedEvent(
      id = eventId,
      timestamp = Instant.now(),
      eventType = EventType.personDeparted,
      eventDetails = CAS3PersonDepartedEventDetailsFactory().produce(),
    )

    val event = domainEventFactory.produceAndPersist {
      withId(eventId)
      withType(DomainEventType.CAS3_PERSON_DEPARTED)
      withData(objectMapper.writeValueAsString(envelopedData))
    }

    val response = webTestClient.get()
      .uri("/events/cas3/person-departed/${event.id}")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(CAS3PersonDepartedEvent::class.java)
      .returnResult()

    assertThat(response.responseBody).isEqualTo(envelopedData)
  }
}