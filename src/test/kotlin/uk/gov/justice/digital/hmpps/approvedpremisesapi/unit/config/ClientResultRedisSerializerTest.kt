package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ClientResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.config.ClientResultRedisSerializer

class ClientResultRedisSerializerTest {
  private val objectMapper = jacksonObjectMapper()
  private val clientResponseRedisSerializer = ClientResultRedisSerializer(objectMapper, object : TypeReference<ClientResponseBody>() {})

  @Test
  fun `ClientResult-StatusCodeFailure responses are serialized and deserialized correctly`() {
    val clientResult = ClientResult.StatusCodeFailure<ClientResponseBody>(
      method = HttpMethod.GET,
      path = "/an/endpoint",
      status = HttpStatus.BAD_REQUEST,
      body = "Something went wrong"
    )

    val cachedByteArray = clientResponseRedisSerializer.serialize(clientResult)
    val deserializedCacheValue = clientResponseRedisSerializer.deserialize(cachedByteArray)

    assertThat(deserializedCacheValue is ClientResult.StatusCodeFailure).isTrue
    deserializedCacheValue as ClientResult.StatusCodeFailure
    assertThat(deserializedCacheValue.method).isEqualTo(HttpMethod.GET)
    assertThat(deserializedCacheValue.path).isEqualTo("/an/endpoint")
    assertThat(deserializedCacheValue.status).isEqualTo(HttpStatus.BAD_REQUEST)
    assertThat(deserializedCacheValue.body).isEqualTo("Something went wrong")
  }

  @Test
  fun `ClientResult-OtherFailure responses are serialized and deserialized correctly`() {
    val clientResult = ClientResult.OtherFailure<ClientResponseBody>(
      method = HttpMethod.GET,
      path = "/an/endpoint",
      exception = RuntimeException("Something went wrong")
    )

    val cachedByteArray = clientResponseRedisSerializer.serialize(clientResult)
    val deserializedCacheValue = clientResponseRedisSerializer.deserialize(cachedByteArray)

    assertThat(deserializedCacheValue is ClientResult.OtherFailure).isTrue
    deserializedCacheValue as ClientResult.OtherFailure
    assertThat(deserializedCacheValue.method).isEqualTo(HttpMethod.GET)
    assertThat(deserializedCacheValue.path).isEqualTo("/an/endpoint")
    assertThat(deserializedCacheValue.exception.message).isEqualTo(clientResult.exception.message)
  }

  @Test
  fun `ClientResult-Success responses are serialized and deserialized correctly`() {
    val clientResult = ClientResult.Success(
      status = HttpStatus.OK,
      body = ClientResponseBody(
        property = "hello"
      )
    )

    val cachedByteArray = clientResponseRedisSerializer.serialize(clientResult)
    val cachedString = String(cachedByteArray)
    val deserializedCacheValue = clientResponseRedisSerializer.deserialize(cachedByteArray)

    assertThat(deserializedCacheValue is ClientResult.Success).isTrue
    deserializedCacheValue as ClientResult.Success
    assertThat(deserializedCacheValue.status).isEqualTo(HttpStatus.OK)
    assertThat(deserializedCacheValue.body).isEqualTo(clientResult.body)
  }
}

data class ClientResponseBody(
  val property: String
)
