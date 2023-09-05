package uk.gov.justice.digital.hmpps.approvedpremisesapi.factory

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.CharacteristicNames
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TemporaryAccommodationBedSearchResult
import uk.gov.justice.digital.hmpps.approvedpremisesapi.repository.TemporaryAccommodationBedSearchResultOverlap
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomInt
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringMultiCaseWithNumbers
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringUpperCase
import java.util.UUID

class TemporaryAccommodationBedSearchResultFactory : Factory<TemporaryAccommodationBedSearchResult> {
  private var premisesId: Yielded<UUID> = { UUID.randomUUID() }
  private var premisesName: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }
  private var premisesAddressLine1: Yielded<String> = { randomStringUpperCase(12) }
  private var premisesAddressLine2: Yielded<String?> = { null }
  private var premisesTown: Yielded<String?> = { null }
  private var premisesPostcode: Yielded<String> = { randomStringUpperCase(6) }
  private var premisesCharacteristics: Yielded<MutableList<CharacteristicNames>> = { mutableListOf() }
  private var roomId: Yielded<UUID> = { UUID.randomUUID() }
  private var roomName: Yielded<String> = { randomStringUpperCase(6) }
  private var bedId: Yielded<UUID> = { UUID.randomUUID() }
  private var bedName: Yielded<String> = { randomStringUpperCase(6) }
  private var roomCharacteristics: Yielded<MutableList<CharacteristicNames>> = { mutableListOf() }
  private var premisesBedCount: Yielded<Int> = { randomInt(1, 10) }
  private var overlaps: Yielded<MutableList<TemporaryAccommodationBedSearchResultOverlap>> = { mutableListOf() }

  fun withPremisesId(premisesId: UUID) = apply {
    this.premisesId = { premisesId }
  }

  fun withPremisesName(premisesName: String) = apply {
    this.premisesName = { premisesName }
  }

  fun withPremisesAddressLine1(premisesAddressLine1: String) = apply {
    this.premisesAddressLine1 = { premisesAddressLine1 }
  }

  fun withPremisesAddressLine2(premisesAddressLine2: String?) = apply {
    this.premisesAddressLine2 = { premisesAddressLine2 }
  }

  fun withPremisesTown(premisesTown: String?) = apply {
    this.premisesTown = { premisesTown }
  }

  fun withPremisesPostcode(premisesPostcode: String) = apply {
    this.premisesPostcode = { premisesPostcode }
  }

  fun withPremisesCharacteristics(premisesCharacteristics: MutableList<CharacteristicNames>) = apply {
    this.premisesCharacteristics = { premisesCharacteristics }
  }

  fun withRoomId(roomId: UUID) = apply {
    this.roomId = { roomId }
  }

  fun withRoomName(roomName: String) = apply {
    this.roomName = { roomName }
  }

  fun withBedId(bedId: UUID) = apply {
    this.bedId = { bedId }
  }

  fun withBedName(bedName: String) = apply {
    this.bedName = { bedName }
  }

  fun withRoomCharacteristics(roomCharacteristics: MutableList<CharacteristicNames>) = apply {
    this.roomCharacteristics = { roomCharacteristics }
  }

  fun withPremisesBedCount(premisesBedCount: Int) = apply {
    this.premisesBedCount = { premisesBedCount }
  }

  fun withOverlaps(overlaps: MutableList<TemporaryAccommodationBedSearchResultOverlap>) = apply {
    this.overlaps = { overlaps }
  }

  override fun produce() = TemporaryAccommodationBedSearchResult(
    premisesId = this.premisesId(),
    premisesName = this.premisesName(),
    premisesAddressLine1 = this.premisesAddressLine1(),
    premisesAddressLine2 = this.premisesAddressLine2(),
    premisesTown = this.premisesTown(),
    premisesPostcode = this.premisesPostcode(),
    premisesCharacteristics = this.premisesCharacteristics(),
    roomId = this.roomId(),
    roomName = this.roomName(),
    bedId = this.bedId(),
    bedName = this.bedName(),
    roomCharacteristics = this.roomCharacteristics(),
    premisesBedCount = this.premisesBedCount(),
    overlaps = this.overlaps(),
  )
}
