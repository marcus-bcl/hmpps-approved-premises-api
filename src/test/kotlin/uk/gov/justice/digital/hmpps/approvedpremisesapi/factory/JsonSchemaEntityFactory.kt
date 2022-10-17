package uk.gov.justice.digital.hmpps.approvedpremisesapi.factory

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.JsonSchemaType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomDateTimeBefore
import java.time.OffsetDateTime
import java.util.UUID

class JsonSchemaEntityFactory : Factory<JsonSchemaEntity> {
  private var id: Yielded<UUID> = { UUID.randomUUID() }
  private var addedAt: Yielded<OffsetDateTime> = { OffsetDateTime.now().randomDateTimeBefore(7) }
  private var schema: Yielded<String> = { "{}" }
  private var type: Yielded<JsonSchemaType> = { JsonSchemaType.APPLICATION }

  fun withId(id: UUID) = apply {
    this.id = { id }
  }

  fun withAddedAt(addedAt: OffsetDateTime) = apply {
    this.addedAt = { addedAt }
  }

  fun withSchema(schema: String) = apply {
    this.schema = { schema }
  }

  fun withType(type: JsonSchemaType) = apply {
    this.type = { type }
  }

  override fun produce(): JsonSchemaEntity = JsonSchemaEntity(
    id = this.id(),
    addedAt = this.addedAt(),
    schema = this.schema(),
    type = this.type()
  )
}
