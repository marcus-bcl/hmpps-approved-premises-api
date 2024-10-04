package uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext

import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.events.model.StaffMember

data class StaffDetail(
  val email: String?,
  val telephoneNumber: String?,
  val staffIdentifier: Long,
  val teams: List<Team> = emptyList(),
  val probationArea: ProbationArea,
  val username: String,
  val name: PersonName,
  val code: String,
  val active: Boolean,
) {
  fun activeTeamsNewestFirst() = teams
    .filter { it.endDate == null }
    .sortedByDescending { it.startDate }

  fun teamCodes() = teams.map { it.code }

  fun toStaffMember() = StaffMember(
    staffCode = this.code,
    staffIdentifier = this.staffIdentifier,
    forenames = this.name.forenames(),
    surname = this.name.surname,
    username = this.username,
  )
}

data class PersonName(
  val forename: String,
  val surname: String,
  val middleName: String? = null,
) {
  fun deliusName() = "$forename $surname"
  fun forenames() = "$forename ${middleName?.takeIf { it.isNotEmpty() } ?: ""}".trim()
}

data class ProbationArea(
  val code: String,
  val description: String,
)
