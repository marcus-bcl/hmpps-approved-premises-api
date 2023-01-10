package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.ClientResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.CommunityApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.StaffUserDetailsFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualificationAssignmentRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRoleAssignmentRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.results.AuthorisableActionResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.HttpAuthService
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserService
import java.util.UUID

class UserServiceTest {
  private val mockHttpAuthService = mockk<HttpAuthService>()
  private val mockCommunityApiClient = mockk<CommunityApiClient>()
  private val mockUserRepository = mockk<UserRepository>()
  private val mockUserRoleAssignmentRepository = mockk<UserRoleAssignmentRepository>()
  private val mockUserQualificationAssignmentRepository = mockk<UserQualificationAssignmentRepository>()

  private val userService = UserService(
    mockHttpAuthService,
    mockCommunityApiClient,
    mockUserRepository,
    mockUserRoleAssignmentRepository,
    mockUserQualificationAssignmentRepository
  )

  @Test
  fun `getUserForRequest returns existing User when exists, does not call Community API or save`() {
    val username = "SOMEPERSON"
    val mockPrincipal = mockk<AuthAwareAuthenticationToken>()

    every { mockHttpAuthService.getDeliusPrincipalOrThrow() } returns mockPrincipal
    every { mockPrincipal.name } returns username

    val user = UserEntityFactory().produce()

    every { mockUserRepository.findByDeliusUsername(username) } returns user

    assertThat(userService.getUserForRequest()).isEqualTo(user)

    verify(exactly = 0) { mockCommunityApiClient.getStaffUserDetails(username) }
    verify(exactly = 0) { mockUserRepository.save(any()) }
  }

  @Test
  fun `getUserForRequest returns new User when one does not already exist, does call Community API and save`() {
    val username = "SOMEPERSON"
    val mockPrincipal = mockk<AuthAwareAuthenticationToken>()

    every { mockHttpAuthService.getDeliusPrincipalOrThrow() } returns mockPrincipal
    every { mockPrincipal.name } returns username

    every { mockUserRepository.findByDeliusUsername(username) } returns null
    every { mockUserRepository.save(any()) } answers { it.invocation.args[0] as UserEntity }

    every { mockCommunityApiClient.getStaffUserDetails(username) } returns ClientResult.Success(
      HttpStatus.OK,
      StaffUserDetailsFactory()
        .withUsername(username)
        .withForenames("Jim")
        .withSurname("Jimmerson")
        .withStaffIdentifier(5678)
        .produce()
    )

    assertThat(userService.getUserForRequest()).matches {
      it.name == "Jim Jimmerson"
    }

    verify(exactly = 1) { mockCommunityApiClient.getStaffUserDetails(username) }
    verify(exactly = 1) { mockUserRepository.save(any()) }
  }

  @Nested
  class GetUserForId {
    private val mockHttpAuthService = mockk<HttpAuthService>()
    private val mockCommunityApiClient = mockk<CommunityApiClient>()
    private val mockUserRepository = mockk<UserRepository>()
    private val mockUserRoleAssignmentRepository = mockk<UserRoleAssignmentRepository>()
    private val mockUserQualificationAssignmentRepository = mockk<UserQualificationAssignmentRepository>()

    private val userService = UserService(
      mockHttpAuthService,
      mockCommunityApiClient,
      mockUserRepository,
      mockUserRoleAssignmentRepository,
      mockUserQualificationAssignmentRepository
    )

    private val id = UUID.fromString("21b61d19-3a96-4b88-8df9-a5e89bc6fe73")
    private val username = "SOMEPERSON"
    private val forename = "Jim"
    private val surname = "Jimmerson"
    private val staffIdentifier = 5678

    private val userFactory = UserEntityFactory()
      .withDeliusUsername(username)
      .withName("$forename $surname")
      .withDeliusStaffIdentifier(staffIdentifier.toLong())

    private val staffUserDetailsFactory = StaffUserDetailsFactory()
      .withUsername(username)
      .withForenames(forename)
      .withSurname(surname)
      .withStaffIdentifier(staffIdentifier.toLong())

    @BeforeEach
    fun setup() {
      every { mockUserRepository.save(any()) } answers { it.invocation.args[0] as UserEntity }
    }

    @Test
    fun `it returns the user's details from the Community API and saves the email address`() {
      val user = userFactory.produce()
      val deliusUser = staffUserDetailsFactory
        .withEmail("foo@example.com")
        .withTelephoneNumber("0123456789")
        .produce()

      every { mockUserRepository.findByIdOrNull(id) } returns user
      every { mockCommunityApiClient.getStaffUserDetails(username) } returns ClientResult.Success(
        HttpStatus.OK,
        deliusUser
      )

      val result = userService.getUserForId(id)

      assertThat(result).isInstanceOf(AuthorisableActionResult.Success::class.java)
      result as AuthorisableActionResult.Success

      var entity = result.entity

      assertThat(entity.id).isEqualTo(user.id)
      assertThat(entity.name).isEqualTo(user.name)
      assertThat(entity.deliusUsername).isEqualTo(user.deliusUsername)
      assertThat(entity.email).isEqualTo(deliusUser.email)
      assertThat(entity.telephoneNumber).isEqualTo(deliusUser.telephoneNumber)

      verify(exactly = 1) { mockCommunityApiClient.getStaffUserDetails(username) }
      verify(exactly = 1) { mockUserRepository.save(any()) }
    }

    @Test
    fun `it does not save the object if the email and telephone number are the same as Delius`() {
      val email = "foo@example.com"
      val telephoneNumber = "0123456789"

      val user = userFactory
        .withEmail(email)
        .withTelephoneNumber(telephoneNumber)
        .produce()

      val deliusUser = staffUserDetailsFactory
        .withEmail(email)
        .withTelephoneNumber(telephoneNumber)
        .produce()

      every { mockUserRepository.findByIdOrNull(id) } returns user
      every { mockCommunityApiClient.getStaffUserDetails(username) } returns ClientResult.Success(
        HttpStatus.OK,
        deliusUser
      )

      val result = userService.getUserForId(id)

      assertThat(result).isInstanceOf(AuthorisableActionResult.Success::class.java)
      result as AuthorisableActionResult.Success

      var entity = result.entity

      assertThat(entity.id).isEqualTo(user.id)
      assertThat(entity.name).isEqualTo(user.name)

      verify(exactly = 0) { mockUserRepository.save(any()) }
    }

    @Test
    fun `it returns not found when there is no user for that ID`() {
      every { mockUserRepository.findByIdOrNull(id) } returns null

      val result = userService.getUserForId(id)

      assertThat(result).isInstanceOf(AuthorisableActionResult.NotFound::class.java)
    }
  }
}
