package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import io.github.bluegroundltd.kfactory.Factory
import io.github.bluegroundltd.kfactory.Yielded
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.PropertyStatus
import uk.gov.justice.digital.hmpps.approvedpremisesapi.api.model.SeedFileType
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.ApprovedPremisesSeedCsvRow
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.CsvBuilder
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomDouble
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomInt
import uk.gov.justice.digital.hmpps.approvedpremisesapi.util.randomStringMultiCaseWithNumbers
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SeedApprovedPremisesTest : SeedTestBase() {

  @BeforeEach
  fun removeDefaultCharacteristicsFromDatabaseMigrations() {
    characteristicRepository.deleteAll()
  }

  @Test
  fun `Attempting to create an Approved Premises with an invalid Probation Region logs an error`() {
    withCsv(
      "invalid-probation",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          ApprovedPremisesSeedCsvRowFactory()
            .withProbationRegion("Not Real Region")
            .produce(),
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "invalid-probation")

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Error on row 1:" &&
          it.throwable != null &&
          it.throwable.message == "Probation Region Not Real Region does not exist"
      }
  }

  @Test
  fun `Attempting to create an Approved Premises with an invalid Local Authority Area logs an error`() {
    val probationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    withCsv(
      "invalid-local-authority",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          ApprovedPremisesSeedCsvRowFactory()
            .withProbationRegion(probationRegion.name)
            .withLocalAuthorityArea("Not Real Authority")
            .produce(),
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "invalid-local-authority")

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Error on row 1:" &&
          it.throwable != null &&
          it.throwable.message == "Local Authority Area Not Real Authority does not exist"
      }
  }

  @Test
  fun `Attempting to create an Approved Premises with an incorrectly service-scoped characteristic logs an error`() {
    val probationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    val localAuthorityArea = localAuthorityEntityFactory.produceAndPersist()

    characteristicEntityFactory.produceAndPersist {
      withId(UUID.fromString("8e04628f-2cdd-4d9a-8ae7-27689d7daa73"))
      withPropertyName("isCatered")
      withModelScope("premises")
      withServiceScope("temporary-accommodation")
    }

    withCsv(
      "invalid-service-scope",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          ApprovedPremisesSeedCsvRowFactory()
            .withProbationRegion(probationRegion.name)
            .withLocalAuthorityArea(localAuthorityArea.name)
            .withIsCatered("yes")
            .produce(),
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "invalid-service-scope")

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Error on row 1:" &&
          it.throwable != null &&
          it.throwable.message == "Characteristic 'isCatered' does not exist for AP premises"
      }
  }

  @Test
  fun `Attempting to create an Approved Premises with an incorrectly model-scoped characteristic logs an error`() {
    val probationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    val localAuthorityArea = localAuthorityEntityFactory.produceAndPersist()

    val characteristic = characteristicEntityFactory.produceAndPersist {
      withId(UUID.fromString("8e04628f-2cdd-4d9a-8ae7-27689d7daa73"))
      withPropertyName("isCatered")
      withServiceScope("approved-premises")
      withModelScope("room")
    }

    withCsv(
      "invalid-model-scope",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          ApprovedPremisesSeedCsvRowFactory()
            .withProbationRegion(probationRegion.name)
            .withLocalAuthorityArea(localAuthorityArea.name)
            .withIsCatered("yes")
            .produce(),
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "invalid-model-scope")

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Error on row 1:" &&
          it.throwable != null &&
          it.throwable.message == "Characteristic 'isCatered' does not exist for AP premises"
      }
  }

  @Test
  fun `Attempting to create an Approved Premises with an incorrect boolean value logs an error`() {
    val csvRow = ApprovedPremisesSeedCsvRowFactory()
      .withIsCatered("Catered")
      .produce()

    withCsv(
      "new-ap-invalid-boolean",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          csvRow,
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "new-ap-invalid-boolean")

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Unable to complete Seed Job" &&
          it.throwable != null &&
          it.throwable.message!!.contains("'Catered' is not a recognised boolean for 'isCatered' (use yes | no)")
      }
  }

  @Test
  fun `Attempting to create an Approved Premises missing required headers lists missing fields`() {
    withCsv(
      "new-ap-missing-headers",
      "name,apCode,qCode,apArea,pdu,probationRegion,localAuthorityArea,town,addressLine1\n" +
        "HOPE,Q00,North East,Leeds,Yorkshire & The Humber,Leeds,Leeds,1 The Street, Leeds",
    )

    seedService.seedData(SeedFileType.approvedPremises, "new-ap-missing-headers")

    val expectedErrorMessage = "The headers provided: " +
      "[name, apCode, qCode, apArea, pdu, probationRegion, localAuthorityArea, town, addressLine1] " +
      "did not include required headers: " +
      "[addressLine2, postcode, totalBeds, notes, characteristics, isIAP, isPIPE, isESAP, isSemiSpecialistMentalHealth, " +
      "isRecoveryFocussed, isSuitableForVulnerable, acceptsSexOffenders, acceptsChildSexOffenders, " +
      "acceptsNonSexualChildOffenders, acceptsHateCrimeOffenders, isCatered, hasWideStepFreeAccess, " +
      "hasWideAccessToCommunalAreas, hasStepFreeAccessToCommunalAreas, hasWheelChairAccessibleBathrooms, " +
      "hasLift, hasTactileFlooring, hasBrailleSignage, hasHearingLoop, status, latitude, longitude]"

    assertThat(logEntries)
      .withFailMessage("-> logEntries actually contains: $logEntries")
      .anyMatch {
        it.level == "error" &&
          it.message == "Unable to complete Seed Job" &&
          it.throwable != null &&
          it.throwable.message!!.contains(expectedErrorMessage)
      }
  }

  @Test
  fun `Creating a new Approved Premises persists correctly`() {
    val probationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    val localAuthorityArea = localAuthorityEntityFactory.produceAndPersist()

    characteristicEntityFactory.produceAndPersist {
      withServiceScope("approved-premises")
      withModelScope("premises")
      withName("Is this premises catered?")
      withPropertyName("isCatered")
    }

    characteristicEntityFactory.produceAndPersist {
      withServiceScope("approved-premises")
      withModelScope("premises")
      withName("Is this an IAP?")
      withPropertyName("isIAP")
    }

    val csvRow = ApprovedPremisesSeedCsvRowFactory()
      .withProbationRegion(probationRegion.name)
      .withLocalAuthorityArea(localAuthorityArea.name)
      .withIsCatered("yes")
      .withIsIAP("no")
      .withLongitude(-1.1169752)
      .withLatitude(53.9634721)
      .produce()

    withCsv(
      "new-ap",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          csvRow,
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "new-ap")

    val persistedApprovedPremises = approvedPremisesRepository.findByApCode(csvRow.apCode)
    assertThat(persistedApprovedPremises).isNotNull
    assertThat(persistedApprovedPremises!!.apCode).isEqualTo(csvRow.apCode)
    assertThat(persistedApprovedPremises.name).isEqualTo(csvRow.name)
    assertThat(persistedApprovedPremises.addressLine1).isEqualTo(csvRow.addressLine1)
    assertThat(persistedApprovedPremises.addressLine2).isEqualTo(csvRow.addressLine2)
    assertThat(persistedApprovedPremises.town).isEqualTo(csvRow.town)
    assertThat(persistedApprovedPremises.postcode).isEqualTo(csvRow.postcode)
    assertThat(persistedApprovedPremises.latitude).isEqualTo(csvRow.latitude)
    assertThat(persistedApprovedPremises.longitude).isEqualTo(csvRow.longitude)
    assertThat(persistedApprovedPremises.totalBeds).isEqualTo(csvRow.totalBeds)
    assertThat(persistedApprovedPremises.notes).isEqualTo(csvRow.notes)
    assertThat(persistedApprovedPremises.probationRegion.name).isEqualTo(csvRow.probationRegion)
    assertThat(persistedApprovedPremises.localAuthorityArea!!.name).isEqualTo(csvRow.localAuthorityArea)
    assertThat(persistedApprovedPremises.characteristics.map { it.propertyName }).isEqualTo(listOf("isCatered"))
    assertThat(persistedApprovedPremises.status).isEqualTo(csvRow.status)
  }

  @Test
  fun `Updating an existing Approved Premises persists correctly`() {
    val originalProbationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    val updatedProbationRegion = probationRegionEntityFactory.produceAndPersist {
      withApArea(apAreaEntityFactory.produceAndPersist())
    }

    val originalLocalAuthorityArea = localAuthorityEntityFactory.produceAndPersist()

    val updatedLocalAuthorityArea = localAuthorityEntityFactory.produceAndPersist()

    val existingApprovedPremises = approvedPremisesEntityFactory.produceAndPersist {
      withId(UUID.randomUUID())
      withProbationRegion(originalProbationRegion)
      withLocalAuthorityArea(originalLocalAuthorityArea)
      withQCode("OLD Q-CODE")
    }

    val csvRow = ApprovedPremisesSeedCsvRowFactory()
      .withApCode(existingApprovedPremises.apCode)
      .withProbationRegion(updatedProbationRegion.name)
      .withLocalAuthorityArea(updatedLocalAuthorityArea.name)
      .withLatitude(12.5)
      .withLongitude(30.1)
      .produce()

    withCsv(
      "update-ap",
      approvedPremisesSeedCsvRowsToCsv(
        listOf(
          csvRow,
        ),
      ),
    )

    seedService.seedData(SeedFileType.approvedPremises, "update-ap")

    val persistedApprovedPremises = approvedPremisesRepository.findByApCode(csvRow.apCode)
    assertThat(persistedApprovedPremises).isNotNull
    assertThat(persistedApprovedPremises!!.apCode).isEqualTo(csvRow.apCode)
    assertThat(persistedApprovedPremises.name).isEqualTo(csvRow.name)
    assertThat(persistedApprovedPremises.addressLine1).isEqualTo(csvRow.addressLine1)
    assertThat(persistedApprovedPremises.postcode).isEqualTo(csvRow.postcode)
    assertThat(persistedApprovedPremises.totalBeds).isEqualTo(csvRow.totalBeds)
    assertThat(persistedApprovedPremises.notes).isEqualTo(csvRow.notes)
    assertThat(persistedApprovedPremises.probationRegion.name).isEqualTo(csvRow.probationRegion)
    assertThat(persistedApprovedPremises.localAuthorityArea!!.name).isEqualTo(csvRow.localAuthorityArea)
    assertThat(persistedApprovedPremises.characteristics.map { it.name }).isEqualTo(csvRow.characteristics)
    assertThat(persistedApprovedPremises.status).isEqualTo(csvRow.status)
    assertThat(persistedApprovedPremises.latitude).isEqualTo(csvRow.latitude!!)
    assertThat(persistedApprovedPremises.longitude).isEqualTo(csvRow.longitude!!)
    assertThat(persistedApprovedPremises.point).isEqualTo(
      GeometryFactory(PrecisionModel(PrecisionModel.FLOATING), 4326)
        .createPoint(Coordinate(csvRow.latitude!!, csvRow.longitude!!)),
    )
  }

  private fun approvedPremisesSeedCsvRowsToCsv(rows: List<ApprovedPremisesSeedCsvRow>): String {
    val builder = CsvBuilder()
      .withUnquotedFields(
        "name",
        "addressLine1",
        "addressLine2",
        "town",
        "postcode",
        "latitude",
        "longitude",
        "totalBeds",
        "notes",
        "probationRegion",
        "localAuthorityArea",
        "characteristics",
        "isIAP",
        "isPIPE",
        "isESAP",
        "isSemiSpecialistMentalHealth",
        "isRecoveryFocussed",
        "isSuitableForVulnerable",
        "acceptsSexOffenders",
        "acceptsChildSexOffenders",
        "acceptsNonSexualChildOffenders",
        "acceptsHateCrimeOffenders",
        "isCatered",
        "hasWideStepFreeAccess",
        "hasWideAccessToCommunalAreas",
        "hasStepFreeAccessToCommunalAreas",
        "hasWheelChairAccessibleBathrooms",
        "hasLift",
        "hasTactileFlooring",
        "hasBrailleSignage",
        "hasHearingLoop",
        "status",
        "apCode",
        "qCode",
      )
      .newRow()

    rows.forEach {
      builder
        .withQuotedField(it.name)
        .withQuotedField(it.addressLine1)
        .withQuotedField(it.addressLine2!!)
        .withQuotedField(it.town)
        .withQuotedField(it.postcode)
        .withQuotedField(it.latitude!!)
        .withQuotedField(it.longitude!!)
        .withUnquotedField(it.totalBeds)
        .withQuotedFields(it.notes)
        .withQuotedField(it.probationRegion)
        .withQuotedField(it.localAuthorityArea)
        .withQuotedField(it.characteristics.joinToString(","))
        .withQuotedField(it.isIAP)
        .withQuotedField(it.isPIPE)
        .withQuotedField(it.isESAP)
        .withQuotedField(it.isSemiSpecialistMentalHealth)
        .withQuotedField(it.isRecoveryFocussed)
        .withQuotedField(it.isSuitableForVulnerable)
        .withQuotedField(it.acceptsSexOffenders)
        .withQuotedField(it.acceptsChildSexOffenders)
        .withQuotedField(it.acceptsNonSexualChildOffenders)
        .withQuotedField(it.acceptsHateCrimeOffenders)
        .withQuotedField(it.isCatered)
        .withQuotedField(it.hasWideStepFreeAccess)
        .withQuotedField(it.hasWideAccessToCommunalAreas)
        .withQuotedField(it.hasStepFreeAccessToCommunalAreas)
        .withQuotedField(it.hasWheelChairAccessibleBathrooms)
        .withQuotedField(it.hasLift)
        .withQuotedField(it.hasTactileFlooring)
        .withQuotedField(it.hasBrailleSignage)
        .withQuotedField(it.hasHearingLoop)
        .withQuotedField(it.status.value)
        .withQuotedField(it.apCode)
        .withQuotedField(it.qCode)
        .newRow()
    }

    return builder.build()
  }
}

class ApprovedPremisesSeedCsvRowFactory : Factory<ApprovedPremisesSeedCsvRow> {
  private var name: Yielded<String> = { randomStringMultiCaseWithNumbers(10) }
  private var addressLine1: Yielded<String> = { randomStringMultiCaseWithNumbers(10) }
  private var addressLine2: Yielded<String?> = { randomStringMultiCaseWithNumbers(10) }
  private var town: Yielded<String> = { randomStringMultiCaseWithNumbers(10) }
  private var postcode: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }
  private var latitude: Yielded<Double> = { randomDouble(53.50, 54.99) }
  private var longitude: Yielded<Double> = { randomDouble(-1.56, 1.10) }
  private var totalBeds: Yielded<Int> = { randomInt(5, 50) }
  private var notes: Yielded<String> = { randomStringMultiCaseWithNumbers(20) }
  private var probationRegion: Yielded<String> = { randomStringMultiCaseWithNumbers(5) }
  private var localAuthorityArea: Yielded<String> = { randomStringMultiCaseWithNumbers(5) }
  private var characteristics: Yielded<List<String>> = { listOf() }
  private var isIAP: Yielded<String> = { "no" }
  private var isPIPE: Yielded<String> = { "no" }
  private var isESAP: Yielded<String> = { "no" }
  private var isSemiSpecialistMentalHealth: Yielded<String> = { "no" }
  private var isRecoveryFocussed: Yielded<String> = { "no" }
  private var isSuitableForVulnerable: Yielded<String> = { "no" }
  private var acceptsSexOffenders: Yielded<String> = { "no" }
  private var acceptsChildSexOffenders: Yielded<String> = { "no" }
  private var acceptsNonSexualChildOffenders: Yielded<String> = { "no" }
  private var acceptsHateCrimeOffenders: Yielded<String> = { "no" }
  private var isCatered: Yielded<String> = { "no" }
  private var hasWideStepFreeAccess: Yielded<String> = { "no" }
  private var hasWideAccessToCommunalAreas: Yielded<String> = { "no" }
  private var hasStepFreeAccessToCommunalAreas: Yielded<String> = { "no" }
  private var hasWheelChairAccessibleBathrooms: Yielded<String> = { "no" }
  private var hasLift: Yielded<String> = { "no" }
  private var hasTactileFlooring: Yielded<String> = { "no" }
  private var hasBrailleSignage: Yielded<String> = { "no" }
  private var hasHearingLoop: Yielded<String> = { "no" }
  private var status: Yielded<PropertyStatus> = { PropertyStatus.active }
  private var apCode: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }
  private var qCode: Yielded<String> = { randomStringMultiCaseWithNumbers(6) }

  fun withName(name: String) = apply {
    this.name = { name }
  }

  fun withAddressLine1(addressLine1: String) = apply {
    this.addressLine1 = { addressLine1 }
  }

  fun withPostcode(postcode: String) = apply {
    this.postcode = { postcode }
  }

  fun withLatitude(latitude: Double) = apply {
    this.latitude = { latitude }
  }
  fun withLongitude(longitude: Double) = apply {
    this.longitude = { longitude }
  }

  fun withIsCatered(value: String) = apply {
    this.isCatered = { value }
  }

  fun withIsIAP(value: String) = apply {
    this.isIAP = { value }
  }

  fun withTotalBeds(totalBeds: Int) = apply {
    this.totalBeds = { totalBeds }
  }

  fun withNotes(notes: String) = apply {
    this.notes = { notes }
  }

  fun withProbationRegion(probationRegion: String) = apply {
    this.probationRegion = { probationRegion }
  }

  fun withLocalAuthorityArea(localAuthorityArea: String) = apply {
    this.localAuthorityArea = { localAuthorityArea }
  }

  fun withCharacteristics(characteristics: List<String>) = apply {
    this.characteristics = { characteristics }
  }

  fun withStatus(status: PropertyStatus) = apply {
    this.status = { status }
  }

  fun withApCode(apCode: String) = apply {
    this.apCode = { apCode }
  }

  fun withQCode(qCode: String) = apply {
    this.qCode = { qCode }
  }

  override fun produce() = ApprovedPremisesSeedCsvRow(
    name = this.name(),
    addressLine1 = this.addressLine1(),
    addressLine2 = this.addressLine2(),
    town = this.town(),
    postcode = this.postcode(),
    latitude = this.latitude(),
    longitude = this.longitude(),
    totalBeds = this.totalBeds(),
    notes = this.notes(),
    probationRegion = this.probationRegion(),
    localAuthorityArea = this.localAuthorityArea(),
    characteristics = this.characteristics(),
    isIAP = this.isIAP(),
    isPIPE = this.isPIPE(),
    isESAP = this.isESAP(),
    isSemiSpecialistMentalHealth = this.isSemiSpecialistMentalHealth(),
    isRecoveryFocussed = this.isRecoveryFocussed(),
    isSuitableForVulnerable = this.isSuitableForVulnerable(),
    acceptsSexOffenders = this.acceptsSexOffenders(),
    acceptsChildSexOffenders = this.acceptsChildSexOffenders(),
    acceptsNonSexualChildOffenders = this.acceptsNonSexualChildOffenders(),
    acceptsHateCrimeOffenders = this.acceptsHateCrimeOffenders(),
    isCatered = this.isCatered(),
    hasWideStepFreeAccess = this.hasWideStepFreeAccess(),
    hasWideAccessToCommunalAreas = this.hasWideAccessToCommunalAreas(),
    hasStepFreeAccessToCommunalAreas = this.hasStepFreeAccessToCommunalAreas(),
    hasWheelChairAccessibleBathrooms = this.hasWheelChairAccessibleBathrooms(),
    hasLift = this.hasLift(),
    hasTactileFlooring = this.hasTactileFlooring(),
    hasBrailleSignage = this.hasBrailleSignage(),
    hasHearingLoop = this.hasHearingLoop(),
    status = this.status(),
    apCode = this.apCode(),
    qCode = this.qCode(),
  )
}
