package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.CommunityApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.client.PrisonsApiClient
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApAreaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApplicationTeamCodeEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesApplicationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesApplicationJsonSchemaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesAssessmentJsonSchemaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesPlacementApplicationJsonSchemaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ArrivalEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.AssessmentClarificationNoteEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.AssessmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BedEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BookingEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BookingNotMadeEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CancellationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CancellationReasonEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.CharacteristicEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ConfirmationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.DepartureEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.DepartureReasonEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.DestinationProviderEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.DomainEventEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ExtensionEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LocalAuthorityEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LostBedCancellationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LostBedReasonEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LostBedsEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.MoveOnCategoryEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.NonArrivalEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.NonArrivalReasonEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.OfflineApplicationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PersistedFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PlacementApplicationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PlacementRequestEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PlacementRequirementsEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.PostCodeDistrictEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ProbationDeliveryUnitEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ProbationRegionEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.RoomEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TemporaryAccommodationApplicationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TemporaryAccommodationApplicationJsonSchemaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TemporaryAccommodationPremisesEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TurnaroundEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserQualificationAssignmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.UserRoleAssignmentEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApAreaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApplicationTeamCodeEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesApplicationJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesAssessmentJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ApprovedPremisesPlacementApplicationJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ArrivalEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentClarificationNoteEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.AssessmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BedEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BedRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingNotMadeEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CancellationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CancellationReasonEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CharacteristicEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.CharacteristicRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ConfirmationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DepartureEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DepartureReasonEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DestinationProviderEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.DomainEventEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ExtensionEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LocalAuthorityAreaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedCancellationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedReasonEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedsEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.MoveOnCategoryEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.NonArrivalEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.NonArrivalReasonEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.OfflineApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementApplicationRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequestEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequirementsEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PlacementRequirementsRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.PostCodeDistrictEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ProbationDeliveryUnitEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.ProbationRegionEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.RoomEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.RoomRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationApplicationEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationApplicationJsonSchemaEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TemporaryAccommodationPremisesEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.TurnaroundEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserQualificationAssignmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRoleAssignmentEntity
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.OffenderDetailSummary
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.StaffUserDetails
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.community.UserOffenderAccess
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.StaffMember
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.deliuscontext.StaffMembersPage
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.hmppsauth.GetTokenResponse
import uk.gov.justice.digital.hmpps.approvedpremisesapi.model.prisonsapi.InmateDetail
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApAreaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApplicationTeamCodeTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApprovedPremisesApplicationJsonSchemaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApprovedPremisesApplicationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApprovedPremisesAssessmentJsonSchemaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApprovedPremisesPlacementApplicationJsonSchemaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ApprovedPremisesTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ArrivalTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.AssessmentClarificationNoteTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.AssessmentTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.BookingNotMadeTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.BookingTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.CancellationReasonTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.CancellationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ConfirmationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.DepartureReasonTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.DepartureTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.DestinationProviderTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.DomainEventTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ExtensionTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.LocalAuthorityAreaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.LostBedCancellationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.LostBedReasonTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.LostBedsTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.MoveOnCategoryTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.NonArrivalReasonTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.NonArrivalTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.OfflineApplicationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.PostCodeDistrictTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ProbationDeliveryUnitTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.ProbationRegionTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TemporaryAccommodationApplicationJsonSchemaTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TemporaryAccommodationApplicationTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TemporaryAccommodationPremisesTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TurnaroundTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.UserQualificationAssignmentTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.UserRoleAssignmentTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.UserTestRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.DbExtension
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.JwtAuthHelper
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.TestPropertiesInitializer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.TimeZone
import java.util.UUID

object WiremockPortHolder {
  private val possiblePorts = 57830..57880

  private var port: Int? = null
  private var channel: FileChannel? = null

  fun getPort(): Int {
    synchronized(this) {
      if (port != null) {
        return port!!
      }

      possiblePorts.forEach { portToTry ->
        val lockFilePath = Paths.get("${System.getProperty("java.io.tmpdir")}${System.getProperty("file.separator")}ap-int-port-lock-$portToTry.lock")

        try {
          channel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
          channel!!.position(0)

          if (channel!!.tryLock() == null) {
            channel!!.close()
            channel = null
            return@forEach
          }

          port = portToTry

          return portToTry
        } catch (_: Exception) {
        }
      }

      throw RuntimeException("Could not lock any potential Wiremock ports")
    }
  }

  fun releasePort() = channel?.close()
}

@ExtendWith(DbExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(initializers = [TestPropertiesInitializer::class])
@ActiveProfiles("test")
@Tag("integration")
abstract class IntegrationTestBase {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Value("\${wiremock.port}")
  lateinit var wiremockPort: Integer

  @Value("\${preemptive-cache-key-prefix}")
  lateinit var preemptiveCacheKeyPrefix: String

  lateinit var wiremockServer: WireMockServer

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  private lateinit var cacheManager: CacheManager

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Autowired
  lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  lateinit var redisTemplate: RedisTemplate<String, String>

  @Autowired
  private lateinit var communityApiClient: CommunityApiClient

  @Autowired
  private lateinit var prisonsApiClient: PrisonsApiClient

  @Autowired
  lateinit var probationRegionRepository: ProbationRegionTestRepository

  @Autowired
  lateinit var apAreaRepository: ApAreaTestRepository

  @Autowired
  lateinit var localAuthorityAreaRepository: LocalAuthorityAreaTestRepository

  @Autowired
  lateinit var approvedPremisesRepository: ApprovedPremisesTestRepository

  @Autowired
  lateinit var temporaryAccommodationPremisesRepository: TemporaryAccommodationPremisesTestRepository

  @Autowired
  lateinit var bookingRepository: BookingTestRepository

  @Autowired
  lateinit var arrivalRepository: ArrivalTestRepository

  @Autowired
  lateinit var confirmationRepository: ConfirmationTestRepository

  @Autowired
  lateinit var departureRepository: DepartureTestRepository

  @Autowired
  lateinit var destinationProviderRepository: DestinationProviderTestRepository

  @Autowired
  lateinit var nonArrivalRepository: NonArrivalTestRepository

  @Autowired
  lateinit var cancellationRepository: CancellationTestRepository

  @Autowired
  lateinit var departureReasonRepository: DepartureReasonTestRepository

  @Autowired
  lateinit var moveOnCategoryRepository: MoveOnCategoryTestRepository

  @Autowired
  lateinit var cancellationReasonRepository: CancellationReasonTestRepository

  @Autowired
  lateinit var lostBedReasonRepository: LostBedReasonTestRepository

  @Autowired
  lateinit var lostBedsRepository: LostBedsTestRepository

  @Autowired
  lateinit var lostBedCancellationRepository: LostBedCancellationTestRepository

  @Autowired
  lateinit var extensionRepository: ExtensionTestRepository

  @Autowired
  lateinit var nonArrivalReasonRepository: NonArrivalReasonTestRepository

  @Autowired
  lateinit var approvedPremisesApplicationRepository: ApprovedPremisesApplicationTestRepository

  @Autowired
  lateinit var temporaryAccommodationApplicationRepository: TemporaryAccommodationApplicationTestRepository

  @Autowired
  lateinit var offlineApplicationRepository: OfflineApplicationTestRepository

  @Autowired
  lateinit var approvedPremisesApplicationJsonSchemaRepository: ApprovedPremisesApplicationJsonSchemaTestRepository

  @Autowired
  lateinit var temporaryAccommodationApplicationJsonSchemaRepository: TemporaryAccommodationApplicationJsonSchemaTestRepository

  @Autowired
  lateinit var approvedPremisesAssessmentJsonSchemaRepository: ApprovedPremisesAssessmentJsonSchemaTestRepository

  @Autowired
  lateinit var approvedPremisesPlacementApplicationJsonSchemaRepository: ApprovedPremisesPlacementApplicationJsonSchemaTestRepository

  @Autowired
  lateinit var userRepository: UserTestRepository

  @Autowired
  lateinit var userRoleAssignmentRepository: UserRoleAssignmentTestRepository

  @Autowired
  lateinit var userQualificationAssignmentRepository: UserQualificationAssignmentTestRepository

  @Autowired
  lateinit var assessmentRepository: AssessmentTestRepository

  @Autowired
  lateinit var assessmentClarificationNoteRepository: AssessmentClarificationNoteTestRepository

  @Autowired
  lateinit var characteristicRepository: CharacteristicRepository

  @Autowired
  lateinit var roomRepository: RoomRepository

  @Autowired
  lateinit var bedRepository: BedRepository

  @Autowired
  lateinit var domainEventRepository: DomainEventTestRepository

  @Autowired
  lateinit var applicationTeamCodeRepository: ApplicationTeamCodeTestRepository

  @Autowired
  lateinit var postCodeDistrictRepository: PostCodeDistrictTestRepository

  @Autowired
  lateinit var placementRequestRepository: PlacementRequestRepository

  @Autowired
  lateinit var placementRequirementsRepository: PlacementRequirementsRepository

  @Autowired
  lateinit var placementApplicationRepository: PlacementApplicationRepository

  @Autowired
  lateinit var bookingNotMadeRepository: BookingNotMadeTestRepository

  @Autowired
  lateinit var probationDeliveryUnitRepository: ProbationDeliveryUnitTestRepository

  @Autowired
  lateinit var turnaroundRepository: TurnaroundTestRepository

  lateinit var probationRegionEntityFactory: PersistedFactory<ProbationRegionEntity, UUID, ProbationRegionEntityFactory>
  lateinit var apAreaEntityFactory: PersistedFactory<ApAreaEntity, UUID, ApAreaEntityFactory>
  lateinit var localAuthorityEntityFactory: PersistedFactory<LocalAuthorityAreaEntity, UUID, LocalAuthorityEntityFactory>
  lateinit var approvedPremisesEntityFactory: PersistedFactory<ApprovedPremisesEntity, UUID, ApprovedPremisesEntityFactory>
  lateinit var temporaryAccommodationPremisesEntityFactory: PersistedFactory<TemporaryAccommodationPremisesEntity, UUID, TemporaryAccommodationPremisesEntityFactory>
  lateinit var bookingEntityFactory: PersistedFactory<BookingEntity, UUID, BookingEntityFactory>
  lateinit var arrivalEntityFactory: PersistedFactory<ArrivalEntity, UUID, ArrivalEntityFactory>
  lateinit var confirmationEntityFactory: PersistedFactory<ConfirmationEntity, UUID, ConfirmationEntityFactory>
  lateinit var departureEntityFactory: PersistedFactory<DepartureEntity, UUID, DepartureEntityFactory>
  lateinit var destinationProviderEntityFactory: PersistedFactory<DestinationProviderEntity, UUID, DestinationProviderEntityFactory>
  lateinit var departureReasonEntityFactory: PersistedFactory<DepartureReasonEntity, UUID, DepartureReasonEntityFactory>
  lateinit var moveOnCategoryEntityFactory: PersistedFactory<MoveOnCategoryEntity, UUID, MoveOnCategoryEntityFactory>
  lateinit var nonArrivalEntityFactory: PersistedFactory<NonArrivalEntity, UUID, NonArrivalEntityFactory>
  lateinit var cancellationEntityFactory: PersistedFactory<CancellationEntity, UUID, CancellationEntityFactory>
  lateinit var cancellationReasonEntityFactory: PersistedFactory<CancellationReasonEntity, UUID, CancellationReasonEntityFactory>
  lateinit var lostBedsEntityFactory: PersistedFactory<LostBedsEntity, UUID, LostBedsEntityFactory>
  lateinit var lostBedReasonEntityFactory: PersistedFactory<LostBedReasonEntity, UUID, LostBedReasonEntityFactory>
  lateinit var lostBedCancellationEntityFactory: PersistedFactory<LostBedCancellationEntity, UUID, LostBedCancellationEntityFactory>
  lateinit var extensionEntityFactory: PersistedFactory<ExtensionEntity, UUID, ExtensionEntityFactory>
  lateinit var nonArrivalReasonEntityFactory: PersistedFactory<NonArrivalReasonEntity, UUID, NonArrivalReasonEntityFactory>
  lateinit var approvedPremisesApplicationEntityFactory: PersistedFactory<ApprovedPremisesApplicationEntity, UUID, ApprovedPremisesApplicationEntityFactory>
  lateinit var temporaryAccommodationApplicationEntityFactory: PersistedFactory<TemporaryAccommodationApplicationEntity, UUID, TemporaryAccommodationApplicationEntityFactory>
  lateinit var offlineApplicationEntityFactory: PersistedFactory<OfflineApplicationEntity, UUID, OfflineApplicationEntityFactory>
  lateinit var approvedPremisesApplicationJsonSchemaEntityFactory: PersistedFactory<ApprovedPremisesApplicationJsonSchemaEntity, UUID, ApprovedPremisesApplicationJsonSchemaEntityFactory>
  lateinit var temporaryAccommodationApplicationJsonSchemaEntityFactory: PersistedFactory<TemporaryAccommodationApplicationJsonSchemaEntity, UUID, TemporaryAccommodationApplicationJsonSchemaEntityFactory>
  lateinit var approvedPremisesPlacementApplicationJsonSchemaEntityFactory: PersistedFactory<ApprovedPremisesPlacementApplicationJsonSchemaEntity, UUID, ApprovedPremisesPlacementApplicationJsonSchemaEntityFactory>
  lateinit var approvedPremisesAssessmentJsonSchemaEntityFactory: PersistedFactory<ApprovedPremisesAssessmentJsonSchemaEntity, UUID, ApprovedPremisesAssessmentJsonSchemaEntityFactory>
  lateinit var userEntityFactory: PersistedFactory<UserEntity, UUID, UserEntityFactory>
  lateinit var userRoleAssignmentEntityFactory: PersistedFactory<UserRoleAssignmentEntity, UUID, UserRoleAssignmentEntityFactory>
  lateinit var userQualificationAssignmentEntityFactory: PersistedFactory<UserQualificationAssignmentEntity, UUID, UserQualificationAssignmentEntityFactory>
  lateinit var assessmentEntityFactory: PersistedFactory<AssessmentEntity, UUID, AssessmentEntityFactory>
  lateinit var assessmentClarificationNoteEntityFactory: PersistedFactory<AssessmentClarificationNoteEntity, UUID, AssessmentClarificationNoteEntityFactory>
  lateinit var characteristicEntityFactory: PersistedFactory<CharacteristicEntity, UUID, CharacteristicEntityFactory>
  lateinit var roomEntityFactory: PersistedFactory<RoomEntity, UUID, RoomEntityFactory>
  lateinit var bedEntityFactory: PersistedFactory<BedEntity, UUID, BedEntityFactory>
  lateinit var domainEventFactory: PersistedFactory<DomainEventEntity, UUID, DomainEventEntityFactory>
  lateinit var postCodeDistrictFactory: PersistedFactory<PostCodeDistrictEntity, UUID, PostCodeDistrictEntityFactory>
  lateinit var placementRequestFactory: PersistedFactory<PlacementRequestEntity, UUID, PlacementRequestEntityFactory>
  lateinit var placementRequirementsFactory: PersistedFactory<PlacementRequirementsEntity, UUID, PlacementRequirementsEntityFactory>
  lateinit var bookingNotMadeFactory: PersistedFactory<BookingNotMadeEntity, UUID, BookingNotMadeEntityFactory>
  lateinit var probationDeliveryUnitFactory: PersistedFactory<ProbationDeliveryUnitEntity, UUID, ProbationDeliveryUnitEntityFactory>
  lateinit var applicationTeamCodeFactory: PersistedFactory<ApplicationTeamCodeEntity, UUID, ApplicationTeamCodeEntityFactory>
  lateinit var turnaroundFactory: PersistedFactory<TurnaroundEntity, UUID, TurnaroundEntityFactory>
  lateinit var placementApplicationFactory: PersistedFactory<PlacementApplicationEntity, UUID, PlacementApplicationEntityFactory>

  private var clientCredentialsCallMocked = false

  @BeforeEach
  fun beforeEach() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    webTestClient = webTestClient.mutate()
      .responseTimeout(Duration.ofMinutes(20))
      .build()

    wiremockServer = WireMockServer(wiremockPort.toInt())
    wiremockServer.start()

    cacheManager.cacheNames.forEach {
      cacheManager.getCache(it)!!.clear()
    }

    redisTemplate.keys("$preemptiveCacheKeyPrefix-**").forEach(redisTemplate::delete)
  }

  @AfterEach
  fun stopMockServer() {
    wiremockServer.stop()
  }

  @BeforeEach
  fun setupFactories() {
    probationRegionEntityFactory = PersistedFactory({ ProbationRegionEntityFactory() }, probationRegionRepository)
    apAreaEntityFactory = PersistedFactory({ ApAreaEntityFactory() }, apAreaRepository)
    localAuthorityEntityFactory = PersistedFactory({ LocalAuthorityEntityFactory() }, localAuthorityAreaRepository)
    approvedPremisesEntityFactory = PersistedFactory({ ApprovedPremisesEntityFactory() }, approvedPremisesRepository)
    temporaryAccommodationPremisesEntityFactory = PersistedFactory({ TemporaryAccommodationPremisesEntityFactory() }, temporaryAccommodationPremisesRepository)
    bookingEntityFactory = PersistedFactory({ BookingEntityFactory() }, bookingRepository)
    arrivalEntityFactory = PersistedFactory({ ArrivalEntityFactory() }, arrivalRepository)
    confirmationEntityFactory = PersistedFactory({ ConfirmationEntityFactory() }, confirmationRepository)
    departureEntityFactory = PersistedFactory({ DepartureEntityFactory() }, departureRepository)
    destinationProviderEntityFactory = PersistedFactory({ DestinationProviderEntityFactory() }, destinationProviderRepository)
    departureReasonEntityFactory = PersistedFactory({ DepartureReasonEntityFactory() }, departureReasonRepository)
    moveOnCategoryEntityFactory = PersistedFactory({ MoveOnCategoryEntityFactory() }, moveOnCategoryRepository)
    nonArrivalEntityFactory = PersistedFactory({ NonArrivalEntityFactory() }, nonArrivalRepository)
    cancellationEntityFactory = PersistedFactory({ CancellationEntityFactory() }, cancellationRepository)
    cancellationReasonEntityFactory = PersistedFactory({ CancellationReasonEntityFactory() }, cancellationReasonRepository)
    lostBedsEntityFactory = PersistedFactory({ LostBedsEntityFactory() }, lostBedsRepository)
    lostBedReasonEntityFactory = PersistedFactory({ LostBedReasonEntityFactory() }, lostBedReasonRepository)
    lostBedCancellationEntityFactory = PersistedFactory({ LostBedCancellationEntityFactory() }, lostBedCancellationRepository)
    extensionEntityFactory = PersistedFactory({ ExtensionEntityFactory() }, extensionRepository)
    nonArrivalReasonEntityFactory = PersistedFactory({ NonArrivalReasonEntityFactory() }, nonArrivalReasonRepository)
    approvedPremisesApplicationEntityFactory = PersistedFactory({ ApprovedPremisesApplicationEntityFactory() }, approvedPremisesApplicationRepository)
    temporaryAccommodationApplicationEntityFactory = PersistedFactory({ TemporaryAccommodationApplicationEntityFactory() }, temporaryAccommodationApplicationRepository)
    offlineApplicationEntityFactory = PersistedFactory({ OfflineApplicationEntityFactory() }, offlineApplicationRepository)
    approvedPremisesApplicationJsonSchemaEntityFactory = PersistedFactory({ ApprovedPremisesApplicationJsonSchemaEntityFactory() }, approvedPremisesApplicationJsonSchemaRepository)
    temporaryAccommodationApplicationJsonSchemaEntityFactory = PersistedFactory({ TemporaryAccommodationApplicationJsonSchemaEntityFactory() }, temporaryAccommodationApplicationJsonSchemaRepository)
    approvedPremisesAssessmentJsonSchemaEntityFactory = PersistedFactory({ ApprovedPremisesAssessmentJsonSchemaEntityFactory() }, approvedPremisesAssessmentJsonSchemaRepository)
    approvedPremisesPlacementApplicationJsonSchemaEntityFactory = PersistedFactory({ ApprovedPremisesPlacementApplicationJsonSchemaEntityFactory() }, approvedPremisesPlacementApplicationJsonSchemaRepository)
    userEntityFactory = PersistedFactory({ UserEntityFactory() }, userRepository)
    userRoleAssignmentEntityFactory = PersistedFactory({ UserRoleAssignmentEntityFactory() }, userRoleAssignmentRepository)
    userQualificationAssignmentEntityFactory = PersistedFactory({ UserQualificationAssignmentEntityFactory() }, userQualificationAssignmentRepository)
    assessmentEntityFactory = PersistedFactory({ AssessmentEntityFactory() }, assessmentRepository)
    assessmentClarificationNoteEntityFactory = PersistedFactory({ AssessmentClarificationNoteEntityFactory() }, assessmentClarificationNoteRepository)
    characteristicEntityFactory = PersistedFactory({ CharacteristicEntityFactory() }, characteristicRepository)
    roomEntityFactory = PersistedFactory({ RoomEntityFactory() }, roomRepository)
    bedEntityFactory = PersistedFactory({ BedEntityFactory() }, bedRepository)
    domainEventFactory = PersistedFactory({ DomainEventEntityFactory() }, domainEventRepository)
    postCodeDistrictFactory = PersistedFactory({ PostCodeDistrictEntityFactory() }, postCodeDistrictRepository)
    placementRequestFactory = PersistedFactory({ PlacementRequestEntityFactory() }, placementRequestRepository)
    placementRequirementsFactory = PersistedFactory({ PlacementRequirementsEntityFactory() }, placementRequirementsRepository)
    bookingNotMadeFactory = PersistedFactory({ BookingNotMadeEntityFactory() }, bookingNotMadeRepository)
    probationDeliveryUnitFactory = PersistedFactory({ ProbationDeliveryUnitEntityFactory() }, probationDeliveryUnitRepository)
    applicationTeamCodeFactory = PersistedFactory({ ApplicationTeamCodeEntityFactory() }, applicationTeamCodeRepository)
    turnaroundFactory = PersistedFactory({ TurnaroundEntityFactory() }, turnaroundRepository)
    placementApplicationFactory = PersistedFactory({ PlacementApplicationEntityFactory() }, placementApplicationRepository)
  }

  fun mockClientCredentialsJwtRequest(
    username: String? = null,
    roles: List<String> = listOf(),
    authSource: String = "none",
  ) {
    wiremockServer.stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              objectMapper.writeValueAsString(
                GetTokenResponse(
                  accessToken = jwtAuthHelper.createClientCredentialsJwt(
                    username = username,
                    roles = roles,
                    authSource = authSource,
                  ),
                  tokenType = "bearer",
                  expiresIn = Duration.ofHours(1).toSeconds().toInt(),
                  scope = "read",
                  sub = username?.uppercase() ?: "integration-test-client-id",
                  authSource = authSource,
                  jti = UUID.randomUUID().toString(),
                  iss = "http://localhost:9092/auth/issuer",
                ),
              ),
            ),
        ),
    )
  }

  fun mockOffenderDetailsCommunityApiCall(offenderDetails: OffenderDetailSummary) = wiremockServer.stubFor(
    WireMock.get(urlEqualTo("/secure/offenders/crn/${offenderDetails.otherIds.crn}"))
      .willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
            objectMapper.writeValueAsString(offenderDetails),
          ),
      ),
  )

  fun mockOffenderUserAccessCommunityApiCall(username: String, crn: String, inclusion: Boolean, exclusion: Boolean) {
    if (!inclusion && !exclusion) {
      wiremockServer.stubFor(
        WireMock.get(urlEqualTo("/secure/offenders/crn/$crn/user/$username/userAccess"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(200)
              .withBody(
                objectMapper.writeValueAsString(
                  UserOffenderAccess(
                    userRestricted = false,
                    userExcluded = false,
                    restrictionMessage = null,
                  ),
                ),
              ),
          ),
      )
      return
    }

    wiremockServer.stubFor(
      WireMock.get(urlEqualTo("/secure/offenders/crn/$crn/user/$username/userAccess"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(403)
            .withBody(
              objectMapper.writeValueAsString(
                UserOffenderAccess(
                  userRestricted = inclusion,
                  userExcluded = exclusion,
                  restrictionMessage = null,
                ),
              ),
            ),
        ),
    )
  }

  fun mockStaffMembersContextApiCall(staffMember: StaffMember, qCode: String) = wiremockServer.stubFor(
    WireMock.get(urlEqualTo("/approved-premises/$qCode/staff"))
      .willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
            objectMapper.writeValueAsString(
              StaffMembersPage(
                content = listOf(staffMember),
              ),
            ),
          ),
      ),
  )

  fun mockInmateDetailPrisonsApiCall(inmateDetail: InmateDetail) = wiremockServer.stubFor(
    WireMock.get(urlEqualTo("/api/offenders/${inmateDetail.offenderNo}"))
      .willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
            objectMapper.writeValueAsString(inmateDetail),
          ),
      ),
  )

  fun mockStaffUserInfoCommunityApiCall(staffUserDetails: StaffUserDetails, createProbationRegionForStaffAreaCode: Boolean = true): StubMapping? {
    if (createProbationRegionForStaffAreaCode) {
      probationRegionEntityFactory.produceAndPersist {
        withYieldedApArea { apAreaEntityFactory.produceAndPersist() }
        withDeliusCode(staffUserDetails.probationArea.code)
      }
    }

    return wiremockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/secure/staff/username/${staffUserDetails.username}"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              objectMapper.writeValueAsString(staffUserDetails),
            ),
        ),
    )
  }

  fun mockStaffUserInfoCommunityApiCallNotFound(username: String) = wiremockServer.stubFor(
    WireMock.get(urlEqualTo("/secure/staff/username/$username"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(404),
      ),
  )

  fun mockSuccessfulGetCallWithJsonResponse(url: String, responseBody: Any, responseStatus: Int = 200) =
    mockOAuth2ClientCredentialsCallIfRequired {
      wiremockServer.stubFor(
        WireMock.get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(responseStatus)
              .withBody(
                objectMapper.writeValueAsString(responseBody),
              ),
          ),
      )
    }

  fun mockUnsuccessfulGetCall(url: String, responseStatus: Int) =
    mockOAuth2ClientCredentialsCallIfRequired {
      wiremockServer.stubFor(
        WireMock.get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(responseStatus),
          ),
      )
    }

  fun mockOAuth2ClientCredentialsCallIfRequired(block: () -> Unit) {
    if (!clientCredentialsCallMocked) {
      mockClientCredentialsJwtRequest()

      clientCredentialsCallMocked = true
    }

    block()
  }

  fun loadPreemptiveCacheForOffenderDetails(crn: String) = communityApiClient.getOffenderDetailSummaryWithCall(crn)
  fun loadPreemptiveCacheForInmateDetails(nomsNumber: String) = prisonsApiClient.getInmateDetailsWithCall(nomsNumber)
}
