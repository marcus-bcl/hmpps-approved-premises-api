package uk.gov.justice.digital.hmpps.approvedpremisesapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.BedEntity
import java.time.LocalDate
import java.util.UUID

interface BedUtilisationReportRepository : JpaRepository<BedEntity, UUID> {
  @Query(
    """
    SELECT
        CAST(b.id AS VARCHAR) AS bedId,
        CAST(r.id AS VARCHAR) AS roomId,
        CAST(p.id AS VARCHAR) AS premisesId,
        pr.name AS probationRegionName,
        pdu.name AS probationDeliveryUnitName,
        laa.name AS localAuthorityName,
        p.name AS premisesName,
        p.address_line1 AS addressLine1,
        p.town AS town,
        p.postcode AS postCode,
        r.name AS roomName,
        b.created_at AS bedspaceStartDate,
        b.end_date AS bedspaceEndDate
    FROM beds b
    INNER JOIN rooms r ON b.room_id = r.id
    LEFT JOIN premises p ON r.premises_id = p.id
    LEFT JOIN probation_regions pr ON p.probation_region_id = pr.id
    INNER JOIN temporary_accommodation_premises tap ON p.id = tap.premises_id
    LEFT JOIN probation_delivery_units pdu ON tap.probation_delivery_unit_id = pdu.id
    LEFT JOIN local_authority_areas laa ON p.local_authority_area_id = laa.id
    WHERE
      p.service = 'temporary-accommodation'
      AND (CAST(:probationRegionId AS UUID) IS NULL OR p.probation_region_id = :probationRegionId)
    ORDER BY b.name      
    """,
    nativeQuery = true,
  )
  fun findAllBedspaces(
    probationRegionId: UUID?,
  ): List<BedUtilisationBedspaceReportData>

  @Query(
    """
    SELECT
      booking.arrival_date AS arrivalDate,
      booking.departure_date AS departureDate,
      CAST(bed.id AS VARCHAR) AS bedId,
      CAST(cancellation.id AS VARCHAR) AS cancellationId,
      CAST(arrival.id AS VARCHAR) AS arrivalId,
      CAST(confirmation.id AS VARCHAR) AS confirmationId,
      CAST(turnaround.Id AS VARCHAR) AS turnaroundId,
      turnaround.working_day_count AS workingDayCount
    From bookings booking
    LEFT JOIN cancellations cancellation ON cancellation.booking_id = booking.id
    LEFT JOIN beds bed ON bed.id = booking.bed_id
    LEFT JOIN premises premises ON booking.premises_id = premises.id
    LEFT JOIN probation_regions probation_region ON probation_region.id = premises.probation_region_id
    LEFT JOIN arrivals arrival ON booking.id = arrival.booking_id
    LEFT JOIN confirmations confirmation ON booking.id = confirmation.booking_id
    LEFT JOIN turnarounds turnaround ON booking.id = turnaround.booking_id   
    WHERE
        premises.service = 'temporary-accommodation'
      AND (CAST(:probationRegionId AS UUID) IS NULL OR premises.probation_region_id = :probationRegionId)
      AND booking.arrival_date <= :endDate AND booking.departure_date >= :startDate
    ORDER BY booking.id
    """,
    nativeQuery = true,
  )
  fun findAllBookingsByOverlappingDate(probationRegionId: UUID?, startDate: LocalDate, endDate: LocalDate): List<BedUtilisationBookingReportData>

  @Query(
    """
    SELECT
        CAST(b.id AS VARCHAR) AS bedId,
        lb.start_date AS startDate,
        lb.end_date AS endDate,
        CAST(lbc.id AS VARCHAR) AS cancellationId
    From lost_beds lb
    LEFT JOIN beds b ON lb.bed_id = b.id
    LEFT JOIN rooms r ON b.room_id = r.id
    LEFT JOIN premises p ON r.premises_id = p.id
    LEFT JOIN lost_bed_cancellations lbc ON lb.id = lbc.lost_bed_id
    WHERE
        p.service = 'temporary-accommodation'
      AND (CAST(:probationRegionId AS UUID) IS NULL OR p.probation_region_id = :probationRegionId)
      AND lb.start_date <= :endDate AND lb.end_date >= :startDate
      AND lbc.id is NULL
    ORDER BY lb.id     
    """,
    nativeQuery = true,
  )
  fun findAllLostBedByOverlappingDate(probationRegionId: UUID?, startDate: LocalDate, endDate: LocalDate): List<BedUtilisationLostBedReportData>
}
interface BedUtilisationBedspaceReportData {
  val bedId: String
  val probationRegionName: String?
  val probationDeliveryUnitName: String?
  val localAuthorityName: String?
  val premisesName: String
  val addressLine1: String
  val town: String?
  val postCode: String
  val roomName: String
  val bedspaceStartDate: LocalDate?
  val bedspaceEndDate: LocalDate?
  val premisesId: String
  val roomId: String
}

interface BedUtilisationBookingReportData {
  val arrivalDate: LocalDate
  val departureDate: LocalDate
  val bedId: String
  val cancellationId: String?
  val arrivalId: String?
  val confirmationId: String?
  val turnaroundId: String?
  val workingDayCount: Int?
}

interface BedUtilisationLostBedReportData {
  val bedId: String
  val startDate: LocalDate
  val endDate: LocalDate
  val cancellationId: String?
}