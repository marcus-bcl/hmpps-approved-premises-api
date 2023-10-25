package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens

import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.NomisUserDetailFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.StaffUserDetailsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.CommunityAPI_mockSuccessfulStaffUserDetailsCall
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ExternalUserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.NomisUserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ProbationRegionEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualification
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRole
import java.util.UUID

fun IntegrationTestBase.`Given a User`(
  id: UUID = UUID.randomUUID(),
  staffUserDetailsConfigBlock: (StaffUserDetailsFactory.() -> Unit)? = null,
  roles: List<UserRole> = emptyList(),
  qualifications: List<UserQualification> = emptyList(),
  probationRegion: ProbationRegionEntity? = null,
  isActive: Boolean = true,
): Pair<UserEntity, String> {
  val staffUserDetailsFactory = StaffUserDetailsFactory()

  if (staffUserDetailsConfigBlock != null) {
    staffUserDetailsConfigBlock(staffUserDetailsFactory)
  }

  val staffUserDetails = staffUserDetailsFactory.produce()

  val user = userEntityFactory.produceAndPersist {
    withId(id)
    withDeliusUsername(staffUserDetails.username)
    withEmail(staffUserDetails.email)
    withTelephoneNumber(staffUserDetails.telephoneNumber)
    withName("${staffUserDetails.staff.forenames} ${staffUserDetails.staff.surname}")
    withIsActive(isActive)
    if (probationRegion == null) {
      withYieldedProbationRegion {
        probationRegionEntityFactory.produceAndPersist {
          withYieldedApArea { apAreaEntityFactory.produceAndPersist() }
        }
      }
    } else {
      withProbationRegion(probationRegion)
    }
  }

  roles.forEach { role ->
    user.roles += userRoleAssignmentEntityFactory.produceAndPersist {
      withUser(user)
      withRole(role)
    }
  }

  qualifications.forEach { qualification ->
    user.qualifications += userQualificationAssignmentEntityFactory.produceAndPersist {
      withUser(user)
      withQualification(qualification)
    }
  }

  val jwt = jwtAuthHelper.createValidAuthorizationCodeJwt(staffUserDetails.username)

  CommunityAPI_mockSuccessfulStaffUserDetailsCall(
    staffUserDetails,
  )

  return Pair(user, jwt)
}

fun IntegrationTestBase.`Given a User`(
  id: UUID = UUID.randomUUID(),
  staffUserDetailsConfigBlock: (StaffUserDetailsFactory.() -> Unit)? = null,
  roles: List<UserRole> = emptyList(),
  qualifications: List<UserQualification> = emptyList(),
  probationRegion: ProbationRegionEntity? = null,
  isActive: Boolean = true,
  block: (userEntity: UserEntity, jwt: String) -> Unit,
) {
  val (user, jwt) = `Given a User`(
    id,
    staffUserDetailsConfigBlock,
    roles,
    qualifications,
    probationRegion,
    isActive,
  )

  return block(user, jwt)
}

fun IntegrationTestBase.`Given a CAS2 User`(
  id: UUID = UUID.randomUUID(),
  nomisUserDetailsConfigBlock: (NomisUserDetailFactory.() -> Unit)? = null,
  block: (nomisUserEntity: NomisUserEntity, jwt: String) -> Unit,
) {
  val nomisUserDetailsFactory = NomisUserDetailFactory()

  if (nomisUserDetailsConfigBlock != null) {
    nomisUserDetailsConfigBlock(nomisUserDetailsFactory)
  }

  val nomisUserDetails = nomisUserDetailsFactory.produce()

  val user = nomisUserEntityFactory.produceAndPersist {
    withId(id)
    withNomisUsername(nomisUserDetails.username)
    withEmail(nomisUserDetails.primaryEmail)
    withName("${nomisUserDetails.firstName} ${nomisUserDetails.lastName}")
  }

  val jwt = jwtAuthHelper.createValidNomisAuthorisationCodeJwt(nomisUserDetails.username)

  block(user, jwt)
}

fun IntegrationTestBase.`Given a CAS2 Assessor`(
  id: UUID = UUID.randomUUID(),
  block: (externalUserEntity: ExternalUserEntity, jwt: String) -> Unit,
) {
  val user = externalUserEntityFactory.produceAndPersist {
    withId(id)
    withUsername("CAS2_ASSESSOR_USER")
  }

  val jwt = jwtAuthHelper.createValidExternalAuthorisationCodeJwt("CAS2_ASSESSOR_USER")

  block(user, jwt)
}
