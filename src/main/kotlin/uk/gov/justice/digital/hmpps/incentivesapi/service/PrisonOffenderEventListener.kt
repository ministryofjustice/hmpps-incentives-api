package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrisonOffenderEventListener(
  private val prisonerIepLevelReviewService: PrisonerIepLevelReviewService,
  private val mapper: ObjectMapper,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("incentives", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "hmpps-incentives-prisoner-event-queue", kind = SpanKind.SERVER)
  fun onPrisonOffenderEvent(requestJson: String) = runBlocking {
    val (message, messageAttributes) = mapper.readValue(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message, type $eventType")

    val hmppsDomainEvent = mapper.readValue(message, HMPPSDomainEvent::class.java)
    when (eventType) {
      "prisoner-offender-search.prisoner.received", "prison-offender-events.prisoner.merged" -> {
        prisonerIepLevelReviewService.processOffenderEvent(hmppsDomainEvent)
      }
      "prisoner-offender-search.prisoner.alerts-updated" -> {
        prisonerIepLevelReviewService.processPrisonerAlertsUpdatedEvent(hmppsDomainEvent)
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
  val MessageAttributes: HMPPSMessageAttributes,
)
