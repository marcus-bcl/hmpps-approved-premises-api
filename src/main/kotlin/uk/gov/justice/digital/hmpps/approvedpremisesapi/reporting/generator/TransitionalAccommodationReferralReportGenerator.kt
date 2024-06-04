package uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.generator

import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentDecision
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.PersonSummaryInfoResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.CaseSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.model.TransitionalAccommodationReferralReportDataAndPersonInfo
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.model.TransitionalAccommodationReferralReportRow
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.TransitionalAccommodationReferralReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.util.toYesNo

class TransitionalAccommodationReferralReportGenerator : ReportGenerator<
  TransitionalAccommodationReferralReportDataAndPersonInfo,
  TransitionalAccommodationReferralReportRow, TransitionalAccommodationReferralReportProperties,
  >(
  TransitionalAccommodationReferralReportRow::class,
) {

  override val convert: TransitionalAccommodationReferralReportDataAndPersonInfo.(properties: TransitionalAccommodationReferralReportProperties) -> List<TransitionalAccommodationReferralReportRow> =
    {
      val referralData = this.referralReportData
      val personInfo = this.personInfoResult

      listOf(
        TransitionalAccommodationReferralReportRow(
          referralId = referralData.referralId,
          referralDate = referralData.referralCreatedDate,
          personName = personInfo.tryGetDetails {
            val nameParts = listOf(it.name.forename) + it.name.middleNames + it.name.surname
            nameParts.joinToString(" ")
          },
          pncNumber = personInfo.tryGetDetails { it.pnc },
          crn = referralData.crn,
          sex = personInfo.tryGetDetails { it.gender },
          genderIdentity = when (personInfo.tryGetDetails { it.profile?.genderIdentity }) {
            "Prefer to self-describe" -> personInfo.tryGetDetails { it.profile?.selfDescribedGender }
            else -> personInfo.tryGetDetails { it.profile?.genderIdentity }
          }.toString(),
          ethnicity = personInfo.tryGetDetails { it.profile?.ethnicity },
          dateOfBirth = personInfo.tryGetDetails { it.dateOfBirth },
          riskOfSeriousHarm = referralData.riskOfSeriousHarm,
          registeredSexOffender = referralData.registeredSexOffender.toYesNo(),
          historyOfSexualOffence = referralData.historyOfSexualOffence.toYesNo(),
          concerningSexualBehaviour = referralData.concerningSexualBehaviour.toYesNo(),
          needForAccessibleProperty = referralData.needForAccessibleProperty.toYesNo(),
          historyOfArsonOffence = referralData.historyOfArsonOffence.toYesNo(),
          concerningArsonBehaviour = referralData.concerningArsonBehaviour.toYesNo(),
          dutyToReferMade = referralData.dutyToReferMade.toYesNo(),
          dateDutyToReferMade = referralData.dateDutyToReferMade,
          dutyToReferLocalAuthorityAreaName = referralData.dutyToReferLocalAuthorityAreaName,
          dutyToReferOutcome = referralData.dutyToReferOutcome,
          town = referralData.town,
          postCode = referralData.postCode,
          probationRegion = referralData.probationRegionName,
          pdu = referralData.pdu,
          referralSubmittedDate = referralData.referralSubmittedDate,
          referralRejected = isReferralRejected(referralData.referralRejectionReason).toYesNo(),
          rejectionReason = referralData.referralRejectionReason,
          rejectionDate = if (AssessmentDecision.REJECTED.name == referralData.assessmentDecision) referralData.assessmentSubmittedDate else null,
          sourceOfReferral = referralData.referralEligibilityReason,
          prisonReleaseType = referralData.prisonReleaseTypes,
          prisonAtReferral = referralData.prisonNameOnCreation,
          releaseDate = referralData.personReleaseDate,
          accommodationRequiredDate = referralData.accommodationRequiredDate?.toLocalDateTime()?.toLocalDate(),
          bookingOffered = (referralData.bookingId != null).toYesNo(),
        ),
      )
    }

  override fun filter(properties: TransitionalAccommodationReferralReportProperties): (TransitionalAccommodationReferralReportDataAndPersonInfo) -> Boolean =
    {
      true
    }

  private fun <V> PersonSummaryInfoResult.tryGetDetails(value: (CaseSummary) -> V): V? {
    return when (this) {
      is PersonSummaryInfoResult.Success.Full -> value(this.summary)
      else -> null
    }
  }

  private fun isReferralRejected(referralRejectionReason: String?): Boolean {
    return when (referralRejectionReason) {
      "They have no recourse to public funds (NRPF)", "They’re not eligible (not because of NRPF)" -> true
      else -> false
    }
  }
}
