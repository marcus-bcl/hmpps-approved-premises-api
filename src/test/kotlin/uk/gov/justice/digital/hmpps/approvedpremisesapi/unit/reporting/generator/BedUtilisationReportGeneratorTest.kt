package uk.gov.justice.digital.hmpps.approvedpremisesapi.unit.reporting.generator

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlinx.dataframe.api.count
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.ServiceName
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApAreaEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ApprovedPremisesEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ArrivalEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BedEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.BookingEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ConfirmationEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LocalAuthorityEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LostBedReasonEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.LostBedsEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.ProbationRegionEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.RoomEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TemporaryAccommodationPremisesEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.factory.TurnaroundEntityFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BookingRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.LostBedsRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.generator.BedUtilisationReportGenerator
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.model.BedUtilisationReportRow
import uk.gov.justice.digital.hmpps.approvedpremisesapi.reporting.properties.BedUtilisationReportProperties
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.WorkingDayCountService
import java.time.LocalDate

class BedUtilisationReportGeneratorTest {
  private val mockBookingRepository = mockk<BookingRepository>()
  private val mockLostBedsRepository = mockk<LostBedsRepository>()
  private val mockWorkingDayCountService = mockk<WorkingDayCountService>()

  private val bedUtilisationReportGenerator = BedUtilisationReportGenerator(
    mockBookingRepository,
    mockLostBedsRepository,
    mockWorkingDayCountService,
  )

  @Test
  fun `Only results for beds from the specified service are returned in the report`() {
    val temporaryAccommodationPremises = TemporaryAccommodationPremisesEntityFactory()
      .withUnitTestControlTestProbationAreaAndLocalAuthority()
      .produce()

    val temporaryAccommodationRoom = RoomEntityFactory()
      .withPremises(temporaryAccommodationPremises)
      .produce()

    val temporaryAccommodationBed = BedEntityFactory()
      .withRoom(temporaryAccommodationRoom)
      .produce()

    val temporaryAccommodationLostBed = LostBedsEntityFactory()
      .withBed(temporaryAccommodationBed)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(temporaryAccommodationPremises)
      .produce()

    val approvedPremises = ApprovedPremisesEntityFactory()
      .withUnitTestControlTestProbationAreaAndLocalAuthority()
      .produce()

    val approvedPremisesRoom = RoomEntityFactory()
      .withPremises(approvedPremises)
      .produce()

    val approvedPremisesBed = BedEntityFactory()
      .withRoom(approvedPremisesRoom)
      .produce()

    val approvedPremisesLostBed = LostBedsEntityFactory()
      .withBed(approvedPremisesBed)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(approvedPremises)
      .produce()

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBed) } returns emptyList()
    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), approvedPremisesBed) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBed) } returns listOf(temporaryAccommodationLostBed)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), approvedPremisesBed) } returns listOf(approvedPremisesLostBed)

    val result = bedUtilisationReportGenerator.createReport(
      listOf(approvedPremisesBed, temporaryAccommodationBed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::propertyRef]).isEqualTo(temporaryAccommodationPremises.name)
  }

  @Test
  fun `Only results for beds from the specified probation region are returned in the report`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val probationRegion2 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea2 = LocalAuthorityEntityFactory().produce()

    val temporaryAccommodationPremisesInProbationRegion = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val temporaryAccommodationRoomInProbationRegion = RoomEntityFactory()
      .withPremises(temporaryAccommodationPremisesInProbationRegion)
      .produce()

    val temporaryAccommodationBedInProbationRegion = BedEntityFactory()
      .withRoom(temporaryAccommodationRoomInProbationRegion)
      .produce()

    val temporaryAccommodationLostBedInProbationArea = LostBedsEntityFactory()
      .withBed(temporaryAccommodationBedInProbationRegion)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(temporaryAccommodationPremisesInProbationRegion)
      .produce()

    val temporaryAccommodationPremisesOutsideProbationRegion = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea2)
      .withProbationRegion(probationRegion2)
      .produce()

    val temporaryAccommodationRoomOutsideProbationRegion = RoomEntityFactory()
      .withPremises(temporaryAccommodationPremisesOutsideProbationRegion)
      .produce()

    val temporaryAccommodationBedOutsideProbationRegion = BedEntityFactory()
      .withRoom(temporaryAccommodationRoomOutsideProbationRegion)
      .produce()

    val temporaryAccommodationLostBedOutsideProbationArea = LostBedsEntityFactory()
      .withBed(temporaryAccommodationBedOutsideProbationRegion)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(temporaryAccommodationPremisesOutsideProbationRegion)
      .produce()

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedInProbationRegion) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedInProbationRegion) } returns listOf(temporaryAccommodationLostBedInProbationArea)
    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedOutsideProbationRegion) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedOutsideProbationRegion) } returns listOf(temporaryAccommodationLostBedOutsideProbationArea)

    val result = bedUtilisationReportGenerator.createReport(
      listOf(temporaryAccommodationBedInProbationRegion, temporaryAccommodationBedOutsideProbationRegion),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, probationRegion1.id, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::propertyRef]).isEqualTo(temporaryAccommodationPremisesInProbationRegion.name)
  }

  @Test
  fun `Results for beds from all probation regions are returned in the report if no probation region ID is provided`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val probationRegion2 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea2 = LocalAuthorityEntityFactory().produce()

    val temporaryAccommodationPremisesInProbationRegion = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val temporaryAccommodationRoomInProbationRegion = RoomEntityFactory()
      .withPremises(temporaryAccommodationPremisesInProbationRegion)
      .produce()

    val temporaryAccommodationBedInProbationRegion = BedEntityFactory()
      .withRoom(temporaryAccommodationRoomInProbationRegion)
      .produce()

    val temporaryAccommodationLostBedInProbationArea = LostBedsEntityFactory()
      .withBed(temporaryAccommodationBedInProbationRegion)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(temporaryAccommodationPremisesInProbationRegion)
      .produce()

    val temporaryAccommodationPremisesOutsideProbationRegion = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea2)
      .withProbationRegion(probationRegion2)
      .produce()

    val temporaryAccommodationRoomOutsideProbationRegion = RoomEntityFactory()
      .withPremises(temporaryAccommodationPremisesOutsideProbationRegion)
      .produce()

    val temporaryAccommodationBedOutsideProbationRegion = BedEntityFactory()
      .withRoom(temporaryAccommodationRoomOutsideProbationRegion)
      .produce()

    val temporaryAccommodationLostBedOutsideProbationArea = LostBedsEntityFactory()
      .withBed(temporaryAccommodationBedOutsideProbationRegion)
      .withStartDate(LocalDate.parse("2023-04-05"))
      .withEndDate(LocalDate.parse("2023-04-07"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .withPremises(temporaryAccommodationPremisesOutsideProbationRegion)
      .produce()

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedInProbationRegion) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedInProbationRegion) } returns listOf(temporaryAccommodationLostBedInProbationArea)
    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedOutsideProbationRegion) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), temporaryAccommodationBedOutsideProbationRegion) } returns listOf(temporaryAccommodationLostBedOutsideProbationArea)

    val result = bedUtilisationReportGenerator.createReport(
      listOf(temporaryAccommodationBedInProbationRegion, temporaryAccommodationBedOutsideProbationRegion),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(2)
    assertThat(result[0][BedUtilisationReportRow::propertyRef]).isEqualTo(temporaryAccommodationPremisesInProbationRegion.name)
    assertThat(result[1][BedUtilisationReportRow::propertyRef]).isEqualTo(temporaryAccommodationPremisesOutsideProbationRegion.name)
  }

  @Test
  fun `bookedDaysActiveAndClosed shows the total number of days within the month for Bookings that are marked as arrived`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    // An irrelevant Booking - does not have an Arrival set
    BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-05"))
      .withDepartureDate(LocalDate.parse("2023-04-10"))
      .produce()

    val relevantBookingStraddlingStartOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-03-28"))
      .withDepartureDate(LocalDate.parse("2023-04-04"))
      .produce()
      .apply {
        arrival = ArrivalEntityFactory()
          .withBooking(this)
          .produce()
      }

    val relevantBookingStraddlingEndOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-28"))
      .withDepartureDate(LocalDate.parse("2023-05-04"))
      .produce()
      .apply {
        arrival = ArrivalEntityFactory()
          .withBooking(this)
          .produce()
      }

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantBookingStraddlingStartOfMonth, relevantBookingStraddlingEndOfMonth)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns emptyList()

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::bookedDaysActiveAndClosed]).isEqualTo(7)
  }

  @Test
  fun `confirmedDays shows the total number of days within the month for Bookings that are marked as confirmed but not arrived`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    // An irrelevant Booking - does not have a Confirmation set
    BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-05"))
      .withDepartureDate(LocalDate.parse("2023-04-10"))
      .produce()

    val relevantBookingStraddlingStartOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-03-28"))
      .withDepartureDate(LocalDate.parse("2023-04-04"))
      .produce()
      .apply {
        confirmation = ConfirmationEntityFactory()
          .withBooking(this)
          .produce()
      }

    val relevantBookingStraddlingEndOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-28"))
      .withDepartureDate(LocalDate.parse("2023-05-04"))
      .produce()
      .apply {
        confirmation = ConfirmationEntityFactory()
          .withBooking(this)
          .produce()
      }

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantBookingStraddlingStartOfMonth, relevantBookingStraddlingEndOfMonth)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns emptyList()

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::confirmedDays]).isEqualTo(7)
  }

  @Test
  fun `scheduledTurnaroundDays shows the number of working days in the month for the turnaround`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    val relevantBookingStraddlingStartOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-03-28"))
      .withDepartureDate(LocalDate.parse("2023-04-04"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(5)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingStartOfMonth.departureDate,
        relevantBookingStraddlingStartOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-04-10")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-05"), LocalDate.parse("2023-04-10")) } returns 5

    val relevantBookingStraddlingEndOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-25"))
      .withDepartureDate(LocalDate.parse("2023-04-27"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(3)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingEndOfMonth.departureDate,
        relevantBookingStraddlingEndOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-05-01")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-28"), LocalDate.parse("2023-04-30")) } returns 1

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantBookingStraddlingStartOfMonth, relevantBookingStraddlingEndOfMonth)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns emptyList()

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::scheduledTurnaroundDays]).isEqualTo(6)
  }

  @Test
  fun `effectiveTurnaroundDays shows the total number of days (regardless of whether working days) in the month for the turnaround`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    val relevantBookingStraddlingStartOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-03-28"))
      .withDepartureDate(LocalDate.parse("2023-04-04"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(5)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingStartOfMonth.departureDate,
        relevantBookingStraddlingStartOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-04-10")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-05"), LocalDate.parse("2023-04-10")) } returns 4

    val relevantBookingStraddlingEndOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-25"))
      .withDepartureDate(LocalDate.parse("2023-04-27"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(3)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingEndOfMonth.departureDate,
        relevantBookingStraddlingEndOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-05-01")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-28"), LocalDate.parse("2023-04-30")) } returns 1

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantBookingStraddlingStartOfMonth, relevantBookingStraddlingEndOfMonth)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns emptyList()

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::effectiveTurnaroundDays]).isEqualTo(9)
  }

  @Test
  fun `voidDays shows the total number of days in the month for voids`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    val relevantVoidStraddlingStartOfMonth = LostBedsEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withStartDate(LocalDate.parse("2023-03-28"))
      .withEndDate(LocalDate.parse("2023-04-04"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .produce()

    val relevantVoidStraddlingEndOfMonth = LostBedsEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withStartDate(LocalDate.parse("2023-04-25"))
      .withEndDate(LocalDate.parse("2023-05-03"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .produce()

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns emptyList()
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantVoidStraddlingStartOfMonth, relevantVoidStraddlingEndOfMonth)

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    assertThat(result[0][BedUtilisationReportRow::voidDays]).isEqualTo(10)
  }

  @Test
  fun `totalBookedDays shows the combined total days in the month of all non-cancelled bookings, non-cancelled voids and turnarounds - totalDaysInTheMonth, occupancyRate show correctly`() {
    val apArea = ApAreaEntityFactory()
      .produce()

    val probationRegion1 = ProbationRegionEntityFactory()
      .withApArea(apArea)
      .produce()

    val localAuthorityArea1 = LocalAuthorityEntityFactory().produce()

    val premises = TemporaryAccommodationPremisesEntityFactory()
      .withLocalAuthorityArea(localAuthorityArea1)
      .withProbationRegion(probationRegion1)
      .produce()

    val room = RoomEntityFactory()
      .withPremises(premises)
      .produce()

    val bed = BedEntityFactory()
      .withRoom(room)
      .produce()

    val relevantBookingStraddlingStartOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-03-28"))
      .withDepartureDate(LocalDate.parse("2023-04-04"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(5)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingStartOfMonth.departureDate,
        relevantBookingStraddlingStartOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-04-09")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-05"), LocalDate.parse("2023-04-09")) } returns 4

    val relevantBookingStraddlingEndOfMonth = BookingEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withArrivalDate(LocalDate.parse("2023-04-25"))
      .withDepartureDate(LocalDate.parse("2023-04-27"))
      .produce()
      .apply {
        turnarounds += TurnaroundEntityFactory()
          .withBooking(this)
          .withWorkingDayCount(4)
          .produce()
      }

    every {
      mockWorkingDayCountService.addWorkingDays(
        relevantBookingStraddlingEndOfMonth.departureDate,
        relevantBookingStraddlingEndOfMonth.turnaround!!.workingDayCount
      )
    } returns LocalDate.parse("2023-05-01")

    every { mockWorkingDayCountService.getWorkingDaysCount(LocalDate.parse("2023-04-28"), LocalDate.parse("2023-04-30")) } returns 1

    val relevantVoidStraddlingStartOfMonth = LostBedsEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withStartDate(LocalDate.parse("2023-03-28"))
      .withEndDate(LocalDate.parse("2023-04-04"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .produce()

    val relevantVoidStraddlingEndOfMonth = LostBedsEntityFactory()
      .withBed(bed)
      .withPremises(premises)
      .withStartDate(LocalDate.parse("2023-04-25"))
      .withEndDate(LocalDate.parse("2023-05-03"))
      .withYieldedReason { LostBedReasonEntityFactory().produce() }
      .produce()

    every { mockBookingRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantBookingStraddlingStartOfMonth, relevantBookingStraddlingEndOfMonth)
    every { mockLostBedsRepository.findAllByOverlappingDateForBed(LocalDate.parse("2023-04-01"), LocalDate.parse("2023-04-30"), bed) } returns listOf(relevantVoidStraddlingStartOfMonth, relevantVoidStraddlingEndOfMonth)

    val result = bedUtilisationReportGenerator.createReport(
      listOf(bed),
      BedUtilisationReportProperties(ServiceName.temporaryAccommodation, null, 2023, 4)
    )

    assertThat(result.count()).isEqualTo(1)
    // 4 days for relevantBookingStraddlingStartOfMonth
    // 5 days for turnaround of relevantBookingStraddlingStartOfMonth
    // 3 days for relevantBookingStraddlingEndOfMonth
    // 3 days for turnaround of relevantBookingStraddlingEndOfMonth
    // 4 days for relevantVoidStraddlingStartOfMonth
    // 6 days for relevantVoidStraddlingEndOfMonth
    assertThat(result[0][BedUtilisationReportRow::totalBookedDays]).isEqualTo(25)
    assertThat(result[0][BedUtilisationReportRow::totalDaysInTheMonth]).isEqualTo(30)
    assertThat(result[0][BedUtilisationReportRow::occupancyRate]).isEqualTo(25.toDouble() / 30)
  }
}
