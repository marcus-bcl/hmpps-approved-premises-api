package uk.gov.justice.digital.hmpps.approvedpremisesapi.transformer.cas2v2

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.FullPerson
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PersonStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PersonType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.CaseSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.probationoffendersearchapi.ProbationOffenderDetail

@Component
class Cas2v2PersonTransformer {

  fun transformProbationOffenderDetailAndInmateDetailToFullPerson(
    probationOffenderDetail: ProbationOffenderDetail,
  ): FullPerson = FullPerson(
    name = "${probationOffenderDetail.firstName} ${probationOffenderDetail.surname}",
    dateOfBirth = probationOffenderDetail.dateOfBirth!!, // see https://github.com/ministryofjustice/hmpps-approved-premises-api/blob/48fffe10a0284a6165eb6096cce9cbd632ecc135/src/main/kotlin/uk/gov/justice/digital/hmpps/approvedpremisesapi/transformer/PersonTransformer.kt#L157
    sex = probationOffenderDetail.gender ?: "Unknown",
    status = PersonStatus.unknown,
    crn = probationOffenderDetail.otherIds.crn,
    type = PersonType.fullPerson,
    nomsNumber = probationOffenderDetail.otherIds.nomsNumber,
  )

  fun transformCaseSummaryToFullPerson(caseSummary: CaseSummary): FullPerson = FullPerson(
    name = "${caseSummary.name.forename} ${caseSummary.name.surname}",
    dateOfBirth = caseSummary.dateOfBirth,
    sex = caseSummary.gender ?: "Unknown",
    status = PersonStatus.unknown,
    crn = caseSummary.crn,
    type = PersonType.fullPerson,
    nomsNumber = caseSummary.nomsId,
  )
}
