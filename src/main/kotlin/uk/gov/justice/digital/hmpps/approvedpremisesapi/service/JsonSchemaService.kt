package uk.gov.justice.digital.hmpps.approvedpremisesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersionDetector
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaRepository
import java.util.Collections.synchronizedMap
import java.util.UUID

@Service
class JsonSchemaService(
  private val objectMapper: ObjectMapper,
  private val jsonSchemaRepository: JsonSchemaRepository,
  private val applicationRepository: ApplicationRepository
) {
  private val schemas = synchronizedMap<UUID, JsonSchema>(mutableMapOf())

  fun validate(schema: JsonSchemaEntity, json: String): Boolean {
    val schemaJsonNode = objectMapper.readTree(schema.schema)
    val jsonNode = objectMapper.readTree(json)

    if (!schemas.containsKey(schema.id)) {
      schemas[schema.id] = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(schemaJsonNode))
        .getSchema(schemaJsonNode)
    }

    val validationErrors = schemas[schema.id]!!.validate(jsonNode)

    return validationErrors.isEmpty()
  }

  fun checkSchemaOutdated(application: ApplicationEntity): ApplicationEntity {
    val newestSchema = getNewestSchema(ApprovedPremisesApplicationJsonSchemaEntity::class.java)

    return application.apply { application.schemaUpToDate = application.schemaVersion.id == newestSchema.id }
  }

  fun <T : JsonSchemaEntity> getNewestSchema(type: Class<T>): JsonSchemaEntity = jsonSchemaRepository.getSchemasForType(type).maxBy { it.addedAt }
}
