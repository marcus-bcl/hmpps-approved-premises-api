package uk.gov.justice.digital.hmpps.approvedpremisesapi.factory

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ExternalUserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringUpperCase
import java.util.UUID

class ExternalUserEntityFactory : Factory<ExternalUserEntity> {
  private var id: Yielded<UUID> = { UUID.randomUUID() }
  private var username: Yielded<String> = { randomStringUpperCase(12) }
  private var isEnabled: Yielded<Boolean> = { true }
  private var origin: Yielded<String> = { "NACRO" }

  fun withId(id: UUID) = apply {
    this.id = { id }
  }

  fun withUsername(username: String) = apply {
    this.username = { username }
  }

  override fun produce(): ExternalUserEntity = ExternalUserEntity(
    id = this.id(),
    username = this.username(),
    isEnabled = this.isEnabled(),
    origin = this.origin(),
  )
}