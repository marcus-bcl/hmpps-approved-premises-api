package uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.events

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.ApplicationWithdrawn
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.ApplicationWithdrawnWithdrawnBy
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.PersonReference
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.ProbationArea
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.StaffMember
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomDateTimeBefore
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringMultiCaseWithNumbers
import java.time.Instant
import java.util.UUID

class ApplicationWithdrawnFactory : Factory<ApplicationWithdrawn> {
  private var applicationId: Yielded<UUID> = { UUID.randomUUID() }
  private var applicationUrl: Yielded<String> = { randomStringMultiCaseWithNumbers(10) }
  private var personReference: Yielded<PersonReference> = { PersonReferenceFactory().produce() }
  private var deliusEventNumber: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }
  private var submittedAt: Yielded<Instant> = { Instant.now().randomDateTimeBefore(7) }
  private var withdrawnByStaffMember: Yielded<StaffMember> = { StaffMemberFactory().produce() }
  private var withdrawnByProbationArea: Yielded<ProbationArea> = { ProbationAreaFactory().produce() }
  private var withdrawnAt: Yielded<Instant> = { Instant.now().randomDateTimeBefore(5) }
  private var withdrawalReason: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }

  fun withApplicationId(applicationId: UUID) = apply {
    this.applicationId = { applicationId }
  }

  fun withApplicationUrl(applicationUrl: String) = apply {
    this.applicationUrl = { applicationUrl }
  }

  fun withPersonReference(personReference: PersonReference) = apply {
    this.personReference = { personReference }
  }

  fun withDeliusEventNumber(deliusEventNumber: String) = apply {
    this.deliusEventNumber = { deliusEventNumber }
  }

  fun withSubmittedAt(submittedAt: Instant) = apply {
    this.submittedAt = { submittedAt }
  }

  fun withWithdrawnByStaffMember(staffMember: StaffMember) = apply {
    this.withdrawnByStaffMember = { staffMember }
  }

  fun withWithdrawnByProbationArea(probationArea: ProbationArea) = apply {
    this.withdrawnByProbationArea = { probationArea }
  }

  fun withWithdrawnAt(withdrawnAt: Instant) = apply {
    this.withdrawnAt = { withdrawnAt }
  }

  fun withWithdrawalReason(withdrawalReason: String) = apply {
    this.withdrawalReason = { withdrawalReason }
  }

  override fun produce() = ApplicationWithdrawn(
    applicationId = this.applicationId(),
    applicationUrl = this.applicationUrl(),
    personReference = this.personReference(),
    deliusEventNumber = this.deliusEventNumber(),
    withdrawnBy = ApplicationWithdrawnWithdrawnBy(
      staffMember = this.withdrawnByStaffMember(),
      probationArea = this.withdrawnByProbationArea(),
    ),
    withdrawnAt = this.withdrawnAt(),
    withdrawalReason = this.withdrawalReason(),
  )
}