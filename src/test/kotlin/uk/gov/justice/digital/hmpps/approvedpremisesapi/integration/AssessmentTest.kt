package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.AssessmentAcceptance
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.AssessmentRejection
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.NewClarificationNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UpdatedClarificationNote
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given a User`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given an Offender`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentDecision
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.domainevent.SnsEventPersonReference
import uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.AssessmentTransformer
import java.time.LocalDate
import java.time.OffsetDateTime

class AssessmentTest : IntegrationTestBase() {
  @Autowired
  lateinit var inboundMessageListener: InboundMessageListener

  @Autowired
  lateinit var assessmentTransformer: AssessmentTransformer

  @BeforeEach
  fun clearMessages() {
    inboundMessageListener.clearMessages()
  }

  @Test
  fun `Get all assessments without JWT returns 401`() {
    webTestClient.get()
      .uri("/assessments")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Get all assessments returns 200 with correct body`() {
    `Given a User` { user, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
          withAddedAt(OffsetDateTime.now())
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(user)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(user)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        assessment.schemaUpToDate = true

        val reallocatedAssessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(user)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
          withReallocatedAt(OffsetDateTime.now())
        }

        reallocatedAssessment.schemaUpToDate = true

        webTestClient.get()
          .uri("/assessments")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .json(
            objectMapper.writeValueAsString(
              listOf(
                assessmentTransformer.transformJpaToApi(assessment, offenderDetails, inmateDetails)
              )
            )
          )
      }
    }
  }

  @Test
  fun `Get assessment by ID without JWT returns 401`() {
    webTestClient.get()
      .uri("/assessments/6966902f-9b7e-4fc7-96c4-b54ec02d16c9")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Get assessment by ID returns 200 with correct body`() {
    `Given a User` { userEntity, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
          withAddedAt(OffsetDateTime.now())
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(userEntity)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(userEntity)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        assessment.schemaUpToDate = true

        webTestClient.get()
          .uri("/assessments/${assessment.id}")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .json(
            objectMapper.writeValueAsString(
              assessmentTransformer.transformJpaToApi(assessment, offenderDetails, inmateDetails)
            )
          )
      }
    }
  }

  @Test
  fun `Accept assessment without JWT returns 401`() {
    webTestClient.post()
      .uri("/assessments/6966902f-9b7e-4fc7-96c4-b54ec02d16c9/acceptance")
      .bodyValue(AssessmentAcceptance(document = "{}"))
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Accept assessment returns 200, persists decision, emits SNS domain event message`() {
    `Given a User` { userEntity, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
          withAddedAt(OffsetDateTime.now())
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(userEntity)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(userEntity)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        assessment.schemaUpToDate = true

        webTestClient.post()
          .uri("/assessments/${assessment.id}/acceptance")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(AssessmentAcceptance(document = mapOf("document" to "value")))
          .exchange()
          .expectStatus()
          .isOk

        val persistedAssessment = assessmentRepository.findByIdOrNull(assessment.id)!!
        assertThat(persistedAssessment.decision).isEqualTo(AssessmentDecision.ACCEPTED)
        assertThat(persistedAssessment.document).isEqualTo("{\"document\":\"value\"}")
        assertThat(persistedAssessment.submittedAt).isNotNull

        var waitedCount = 0
        while (inboundMessageListener.messages.isEmpty()) {
          if (waitedCount == 30) throw RuntimeException("Never received SQS message from SNS topic")

          Thread.sleep(100)
          waitedCount += 1
        }

        val emittedMessage = inboundMessageListener.messages.last()

        assertThat(emittedMessage.eventType).isEqualTo("approved-premises.application.assessed")
        assertThat(emittedMessage.description).isEqualTo("An application has been assessed for an Approved Premises placement")
        assertThat(emittedMessage.detailUrl).matches("http://frontend/events/application-assessed/[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}")
        assertThat(emittedMessage.additionalInformation.applicationId).isEqualTo(assessment.application.id)
        assertThat(emittedMessage.personReference.identifiers).containsExactlyInAnyOrder(
          SnsEventPersonReference("CRN", offenderDetails.otherIds.crn),
          SnsEventPersonReference("NOMS", offenderDetails.otherIds.nomsNumber!!)
        )
      }
    }
  }

  @Test
  fun `Reject assessment without JWT returns 401`() {
    webTestClient.post()
      .uri("/assessments/6966902f-9b7e-4fc7-96c4-b54ec02d16c9/rejection")
      .bodyValue(AssessmentRejection(document = "{}", rejectionRationale = "reasoning"))
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Reject assessment returns 200, persists decision, emits SNS domain event message`() {
    `Given a User` { userEntity, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
          withAddedAt(OffsetDateTime.now())
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(userEntity)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(userEntity)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        assessment.schemaUpToDate = true

        webTestClient.post()
          .uri("/assessments/${assessment.id}/rejection")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(AssessmentRejection(document = mapOf("document" to "value"), rejectionRationale = "reasoning"))
          .exchange()
          .expectStatus()
          .isOk

        val persistedAssessment = assessmentRepository.findByIdOrNull(assessment.id)!!
        assertThat(persistedAssessment.decision).isEqualTo(AssessmentDecision.REJECTED)
        assertThat(persistedAssessment.document).isEqualTo("{\"document\":\"value\"}")
        assertThat(persistedAssessment.submittedAt).isNotNull

        var waitedCount = 0
        while (inboundMessageListener.messages.isEmpty()) {
          if (waitedCount == 30) throw RuntimeException("Never received SQS message from SNS topic")

          Thread.sleep(100)
          waitedCount += 1
        }

        val emittedMessage = inboundMessageListener.messages.last()

        assertThat(emittedMessage.eventType).isEqualTo("approved-premises.application.assessed")
        assertThat(emittedMessage.description).isEqualTo("An application has been assessed for an Approved Premises placement")
        assertThat(emittedMessage.detailUrl).matches("http://frontend/events/application-assessed/[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}")
        assertThat(emittedMessage.additionalInformation.applicationId).isEqualTo(assessment.application.id)
        assertThat(emittedMessage.personReference.identifiers).containsExactlyInAnyOrder(
          SnsEventPersonReference("CRN", offenderDetails.otherIds.crn),
          SnsEventPersonReference("NOMS", offenderDetails.otherIds.nomsNumber!!)
        )
      }
    }
  }

  @Test
  fun `Create clarification note returns 200 with correct body`() {
    `Given a User` { userEntity, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(userEntity)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(userEntity)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        webTestClient.post()
          .uri("/assessments/${assessment.id}/notes")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            NewClarificationNote(
              query = "some text"
            )
          )
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("$.query").isEqualTo("some text")
      }
    }
  }

  @Test
  fun `Update clarification note returns 201 with correct body`() {
    `Given a User` { userEntity, jwt ->
      `Given an Offender` { offenderDetails, inmateDetails ->
        val applicationSchema = approvedPremisesApplicationJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val assessmentSchema = approvedPremisesAssessmentJsonSchemaEntityFactory.produceAndPersist {
          withPermissiveSchema()
        }

        val application = approvedPremisesApplicationEntityFactory.produceAndPersist {
          withCrn(offenderDetails.otherIds.crn)
          withCreatedByUser(userEntity)
          withApplicationSchema(applicationSchema)
        }

        val assessment = assessmentEntityFactory.produceAndPersist {
          withAllocatedToUser(userEntity)
          withApplication(application)
          withAssessmentSchema(assessmentSchema)
        }

        val clarificationNote = assessmentClarificationNoteEntityFactory.produceAndPersist {
          withAssessment(assessment)
          withCreatedBy(userEntity)
        }

        webTestClient.put()
          .uri("/assessments/${assessment.id}/notes/${clarificationNote.id}")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            UpdatedClarificationNote(
              response = "some text",
              responseReceivedOn = LocalDate.parse("2022-03-04")
            )
          )
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("$.response").isEqualTo("some text")
          .jsonPath("$.responseReceivedOn").isEqualTo("2022-03-04")
      }
    }
  }
}
