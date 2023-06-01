package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.NewPlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PlacementApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given a User`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given an Application`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given an Assessment for Approved Premises`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentDecision
import java.util.UUID

class PlacementApplicationsTest : IntegrationTestBase() {

  @Nested
  inner class CreatePlacementApplicationTest {
    @Test
    fun `creating a placement application JWT returns 401`() {
      webTestClient.post()
        .uri("/placement-applications")
        .bodyValue(
          NewPlacementApplication(
            applicationId = UUID.randomUUID(),
          ),
        )
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `creating a placement application when the application does not exist returns 404`() {
      `Given a User` { _, jwt ->
        webTestClient.post()
          .uri("/placement-applications")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            NewPlacementApplication(
              applicationId = UUID.randomUUID(),
            ),
          )
          .exchange()
          .expectStatus()
          .isNotFound()
      }
    }

    @Test
    fun `creating a placement application when the application does not belong to the user returns 401`() {
      `Given a User` { _, jwt ->
        `Given a User` { otherUser, _ ->
          `Given an Application`(createdByUser = otherUser) { application ->
            webTestClient.post()
              .uri("/placement-applications")
              .header("Authorization", "Bearer $jwt")
              .bodyValue(
                NewPlacementApplication(
                  applicationId = application.id,
                ),
              )
              .exchange()
              .expectStatus()
              .isForbidden()
          }
        }
      }
    }

    @Test
    fun `creating a placement application when the application does not have an assessment returns an error`() {
      `Given a User` { user, jwt ->
        `Given an Application`(createdByUser = user) { application ->
          webTestClient.post()
            .uri("/placement-applications")
            .header("Authorization", "Bearer $jwt")
            .bodyValue(
              NewPlacementApplication(
                applicationId = application.id,
              ),
            )
            .exchange()
            .expectStatus()
            .isBadRequest()
        }
      }
    }

    @Test
    fun `creating a placement application when the assessment has been rejected returns an error`() {
      `Given a User` { user, jwt ->
        `Given an Assessment for Approved Premises`(decision = AssessmentDecision.REJECTED, allocatedToUser = user, createdByUser = user) { _, application ->
          approvedPremisesPlacementApplicationJsonSchemaEntityFactory.produceAndPersist {
            withPermissiveSchema()
          }

          webTestClient.post()
            .uri("/placement-applications")
            .header("Authorization", "Bearer $jwt")
            .bodyValue(
              NewPlacementApplication(
                applicationId = application.id,
              ),
            )
            .exchange()
            .expectStatus()
            .isBadRequest()
        }
      }
    }

    @Test
    fun `creating a placement application when the application belongs to the user returns successfully`() {
      `Given a User` { user, jwt ->
        `Given an Assessment for Approved Premises`(decision = AssessmentDecision.ACCEPTED, allocatedToUser = user, createdByUser = user) { _, application ->
          val schema = approvedPremisesPlacementApplicationJsonSchemaEntityFactory.produceAndPersist {
            withPermissiveSchema()
          }

          val rawResult = webTestClient.post()
            .uri("/placement-applications")
            .header("Authorization", "Bearer $jwt")
            .bodyValue(
              NewPlacementApplication(
                applicationId = application.id,
              ),
            )
            .exchange()
            .expectStatus()
            .isOk
            .returnResult<String>()
            .responseBody
            .blockFirst()

          val body = objectMapper.readValue(rawResult, PlacementApplication::class.java)

          assertThat(body.applicationId).isEqualTo(application.id)
          assertThat(body.applicationId).isEqualTo(application.id)
          assertThat(body.outdatedSchema).isEqualTo(false)
          assertThat(body.createdAt).isNotNull()
          assertThat(body.schemaVersion).isEqualTo(schema.id)
        }
      }
    }
  }

  @Nested
  inner class GetPlacementApplicationTest {
    @Test
    fun `getting a placement request application JWT returns 401`() {
      `Given a User` { user, _ ->
        `Given a Placement Application`(
          createdByUser = user,
          schema = approvedPremisesPlacementApplicationJsonSchemaEntityFactory.produceAndPersist {
            withPermissiveSchema()
          },
        ) { placementApplicationEntity ->
          webTestClient.get()
            .uri("/placement-request-applications/${placementApplicationEntity.id}")
            .exchange()
            .expectStatus()
            .isUnauthorized
        }
      }
    }

    @Test
    fun `getting a nonexistent placement request application returns 404`() {
      `Given a User` { _, jwt ->
        webTestClient.get()
          .uri("/placement-applications/${UUID.randomUUID()}")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isNotFound
      }
    }

    @Test
    fun `getting a placement application returns the transformed object`() {
      `Given a User` { user, jwt ->
        `Given a Placement Application`(
          createdByUser = user,
          schema = approvedPremisesPlacementApplicationJsonSchemaEntityFactory.produceAndPersist {
            withPermissiveSchema()
          },
        ) { placementApplicationEntity ->
          val rawResult = webTestClient.get()
            .uri("/placement-applications/${placementApplicationEntity.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus()
            .isOk
            .returnResult<String>()
            .responseBody
            .blockFirst()

          val body = objectMapper.readValue(rawResult, PlacementApplication::class.java)

          assertThat(body.id).isEqualTo(placementApplicationEntity.id)
          assertThat(body.applicationId).isEqualTo(placementApplicationEntity.application.id)
          assertThat(body.createdByUserId).isEqualTo(placementApplicationEntity.createdByUser.id)
          assertThat(body.schemaVersion).isEqualTo(placementApplicationEntity.schemaVersion.id)
          assertThat(body.createdAt).isEqualTo(placementApplicationEntity.createdAt.toInstant())
          assertThat(body.submittedAt).isNull()
        }
      }
    }
  }
}
