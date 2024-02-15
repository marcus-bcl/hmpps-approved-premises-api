package uk.gov.justice.digital.hmpps.approvedpremisesapi.integration.notification

import org.assertj.core.api.Assertions.assertThat
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.test.context.event.annotation.BeforeTestMethod
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.EmailRequest
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.SendEmailRequestedEvent
import kotlin.math.exp

@Component
class EmailNotificationAsserter {

  private val requestedEmails = mutableListOf<EmailRequest>()

  @EventListener
  fun consumeEmailRequestedEvent(emailRequested: SendEmailRequestedEvent) {
    requestedEmails.add(emailRequested.request)
  }

  @BeforeTestMethod
  fun resetEmailList() {
    requestedEmails.clear()
  }

  fun assertNoEmailsRequested() {
    assertEmailsRequestedCount(0)
  }

  fun assertEmailRequested(toEmailAddress: String, templateId: String) {
    val anyMatch = requestedEmails.any { toEmailAddress == it.email && templateId == it.templateId }

    assertThat(anyMatch)
      .withFailMessage {
        "Could not find email request. Provided email requests are $requestedEmails"
      }.isTrue
  }

  fun assertEmailsRequestedCount(expectedCount: Int) {
    assertThat(requestedEmails.size).isEqualTo(expectedCount)
  }
}
