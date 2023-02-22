package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.InvalidParam
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ValidationError
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class ProblemResponsesTest : IntegrationTestBase() {
  @Test
  fun `An invalid request body will return a 400 when the expected body root is an object and an array is provided`() {
    val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt()

    val validationResult = webTestClient.post()
      .uri("/deserialization-test/object")
      .header("Authorization", "Bearer $jwt")
      .header("Content-Type", "application/json")
      .bodyValue("[]")
      .exchange()
      .expectStatus()
      .isBadRequest
      .returnResult<ValidationError>()
      .responseBody
      .blockFirst()

    assertThat(validationResult.detail).isEqualTo("Expected an object but got an array")
  }

  @Test
  fun `An invalid request body will return a 400 when the expected body root is an array and an object is provided`() {
    val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt()

    val validationResult = webTestClient.post()
      .uri("/deserialization-test/array")
      .header("Authorization", "Bearer $jwt")
      .header("Content-Type", "application/json")
      .bodyValue("{}")
      .exchange()
      .expectStatus()
      .isBadRequest
      .returnResult<ValidationError>()
      .responseBody
      .blockFirst()

    assertThat(validationResult.detail).isEqualTo("Expected an array but got an object")
  }

  @Test
  fun `An invalid request body will return a 400 with details of all problems when the expected body root is an object and an object is provided`() {
    val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt()

    val validationResult = webTestClient.post()
      .uri("/deserialization-test/object")
      .header("Authorization", "Bearer $jwt")
      .header("Content-Type", "application/json")
      .bodyValue(
        """
          {
             "requiredInt": null,
             "optionalInt": 123,
             "optionalObject": [],
             "requiredObject": {
                "requiredString": null,
                "optionalBoolean": 1234,
                "optionalLocalDate": false
             },
             "requiredListOfInts": ["not", "ints", false],
             "requiredListOfObjects": null,
             "optionalListOfObjects": [{
                  "requiredString": null,
                  "optionalBoolean": 1234,
                  "optionalLocalDate": false
               },
               null],
             "aLocalDate": "not a date",
             "aLocalDateTime": "not a date time",
             "anOffsetDateTime": "not an offset date time",
             "aUUID": "not a uuid"
          }
        """
      )
      .exchange()
      .expectStatus()
      .isBadRequest
      .returnResult<ValidationError>()
      .responseBody
      .blockFirst()

    assertThat(validationResult!!.invalidParams).containsAll(
      listOf(
        InvalidParam(propertyName = "$.optionalListOfObjects[0].optionalBoolean", errorType = "expectedBoolean"),
        InvalidParam(propertyName = "$.optionalListOfObjects[0].optionalLocalDate", errorType = "expectedString"),
        InvalidParam(propertyName = "$.optionalListOfObjects[0].requiredString", errorType = "empty"),
        InvalidParam(propertyName = "$.optionalListOfObjects[1]", errorType = "expectedObject"),
        InvalidParam(propertyName = "$.optionalObject", errorType = "expectedObject"),
        InvalidParam(propertyName = "$.requiredInt", errorType = "empty"),
        InvalidParam(propertyName = "$.requiredListOfInts[0]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$.requiredListOfInts[1]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$.requiredListOfInts[2]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$.requiredListOfObjects", errorType = "empty"),
        InvalidParam(propertyName = "$.requiredObject.optionalBoolean", errorType = "expectedBoolean"),
        InvalidParam(propertyName = "$.requiredObject.optionalLocalDate", errorType = "expectedString"),
        InvalidParam(propertyName = "$.requiredObject.requiredString", errorType = "empty"),
        InvalidParam(propertyName = "$.aLocalDate", errorType = "invalid"),
        InvalidParam(propertyName = "$.aLocalDateTime", errorType = "invalid"),
        InvalidParam(propertyName = "$.anOffsetDateTime", errorType = "invalid"),
        InvalidParam(propertyName = "$.aUUID", errorType = "invalid")
      )
    )
  }

  @Test
  fun `An invalid request body will return a 400 with details of all problems when the expected body root is an array and an array is provided`() {
    val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt()

    val validationResult = webTestClient.post()
      .uri("/deserialization-test/array")
      .header("Authorization", "Bearer $jwt")
      .header("Content-Type", "application/json")
      .bodyValue(
        """
          [{
             "requiredInt": null,
             "optionalInt": 123,
             "optionalObject": [],
             "requiredObject": {
                "requiredString": null,
                "optionalBoolean": 1234,
                "optionalLocalDate": false,
                "aLocalDate": "not a date",
                "aLocalDateTime": "not a date time",
                "anOffsetDateTime": "not an offset date time",
                "aUUID": "not a uuid"
             },
             "requiredListOfInts": ["not", "ints", false],
             "requiredListOfObjects": null,
             "optionalListOfObjects": [{
                  "requiredString": null,
                  "optionalBoolean": 1234,
                  "optionalLocalDate": false
               },
               null],
             "aLocalDate": "not a date",
             "aLocalDateTime": "not a date time",
             "anOffsetDateTime": "not an offset date time",
             "aUUID": "not a uuid"
          }]
        """
      )
      .exchange()
      .expectStatus()
      .isBadRequest
      .returnResult<ValidationError>()
      .responseBody
      .blockFirst()

    assertThat(validationResult!!.invalidParams).containsAll(
      listOf(
        InvalidParam(propertyName = "$[0].optionalListOfObjects[0].optionalBoolean", errorType = "expectedBoolean"),
        InvalidParam(propertyName = "$[0].optionalListOfObjects[0].optionalLocalDate", errorType = "expectedString"),
        InvalidParam(propertyName = "$[0].optionalListOfObjects[0].requiredString", errorType = "empty"),
        InvalidParam(propertyName = "$[0].optionalListOfObjects[1]", errorType = "expectedObject"),
        InvalidParam(propertyName = "$[0].optionalObject", errorType = "expectedObject"),
        InvalidParam(propertyName = "$[0].requiredInt", errorType = "empty"),
        InvalidParam(propertyName = "$[0].requiredListOfInts[0]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$[0].requiredListOfInts[1]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$[0].requiredListOfInts[2]", errorType = "expectedNumber"),
        InvalidParam(propertyName = "$[0].requiredListOfObjects", errorType = "empty"),
        InvalidParam(propertyName = "$[0].requiredObject.optionalBoolean", errorType = "expectedBoolean"),
        InvalidParam(propertyName = "$[0].requiredObject.optionalLocalDate", errorType = "expectedString"),
        InvalidParam(propertyName = "$[0].requiredObject.requiredString", errorType = "empty"),
        InvalidParam(propertyName = "$[0].aLocalDate", errorType = "invalid"),
        InvalidParam(propertyName = "$[0].aLocalDateTime", errorType = "invalid"),
        InvalidParam(propertyName = "$[0].anOffsetDateTime", errorType = "invalid"),
        InvalidParam(propertyName = "$[0].aUUID", errorType = "invalid")
      )
    )
  }

  @Test
  fun `An unhandled exception will not return a problem response with the exception message in the detail property`() {
    val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt()

    val validationResult = webTestClient.get()
      .uri("/unhandled-exception")
      .header("Authorization", "Bearer $jwt")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .returnResult<ValidationError>()
      .responseBody
      .blockFirst()

    assertThat(validationResult!!.detail).isEqualTo("There was an unexpected problem")
  }
}

@RestController
class DeserializationTestController {
  @PostMapping(path = ["deserialization-test/object"], consumes = ["application/json"])
  fun testDeserialization(@RequestBody body: DeserializationTestBody): ResponseEntity<Unit> {
    return ResponseEntity.ok(Unit)
  }

  @PostMapping(path = ["deserialization-test/array"], consumes = ["application/json"])
  fun testDeserialization(@RequestBody body: List<DeserializationTestBody>): ResponseEntity<Unit> {
    return ResponseEntity.ok(Unit)
  }

  @GetMapping(path = ["unhandled-exception"])
  fun unhandledException(): ResponseEntity<Unit> {
    throw RuntimeException("I am an unhandled exception")
  }
}

data class DeserializationTestBody(
  val requiredInt: Int,
  val optionalInt: Int?,
  val optionalObject: DeserializationTestBodyNested?,
  val requiredObject: DeserializationTestBodyNested,
  val requiredListOfInts: List<Int>,
  val requiredListOfObjects: List<DeserializationTestBodyNested>,
  val optionalListOfObjects: List<DeserializationTestBodyNested>?,
  val aLocalDate: LocalDate,
  val aLocalDateTime: LocalDateTime,
  val anOffsetDateTime: OffsetDateTime,
  val aUUID: UUID
)

data class DeserializationTestBodyNested(
  val requiredString: String,
  val optionalBoolean: Boolean?,
  val optionalLocalDate: LocalDate?
)
