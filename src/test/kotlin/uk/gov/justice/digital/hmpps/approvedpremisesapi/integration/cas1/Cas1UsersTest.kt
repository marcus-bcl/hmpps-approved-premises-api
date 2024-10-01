package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.cas1

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApArea
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesUser
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ApprovedPremisesUserRole
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ProbationRegion
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UserRolesAndQualifications
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.StaffDetailFactory.probationArea
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.StaffDetailFactory.team
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.InitialiseDatabasePerClassTestBase
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given a Probation Region`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.givens.`Given a User`
import uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.httpmocks.ApDeliusContext_addStaffDetailResponse
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserPermission
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualification
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRole
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.PersonName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.StaffDetail
import java.util.UUID
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.UserQualification as APIUserQualification

class Cas1UsersTest : InitialiseDatabasePerClassTestBase() {
  val id: UUID = UUID.fromString("aff9a4dc-e208-4e4b-abe6-99aff7f6af8a")

  @Nested
  inner class GetUser {

    @Test
    fun `Getting a user without a JWT returns 401`() {
      webTestClient.get()
        .uri("/cas1/users/$id")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `Getting a user with a non-Delius JWT returns 403`() {
      val jwt = jwtAuthHelper.createClientCredentialsJwt(
        username = "username",
        authSource = "other-auth-source",
      )

      webTestClient.get()
        .uri("/cas1/users/$id")
        .header("Authorization", "Bearer $jwt")
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `Getting a user with a Nomis JWT returns 403`() {
      val jwt = jwtAuthHelper.createClientCredentialsJwt(
        username = "username",
        authSource = "nomis",
      )

      webTestClient.get()
        .uri("/cas1/users/$id")
        .header("Authorization", "Bearer $jwt")
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `Getting a user with the POM role returns 403`() {
      val jwt = jwtAuthHelper.createClientCredentialsJwt(
        username = "username",
        authSource = "nomis",
        roles = listOf("ROLE_POM"),
      )

      webTestClient.get()
        .uri("/cas1/users/$id")
        .header("Authorization", "Bearer $jwt")
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `Getting an Approved Premises user returns OK with correct body`() {
      val deliusUsername = "JimJimmerson"
      val forename = "Jim"
      val surname = "Jimmerson"
      val name = "$forename $surname"
      val email = "foo@bar.com"
      val telephoneNumber = "123445677"

      val jwt = jwtAuthHelper.createAuthorizationCodeJwt(
        subject = deliusUsername,
        authSource = "delius",
        roles = listOf("ROLE_PROBATION"),
      )

      val region = `Given a Probation Region`()

      userEntityFactory.produceAndPersist {
        withId(id)
        withDeliusUsername(deliusUsername)
        withName(name)
        withEmail(email)
        withTelephoneNumber(telephoneNumber)
        withYieldedProbationRegion { region }
      }

      ApDeliusContext_addStaffDetailResponse(
        StaffDetail(
          email = email,
          telephoneNumber = telephoneNumber,
          staffIdentifier = 5678L,
          teams = listOf(team()),
          probationArea = probationArea(),
          username = deliusUsername,
          name = PersonName(forename, surname, "C"),
          code = "STAFF1",
          active = true,
        ),
      )

      mockClientCredentialsJwtRequest("username", listOf("ROLE_COMMUNITY"), authSource = "delius")

      val result = webTestClient.get()
        .uri("/cas1/users/$id")
        .header("Authorization", "Bearer $jwt")
        .exchange()
        .expectStatus()
        .isOk
        .returnResult(ApprovedPremisesUser::class.java)
        .responseBody
        .blockFirst()!!

      assertThat(result.id).isEqualTo(id)
      assertThat(result.region).isEqualTo(ProbationRegion(region.id, region.name))
      assertThat(result.deliusUsername).isEqualTo(deliusUsername)
      assertThat(result.name).isEqualTo(name)
      assertThat(result.email).isEqualTo(email)
      assertThat(result.telephoneNumber).isEqualTo(telephoneNumber)
      assertThat(result.roles).isEqualTo(emptyList<ApprovedPremisesUserRole>())
      assertThat(result.qualifications).isEqualTo(emptyList<UserQualification>())
      assertThat(result.service).isEqualTo("CAS1")
      assertThat(result.isActive).isEqualTo(true)
      assertThat(result.apArea).isEqualTo(ApArea(region.apArea!!.id, region.apArea!!.identifier, region.apArea!!.name))
      assertThat(result.permissions).isEqualTo(emptyList<UserPermission>())
      assertThat(result.version).isNotZero()
    }
  }

  @Nested
  inner class UpdateUser {
    @ParameterizedTest
    @EnumSource(value = UserRole::class, names = ["CAS1_ADMIN", "CAS1_WORKFLOW_MANAGER", "CAS1_JANITOR", "CAS1_USER_MANAGER"], mode = EnumSource.Mode.EXCLUDE)
    fun `Updating a user without an approved role is forbidden`(role: UserRole) {
      `Given a User`(roles = listOf(role)) { _, jwt ->
        val id = UUID.randomUUID()
        webTestClient.put()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .header("Content-Type", "application/json")
          .bodyValue(
            UserRolesAndQualifications(
              listOf(),
              listOf(),
            ),
          )
          .exchange()
          .expectStatus()
          .isForbidden
      }
    }

    @Test
    fun `Updating a users with no internal role (aka the Applicant pseudo-role) is forbidden`() {
      `Given a User` { _, jwt ->
        val id = UUID.randomUUID()
        webTestClient.put()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            UserRolesAndQualifications(
              listOf(),
              listOf(),
            ),
          )
          .exchange()
          .expectStatus()
          .isForbidden
      }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole::class, names = ["CAS1_ADMIN", "CAS1_WORKFLOW_MANAGER", "CAS1_JANITOR", "CAS1_USER_MANAGER"])
    fun `Updating a user returns OK with correct body when user has an approved role`(role: UserRole) {
      val qualifications = listOf(APIUserQualification.emergency, APIUserQualification.pipe)
      val roles = listOf(
        ApprovedPremisesUserRole.assessor,
        ApprovedPremisesUserRole.reportViewer,
        ApprovedPremisesUserRole.excludedFromAssessAllocation,
        ApprovedPremisesUserRole.excludedFromMatchAllocation,
        ApprovedPremisesUserRole.excludedFromPlacementApplicationAllocation,
      )

      val id = `Given a User`().first.id

      `Given a User`(roles = listOf(role)) { _, jwt ->
        webTestClient.put()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .bodyValue(
            UserRolesAndQualifications(
              roles = roles,
              qualifications = qualifications,
            ),
          )
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath(".qualifications").isArray
          .jsonPath(".qualifications[0]").isEqualTo("emergency")
          .jsonPath(".roles").isArray
          .jsonPath(".roles[0]").isEqualTo(ApprovedPremisesUserRole.assessor.value)
          .jsonPath(".roles[1]").isEqualTo(ApprovedPremisesUserRole.reportViewer.value)
          .jsonPath(".roles[2]").isEqualTo(ApprovedPremisesUserRole.excludedFromAssessAllocation.value)
          .jsonPath(".roles[3]").isEqualTo(ApprovedPremisesUserRole.excludedFromMatchAllocation.value)
          .jsonPath(".roles[4]").isEqualTo(ApprovedPremisesUserRole.excludedFromPlacementApplicationAllocation.value)
          .jsonPath(".isActive").isEqualTo(true)
      }
    }
  }

  @Nested
  inner class DeleteUser {
    @ParameterizedTest
    @EnumSource(value = UserRole::class, names = ["CAS1_ADMIN", "CAS1_WORKFLOW_MANAGER", "CAS1_JANITOR", "CAS1_USER_MANAGER"], mode = EnumSource.Mode.EXCLUDE)
    fun `Deleting a user with an unapproved role is forbidden`(role: UserRole) {
      `Given a User`(roles = listOf(role)) { _, jwt ->
        val id = UUID.randomUUID()
        webTestClient.delete()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus()
          .isForbidden
      }
    }

    @Test
    fun `Deleting a user with no internal role (aka the Applicant pseudo-role) is forbidden`() {
      `Given a User`() { _, jwt ->
        val id = UUID.randomUUID()
        webTestClient.delete()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isForbidden
      }
    }

    @Test
    fun `Deleting a user with a non-Delius JWT returns 403`() {
      val jwt = jwtAuthHelper.createClientCredentialsJwt(
        username = "username",
        authSource = "nomis",
      )

      webTestClient.delete()
        .uri("/cas1/users/$id")
        .header("Authorization", "Bearer $jwt")
        .header("X-Service-Name", ServiceName.approvedPremises.value)
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @ParameterizedTest
    @EnumSource(value = UserRole::class, names = ["CAS1_ADMIN", "CAS1_WORKFLOW_MANAGER", "CAS1_JANITOR", "CAS1_USER_MANAGER"])
    fun `Deleting a user with an approved role deletes successfully`(role: UserRole) {
      userEntityFactory.produceAndPersist {
        withId(id)
        withYieldedProbationRegion { `Given a Probation Region`() }
      }

      `Given a User`(roles = listOf(role)) { _, jwt ->
        webTestClient.delete()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isOk

        val userFromDatabase = userRepository.findByIdOrNull(id)
        assertThat(userFromDatabase?.isActive).isEqualTo(false)
      }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole::class, names = ["CAS1_ADMIN", "CAS1_WORKFLOW_MANAGER", "CAS1_JANITOR", "CAS1_USER_MANAGER"], mode = EnumSource.Mode.EXCLUDE)
    fun `Deleting a user without an approved role is forbidden `(role: UserRole) {
      `Given a User`(roles = listOf(role)) { _, jwt ->
        webTestClient.delete()
          .uri("/cas1/users/$id")
          .header("Authorization", "Bearer $jwt")
          .exchange()
          .expectStatus()
          .isForbidden
      }
    }
  }
}