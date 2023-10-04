package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.cas2

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Application
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas2Application
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.Cas2ApplicationSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.FullPerson
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.NewApplication
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.SubmitCas2Application
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UpdateApplicationType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UpdateCas2Application
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.OffenderDetailsSummaryFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.StaffUserTeamMembershipFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given a User`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given an Offender`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.CommunityAPI_mockNotFoundOffenderDetailsCall
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.CommunityAPI_mockOffenderUserAccessCall
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.CommunityAPI_mockSuccessfulOffenderDetailsCall
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.PrisonAPI_mockNotFoundInmateDetailsCall
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import java.time.OffsetDateTime
import java.util.UUID

class Cas2ApplicationTest : IntegrationTestBase() {
  @SpykBean
  lateinit var realApplicationRepository: ApplicationRepository

  @AfterEach
  fun afterEach() {
    // SpringMockK does not correctly clear mocks for @SpyKBeans that are also a @Repository, causing mocked behaviour
    // in one test to show up in another (see https://github.com/Ninja-Squad/springmockk/issues/85)
    // Manually clearing after each test seems to fix this.
    clearMocks(realApplicationRepository)
  }

  @Nested
  inner class MissingJwt {
    @Test
    fun `Get all applications without JWT returns 401`() {
      webTestClient.get()
        .uri("/cas2/applications")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `Get single application without JWT returns 401`() {
      webTestClient.get()
        .uri("/cas2/applications/9b785e59-b85c-4be0-b271-d9ac287684b6")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `Create new application without JWT returns 401`() {
      webTestClient.post()
        .uri("/cas2/applications")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }
  }

  @Nested
  inner class GetToIndex {
    @Test
    fun `Get all applications returns 200 with correct body - when the service is CAS2`() {
      `Given a User` { userEntity, jwt ->
        `Given a User` { otherUser, _ ->
          `Given an Offender` { offenderDetails, _ ->
            cas2ApplicationJsonSchemaRepository.deleteAll()

            val applicationSchema = cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
              withAddedAt(OffsetDateTime.now())
              withId(UUID.randomUUID())
            }

            val cas2ApplicationEntity = cas2ApplicationEntityFactory.produceAndPersist {
              withApplicationSchema(applicationSchema)
              withCreatedByUser(userEntity)
              withCrn(offenderDetails.otherIds.crn)
              withData("{}")
            }

            val otherCas2ApplicationEntity = cas2ApplicationEntityFactory.produceAndPersist {
              withApplicationSchema(applicationSchema)
              withCreatedByUser(otherUser)
              withCrn(offenderDetails.otherIds.crn)
              withData("{}")
            }

            CommunityAPI_mockOffenderUserAccessCall(userEntity.deliusUsername, offenderDetails.otherIds.crn, false, false)

            val rawResponseBody = webTestClient.get()
              .uri("/cas2/applications")
              .header("Authorization", "Bearer $jwt")
              .header("X-Service-Name", ServiceName.cas2.value)
              .exchange()
              .expectStatus()
              .isOk
              .returnResult<String>()
              .responseBody
              .blockFirst()

            val responseBody =
              objectMapper.readValue(rawResponseBody, object : TypeReference<List<Cas2ApplicationSummary>>() {})

            Assertions.assertThat(responseBody).anyMatch {
              cas2ApplicationEntity.id == it.id &&
                cas2ApplicationEntity.crn == it.person.crn &&
                cas2ApplicationEntity.createdAt.toInstant() == it.createdAt &&
                cas2ApplicationEntity.createdByUser.id == it.createdByUserId &&
                cas2ApplicationEntity.submittedAt?.toInstant() == it.submittedAt
            }

            Assertions.assertThat(responseBody).noneMatch {
              otherCas2ApplicationEntity.id == it.id
            }
          }
        }
      }
    }

    @Test
    fun `Get list of applications returns 500 when a person cannot be found`() {
      `Given a User`(
        staffUserDetailsConfigBlock = {
          withTeams(listOf(StaffUserTeamMembershipFactory().withCode("TEAM1").produce()))
        },
      ) { userEntity, jwt ->
        val crn = "X1234"

        produceAndPersistBasicApplication(crn, userEntity, "TEAM1")
        CommunityAPI_mockNotFoundOffenderDetailsCall(crn)
        loadPreemptiveCacheForOffenderDetails(crn)

        CommunityAPI_mockOffenderUserAccessCall(userEntity.deliusUsername, crn, false, false)

        webTestClient.get()
          .uri("/cas2/applications")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .is5xxServerError
          .expectBody()
          .jsonPath("$.detail").isEqualTo("Unable to get Person via crn: $crn")
      }
    }

    @Test
    fun `Get list of applications returns successfully when the person cannot be fetched from the prisons API`() {
      `Given a User`(
        staffUserDetailsConfigBlock = {
          withTeams(listOf(StaffUserTeamMembershipFactory().withCode("TEAM1").produce()))
        },
      ) { userEntity, jwt ->
        val crn = "X1234"

        val application = produceAndPersistBasicApplication(crn, userEntity, "TEAM1")

        val offenderDetails = OffenderDetailsSummaryFactory()
          .withCrn(crn)
          .withNomsNumber("ABC123")
          .produce()

        CommunityAPI_mockOffenderUserAccessCall(userEntity.deliusUsername, offenderDetails.otherIds.crn, false, false)

        CommunityAPI_mockSuccessfulOffenderDetailsCall(offenderDetails)
        loadPreemptiveCacheForOffenderDetails(offenderDetails.otherIds.crn)
        PrisonAPI_mockNotFoundInmateDetailsCall(offenderDetails.otherIds.nomsNumber!!)
        loadPreemptiveCacheForInmateDetails(offenderDetails.otherIds.nomsNumber!!)

        val rawResponseBody = webTestClient.get()
          .uri("/cas2/applications")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isOk
          .returnResult<String>()
          .responseBody
          .blockFirst()

        val responseBody =
          objectMapper.readValue(
            rawResponseBody,
            object :
              TypeReference<List<Cas2ApplicationSummary>>() {},
          )

        Assertions.assertThat(responseBody).matches {
          val person = it[0].person as FullPerson

          application.id == it[0].id &&
            application.crn == person.crn &&
            person.nomsNumber == null &&
            person.status == FullPerson.Status.unknown &&
            person.prisonName == null
        }
      }
    }
  }

  @Nested
  inner class GetToShow {
    @Test
    fun `Get single application returns 200 with correct body`() {
      `Given a User` { userEntity, jwt ->
        `Given an Offender` { offenderDetails, inmateDetails ->
          cas2ApplicationJsonSchemaRepository.deleteAll()

          val newestJsonSchema = cas2ApplicationJsonSchemaEntityFactory
            .produceAndPersist {
              withAddedAt(OffsetDateTime.parse("2022-09-21T12:45:00+01:00"))
              withSchema(
                """
        {
          "${"\$schema"}": "https://json-schema.org/draft/2020-12/schema",
          "${"\$id"}": "https://example.com/product.schema.json",
          "title": "Thing",
          "description": "A thing",
          "type": "object",
          "properties": {
            "thingId": {
              "description": "The unique identifier for a thing",
              "type": "integer"
            }
          },
          "required": [ "thingId" ]
        }
        """,
              )
            }

          val applicationEntity = cas2ApplicationEntityFactory.produceAndPersist {
            withApplicationSchema(newestJsonSchema)
            withCrn(offenderDetails.otherIds.crn)
            withCreatedByUser(userEntity)
            withData(
              """
          {
             "thingId": 123
          }
          """,
            )
          }

          CommunityAPI_mockOffenderUserAccessCall(userEntity.deliusUsername, offenderDetails.otherIds.crn, false, false)

          val rawResponseBody = webTestClient.get()
            .uri("/cas2/applications/${applicationEntity.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus()
            .isOk
            .returnResult<String>()
            .responseBody
            .blockFirst()

          val responseBody = objectMapper.readValue(
            rawResponseBody,
            Cas2Application::class.java,
          )

          Assertions.assertThat(responseBody).matches {
            applicationEntity.id == it.id &&
              applicationEntity.crn == it.person.crn &&
              applicationEntity.createdAt.toInstant() == it.createdAt &&
              applicationEntity.createdByUser.id == it.createdByUserId &&
              applicationEntity.submittedAt?.toInstant() == it.submittedAt &&
              serializableToJsonNode(applicationEntity.data) == serializableToJsonNode(it.data) &&
              newestJsonSchema.id == it.schemaVersion && !it.outdatedSchema
          }
        }
      }
    }

    @Test
    fun `Get single application returns successfully when the person cannot be fetched from the prisons API`() {
      `Given a User`(
        staffUserDetailsConfigBlock = {
          withTeams(listOf(StaffUserTeamMembershipFactory().withCode("TEAM1").produce()))
        },
      ) { userEntity, jwt ->
        val crn = "X1234"

        val application = produceAndPersistBasicApplication(crn, userEntity, "TEAM1")

        val offenderDetails = OffenderDetailsSummaryFactory()
          .withCrn(crn)
          .withNomsNumber("ABC123")
          .produce()

        CommunityAPI_mockSuccessfulOffenderDetailsCall(offenderDetails)
        loadPreemptiveCacheForOffenderDetails(offenderDetails.otherIds.crn)
        PrisonAPI_mockNotFoundInmateDetailsCall(offenderDetails.otherIds.nomsNumber!!)
        loadPreemptiveCacheForInmateDetails(offenderDetails.otherIds.nomsNumber!!)

        CommunityAPI_mockOffenderUserAccessCall(userEntity.deliusUsername, offenderDetails.otherIds.crn, false, false)

        val rawResponseBody = webTestClient.get()
          .uri("/cas2/applications/${application.id}")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isOk
          .returnResult<String>()
          .responseBody
          .blockFirst()

        val responseBody = objectMapper.readValue(
          rawResponseBody,
          Cas2Application::class.java,
        )

        Assertions.assertThat(responseBody.person is FullPerson).isTrue

        Assertions.assertThat(responseBody).matches {
          val person = it.person as FullPerson

          application.id == it.id &&
            application.crn == person.crn &&
            person.nomsNumber == null &&
            person.status == FullPerson.Status.unknown &&
            person.prisonName == null
        }
      }
    }
  }

  @Nested
  inner class PostToCreate {
    @Test
    fun `Create new application for CAS-2 returns 201 with correct body and Location header`() {
      `Given a User` { userEntity, jwt ->
        `Given an Offender` { offenderDetails, _ ->
          val applicationSchema =
            cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
              withAddedAt(OffsetDateTime.now())
              withId(UUID.randomUUID())
            }

          val result = webTestClient.post()
            .uri("/cas2/applications")
            .header("Authorization", "Bearer $jwt")
            .header("X-Service-Name", ServiceName.cas2.value)
            .bodyValue(
              NewApplication(
                crn = offenderDetails.otherIds.crn,
              ),
            )
            .exchange()
            .expectStatus()
            .isCreated
            .returnResult(Cas2Application::class.java)

          Assertions.assertThat(result.responseHeaders["Location"]).anyMatch {
            it.matches(Regex("/cas2/applications/.+"))
          }

          Assertions.assertThat(result.responseBody.blockFirst()).matches {
            it.person.crn == offenderDetails.otherIds.crn &&
              it.schemaVersion == applicationSchema.id
          }
        }
      }
    }

    @Test
    fun `Create new application returns 404 when a person cannot be found`() {
      `Given a User` { userEntity, jwt ->
        val crn = "X1234"

        CommunityAPI_mockNotFoundOffenderDetailsCall(crn)
        loadPreemptiveCacheForOffenderDetails(crn)

        cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
          withAddedAt(OffsetDateTime.now())
          withId(UUID.randomUUID())
        }

        webTestClient.post()
          .uri("/cas2/applications")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            NewApplication(
              crn = crn,
            ),
          )
          .exchange()
          .expectStatus()
          .isNotFound
          .expectBody()
          .jsonPath("$.detail").isEqualTo("No Offender with an ID of $crn could be found")
      }
    }
  }

  @Nested
  inner class PutToUpdate {
    @Test
    fun `Update existing CAS2 application returns 200 with correct body`() {
      `Given a User` { submittingUser, jwt ->
        `Given an Offender` { offenderDetails, _ ->
          val applicationId = UUID.fromString("22ceda56-98b2-411d-91cc-ace0ab8be872")

          val applicationSchema =
            cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
              withAddedAt(OffsetDateTime.now())
              withId(UUID.randomUUID())
              withSchema(
                """
                {
                  "${"\$schema"}": "https://json-schema.org/draft/2020-12/schema",
                  "${"\$id"}": "https://example.com/product.schema.json",
                  "title": "Thing",
                  "description": "A thing",
                  "type": "object",
                  "properties": {
                    "thingId": {
                      "description": "The unique identifier for a thing",
                      "type": "integer"
                    }
                  },
                  "required": [ "thingId" ]
                }
              """,
              )
            }

          cas2ApplicationEntityFactory.produceAndPersist {
            withCrn(offenderDetails.otherIds.crn)
            withId(applicationId)
            withApplicationSchema(applicationSchema)
            withCreatedByUser(submittingUser)
          }

          val resultBody = webTestClient.put()
            .uri("/cas2/applications/$applicationId")
            .header("Authorization", "Bearer $jwt")
            .bodyValue(
              UpdateCas2Application(
                data = mapOf("thingId" to 123),
                type = UpdateApplicationType.CAS2,
              ),
            )
            .exchange()
            .expectStatus()
            .isOk
            .returnResult(String::class.java)
            .responseBody
            .blockFirst()

          val result = objectMapper.readValue(resultBody, Application::class.java)

          Assertions.assertThat(result.person.crn).isEqualTo(offenderDetails.otherIds.crn)
        }
      }
    }
  }

  @Nested
  inner class PostToSubmit {
    @Test
    fun `Submit Cas2 application returns 200`() {
      `Given a User`(
        staffUserDetailsConfigBlock = {
          withTeams(
            listOf(
              StaffUserTeamMembershipFactory().produce(),
            ),
          )
        },
      ) { submittingUser, jwt ->
        `Given a User` { userEntity, _ ->
          `Given an Offender` { offenderDetails, inmateDetails ->
            val applicationId = UUID.fromString("22ceda56-98b2-411d-91cc-ace0ab8be872")

            val applicationSchema =
              cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
                withAddedAt(OffsetDateTime.now())
                withId(UUID.randomUUID())
                withSchema(
                  """
                {
                  "${"\$schema"}": "https://json-schema.org/draft/2020-12/schema",
                  "${"\$id"}": "https://example.com/product.schema.json",
                  "title": "Thing",
                  "description": "A thing",
                  "type": "object",
                  "properties": {},
                  "required": []
                }
              """,
                )
              }

            cas2ApplicationEntityFactory.produceAndPersist {
              withCrn(offenderDetails.otherIds.crn)
              withId(applicationId)
              withApplicationSchema(applicationSchema)
              withCreatedByUser(submittingUser)
              withData(
                """
                {}
              """,
              )
            }

            webTestClient.post()
              .uri("/cas2/applications/$applicationId/submission")
              .header("Authorization", "Bearer $jwt")
              .header("X-Service-Name", ServiceName.cas2.value)
              .bodyValue(
                SubmitCas2Application(
                  translatedDocument = {},
                  type = "CAS2",
                ),
              )
              .exchange()
              .expectStatus()
              .isOk
          }
        }
      }
    }

    @Test
    fun `When several concurrent submit application requests occur, only one is successful, all others return 400`() {
      `Given a User`(
        staffUserDetailsConfigBlock = {
          withTeams(
            listOf(
              StaffUserTeamMembershipFactory().produce(),
            ),
          )
        },
      ) { submittingUser, jwt ->
        `Given an Offender` { offenderDetails, inmateDetails ->
          val applicationId = UUID.fromString("22ceda56-98b2-411d-91cc-ace0ab8be872")

          val applicationSchema = cas2ApplicationJsonSchemaEntityFactory
            .produceAndPersist {
              withAddedAt(OffsetDateTime.now())
              withId(UUID.randomUUID())
              withSchema(
                """
            {
              "${"\$schema"}": "https://json-schema.org/draft/2020-12/schema",
              "${"\$id"}": "https://example.com/product.schema.json",
              "title": "Thing",
              "description": "A thing",
              "type": "object",
              "properties": {}
              },
              "required": [  ]
            }
          """,
              )
            }

          cas2ApplicationEntityFactory.produceAndPersist {
            withCrn(offenderDetails.otherIds.crn)
            withId(applicationId)
            withApplicationSchema(applicationSchema)
            withCreatedByUser(submittingUser)
            withData(
              """
                {}
              """,
            )
          }

          every { realApplicationRepository.save(any()) } answers {
            Thread.sleep(1000)
            it.invocation.args[0] as ApplicationEntity
          }

          val responseStatuses = mutableListOf<HttpStatus>()

          (1..10).map {
            val thread = Thread {
              webTestClient.post()
                .uri("/cas2/applications/$applicationId/submission")
                .header("Authorization", "Bearer $jwt")
                .bodyValue(
                  SubmitCas2Application(
                    translatedDocument = {},
                    type = "CAS2",
                  ),
                )
                .exchange()
                .returnResult<String>()
                .consumeWith {
                  synchronized(responseStatuses) {
                    responseStatuses += it.status
                  }
                }
            }

            thread.start()

            thread
          }.forEach(Thread::join)

          Assertions.assertThat(responseStatuses.count { it.value() == 200 }).isEqualTo(1)
          Assertions.assertThat(responseStatuses.count { it.value() == 400 }).isEqualTo(9)
        }
      }
    }
  }

  private fun serializableToJsonNode(serializable: Any?): JsonNode {
    if (serializable == null) return NullNode.instance
    if (serializable is String) return objectMapper.readTree(serializable)

    return objectMapper.readTree(objectMapper.writeValueAsString(serializable))
  }

  private fun produceAndPersistBasicApplication(
    crn: String,
    userEntity: UserEntity,
    managingTeamCode: String,
  ): Cas2ApplicationEntity {
    val jsonSchema = cas2ApplicationJsonSchemaEntityFactory.produceAndPersist {
      withAddedAt(OffsetDateTime.parse("2022-09-21T12:45:00+01:00"))
      withSchema(
        """
        {
          "${"\$schema"}": "https://json-schema.org/draft/2020-12/schema",
          "${"\$id"}": "https://example.com/product.schema.json",
          "title": "Thing",
          "description": "A thing",
          "type": "object",
          "properties": {
            "thingId": {
              "description": "The unique identifier for a thing",
              "type": "integer"
            }
          },
          "required": [ "thingId" ]
        }
        """,
      )
    }

    val application = cas2ApplicationEntityFactory.produceAndPersist {
      withApplicationSchema(jsonSchema)
      withCrn(crn)
      withCreatedByUser(userEntity)
      withData(
        """
          {
             "thingId": 123
          }
          """,
      )
    }

    return application
  }
}