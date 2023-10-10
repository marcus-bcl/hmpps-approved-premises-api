package uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext

import java.time.LocalDate
import java.time.LocalDateTime

data class CaseDetail(
  val case: CaseSummary,
  val offences: List<Offence>,
  val registrations: List<Registration>,
  val mappaDetail: MappaDetail?,
)

data class CaseSummary(
  val crn: String,
  val nomsId: String?,
  val name: Name,
  val dateOfBirth: LocalDate,
  val gender: String?,
  val profile: Profile,
  val manager: Manager,
  val currentExclusion: Boolean?,
  val currentRestriction: Boolean?,
)

data class Name(
  val forename: String,
  val surname: String,
  val middleNames: List<String>,
)

data class Manager(
  val team: Team,
)

data class Team(
  val code: String,
  val name: String,
  val ldu: Ldu,
)

data class Ldu(
  val code: String,
  val name: String,
)

data class Profile(
  val ethnicity: String?,
  val genderIdentity: String?,
  val nationality: String?,
  val religion: String?,
)

data class Offence(
  val description: String,
  val date: LocalDate,
  val main: Boolean,
  val eventNumber: String,
)

data class Registration(
  val description: String,
  val startDate: LocalDate,
)

data class MappaDetail(
  val level: Int,
  val levelDescription: String,
  val category: Int,
  val categoryDescription: String,
  val startDate: LocalDate,
  val lastUpdated: LocalDateTime,
)