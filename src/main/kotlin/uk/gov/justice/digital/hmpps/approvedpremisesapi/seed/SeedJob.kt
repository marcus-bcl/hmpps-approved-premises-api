package uk.gov.justice.digital.hmpps.approvedpremisesapi.seed

import java.io.File

abstract class SeedJob<RowType>(
  val requiredHeaders: Set<String>? = null,
  val runInTransaction: Boolean = true,
) {
  open fun verifyPresenceOfRequiredHeaders(headers: Set<String>) {
    if (requiredHeaders == null) return

    val missingHeaders = requiredHeaders - headers

    if (missingHeaders.any()) {
      throw RuntimeException("required headers: $missingHeaders")
    }
  }

  open fun preSeed() {
    // by default do nothing
  }
  open fun postSeed() {
    // by default do nothing
  }
  abstract fun deserializeRow(columns: Map<String, String>): RowType
  abstract fun processRow(row: RowType)
}

@Suppress("TooGenericExceptionThrown")
interface ExcelSeedJob {
  fun processXlsx(file: File)
}

class SeedException(message: String) : RuntimeException(message)
