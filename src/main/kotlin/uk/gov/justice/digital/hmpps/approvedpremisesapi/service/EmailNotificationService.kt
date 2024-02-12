package uk.gov.justice.digital.hmpps.approvedpremisesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.approvedpremisesapi.config.NotifyConfig
import uk.gov.justice.digital.hmpps.approvedpremisesapi.config.NotifyMode
import uk.gov.service.notify.NotificationClient
import uk.gov.service.notify.NotificationClientException

@Service
class EmailNotificationService(
  private val notifyConfig: NotifyConfig,
  @Qualifier("normalNotificationClient") private val normalNotificationClient: NotificationClient?,
  @Qualifier("guestListNotificationClient") private val guestListNotificationClient: NotificationClient?,
  private val applicationEventPublisher: ApplicationEventPublisher,
) {
  var log: Logger = LoggerFactory.getLogger(this::class.java)

  fun sendEmail(email: String, templateId: String, personalisation: Map<String, *>) {
    applicationEventPublisher.publishEvent(SendEmailRequestedEvent(EmailRequest(email, templateId, personalisation)))

    try {
      if (notifyConfig.mode == NotifyMode.DISABLED) {
        log.info("Email sending is disabled - would have sent template $templateId to user $email")
        return
      }

      if (notifyConfig.mode == NotifyMode.TEST_AND_GUEST_LIST) {
        guestListNotificationClient!!.sendEmail(templateId, email, personalisation, null)
      } else {
        normalNotificationClient!!.sendEmail(templateId, email, personalisation, null)
      }
    } catch (notificationClientException: NotificationClientException) {
      log.error("Unable to send template $templateId to user $email", notificationClientException)
    }
  }
}

data class EmailRequest(val email: String, val templateId: String, val personalisation: Map<String, *>)

data class SendEmailRequestedEvent(val request: EmailRequest)
