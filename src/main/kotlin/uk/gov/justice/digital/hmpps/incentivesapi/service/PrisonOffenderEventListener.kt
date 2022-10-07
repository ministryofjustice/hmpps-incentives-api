package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class PrisonOffenderEventListener(
  private val prisonerIepLevelReviewService: PrisonerIepLevelReviewService,
  private val mapper: ObjectMapper
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "incentives", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonOffenderEvent(requestJson: String) = runBlocking {
    val (message, messageAttributes) = mapper.readValue(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message, type $eventType")

    when (eventType) {
      "prison-offender-events.prisoner.received" -> {
        val hmppsDomainEvent = mapper.readValue(message, HMPPSDomainEvent::class.java)
        prisonerIepLevelReviewService.processReceivedPrisoner(hmppsDomainEvent)
      }
      "prison-offender-events.prisoner.merged" -> {
        val hmppsDomainEvent = mapper.readValue(message, HMPPSDomainEvent::class.java)
        prisonerIepLevelReviewService.processReceivedPrisoner(hmppsDomainEvent)
      }
      else -> {
        log.debug("Ignoring message with type $eventType")
      }
    }
  }
}

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageAttributes: HMPPSMessageAttributes
)
