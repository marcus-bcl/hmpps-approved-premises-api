package uk.gov.justice.digital.hmpps.approvedpremisesapi.factory

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ProbationRegionEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualificationAssignmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomEmailAddress
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomInt
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomNumberChars
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringUpperCase
import java.util.UUID

class UserEntityFactory : Factory<UserEntity> {
  private var id: Yielded<UUID> = { UUID.randomUUID() }
  private var name: Yielded<String> = { randomStringUpperCase(12) }
  private var email: Yielded<String?> = { randomEmailAddress() }
  private var telephoneNumber: Yielded<String?> = { randomNumberChars(12) }
  private var deliusUsername: Yielded<String> = { randomStringUpperCase(12) }
  private var deliusStaffCode: Yielded<String> = { randomStringUpperCase(6) }
  private var deliusStaffIdentifier: Yielded<Long> = { randomInt(1000, 10000).toLong() }
  private var applications: Yielded<MutableList<ApplicationEntity>> = { mutableListOf() }
  private var qualifications: Yielded<MutableList<UserQualificationAssignmentEntity>> = { mutableListOf() }
  private var probationRegion: Yielded<ProbationRegionEntity>? = null

  fun withId(id: UUID) = apply {
    this.id = { id }
  }

  fun withName(name: String) = apply {
    this.name = { name }
  }

  fun withDeliusUsername(deliusUsername: String) = apply {
    this.deliusUsername = { deliusUsername }
  }

  fun withDeliusStaffCode(deliusStaffCode: String) = apply {
    this.deliusStaffCode = { deliusStaffCode }
  }

  fun withDeliusStaffIdentifier(deliusStaffIdentifier: Long) = apply {
    this.deliusStaffIdentifier = { deliusStaffIdentifier }
  }

  fun withApplications(applications: MutableList<ApplicationEntity>) = apply {
    this.applications = { applications }
  }

  fun withYieldedQualifications(qualifications: Yielded<MutableList<UserQualificationAssignmentEntity>>) = apply {
    this.qualifications = qualifications
  }

  fun withQualifications(qualifications: MutableList<UserQualificationAssignmentEntity>) = apply {
    this.qualifications = { qualifications }
  }

  fun withEmail(email: String?) = apply {
    this.email = { email }
  }

  fun withTelephoneNumber(telephoneNumber: String?) = apply {
    this.telephoneNumber = { telephoneNumber }
  }

  fun withProbationRegion(probationRegion: ProbationRegionEntity) = apply {
    this.probationRegion = { probationRegion }
  }

  fun withYieldedProbationRegion(probationRegion: Yielded<ProbationRegionEntity>) = apply {
    this.probationRegion = probationRegion
  }

  fun withUnitTestControlProbationRegion() = apply {
    this.withProbationRegion(
      ProbationRegionEntityFactory()
        .withDeliusCode("REGION")
        .withApArea(
          ApAreaEntityFactory()
            .withIdentifier("APAREA")
            .produce()
        )
        .produce()
    )
  }

  override fun produce(): UserEntity = UserEntity(
    id = this.id(),
    name = this.name(),
    email = this.email(),
    telephoneNumber = this.telephoneNumber(),
    deliusUsername = this.deliusUsername(),
    deliusStaffCode = this.deliusStaffCode(),
    deliusStaffIdentifier = this.deliusStaffIdentifier(),
    applications = this.applications(),
    roles = mutableListOf(),
    qualifications = this.qualifications(),
    probationRegion = this.probationRegion?.invoke() ?: throw RuntimeException("A probation region must be provided")
  )
}
