package uk.gov.justice.digital.hmpps.approvedpremisesapi.service.cas2

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersionDetector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.Cas2ApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.cas2v2.Cas2v2ApplicationEntity
import java.util.Collections.synchronizedMap
import java.util.UUID

@SuppressWarnings("UnusedPrivateProperty")
@Service
class Cas2JsonSchemaService(
  private val objectMapper: ObjectMapper,
  private val jsonSchemaRepository: JsonSchemaRepository,
  private val applicationRepository: Cas2ApplicationRepository,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  private val schemas = synchronizedMap<UUID, JsonSchema>(mutableMapOf())

  fun validate(schema: JsonSchemaEntity, json: String): Boolean {
    val schemaJsonNode = objectMapper.readTree(schema.schema)
    val jsonNode = objectMapper.readTree(json)

    if (!schemas.containsKey(schema.id)) {
      schemas[schema.id] = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(schemaJsonNode))
        .getSchema(schemaJsonNode)
    }

    val validationErrors = schemas[schema.id]!!.validate(jsonNode)

    if (validationErrors.isNotEmpty()) {
      log.warn("Validation errors whilst validating schema: \n\t${validationErrors.joinToString("\n\t,") { "Schema Path: ${it.schemaPath} -> Path: ${it.path}: ${it.message}" }}")
    }

    return validationErrors.isEmpty()
  }

  fun checkSchemaOutdated(application: Cas2ApplicationEntity): Cas2ApplicationEntity {
    val newestSchema = getNewestSchema(application.schemaVersion.javaClass)

    return application.apply { application.schemaUpToDate = application.schemaVersion.id == newestSchema.id }
  }

  fun checkCas2v2SchemaOutdated(application: Cas2v2ApplicationEntity): Cas2v2ApplicationEntity {
    val newestSchema = getNewestSchema(application.schemaVersion.javaClass)

    return application.apply { application.schemaUpToDate = application.schemaVersion.id == newestSchema.id }
  }

  fun <T : JsonSchemaEntity> getNewestSchema(type: Class<T>): JsonSchemaEntity = jsonSchemaRepository.getSchemasForType(type).maxBy { it.addedAt }
}
