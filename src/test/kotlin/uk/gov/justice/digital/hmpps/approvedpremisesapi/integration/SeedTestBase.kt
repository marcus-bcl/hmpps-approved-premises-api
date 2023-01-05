package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import uk.gov.justice.digital.hmpps.approvedpremisesapi.seed.SeedLogger
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.SeedService
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class SeedTestBase : IntegrationTestBase() {
  @Autowired
  lateinit var seedService: SeedService

  @Value("\${seed.file-prefix}")
  lateinit var seedFilePrefix: String

  @MockkBean
  lateinit var mockSeedLogger: SeedLogger
  protected val logEntries = mutableListOf<LogEntry>()

  @BeforeEach
  fun setUp() {
    every { mockSeedLogger.info(any()) } answers {
      logEntries += LogEntry(it.invocation.args[0] as String, "info", null)
    }
    every { mockSeedLogger.error(any()) } answers {
      logEntries += LogEntry(it.invocation.args[0] as String, "error", null)
    }
    every { mockSeedLogger.error(any(), any()) } answers {
      logEntries += LogEntry(it.invocation.args[0] as String, "error", it.invocation.args[1] as Throwable)
    }
  }

  protected fun withCsv(csvName: String, contents: String) {
    if (!Files.isDirectory(Path(seedFilePrefix))) {
      Files.createDirectory(Path(seedFilePrefix))
    }
    Files.writeString(
      Path("$seedFilePrefix/$csvName.csv"), contents,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    )
  }
}

data class LogEntry(
  val message: String,
  val level: String,
  val throwable: Throwable?
)