package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class SnsService(hmppsQueueService: HmppsQueueService, private val objectMapper: ObjectMapper) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val domaineventsTopic by lazy { hmppsQueueService.findByTopicId("domainevents") ?: throw RuntimeException("Topic with name domainevents doesn't exist") }
  private val domaineventsTopicClient by lazy { domaineventsTopic.snsClient }

  fun publishDomainEvent(eventType: IncentivesDomainEventType, description: String, occurredAt: LocalDateTime, additionalInformation: AdditionalInformation) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType.value,
        additionalInformation,
        occurredAt.atZone(ZoneId.systemDefault()).toInstant(),
        description,
      )
    )
  }

  private fun publishToDomainEventsTopic(payload: HMPPSDomainEvent) {
    log.debug("Event {} for id {}", payload.eventType, payload.additionalInformation.id)
    domaineventsTopicClient.publish(
      PublishRequest(domaineventsTopic.arn, objectMapper.writeValueAsString(payload))
        .withMessageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue().withDataType("String").withStringValue(payload.eventType)
          )
        )
        .also { log.info("Published event $payload to outbound topic") }
    )
  }
}

data class AdditionalInformation(
  val id: Long? = null,
  val nomsNumber: String? = null,
  val reason: String? = null,
  val removedNomsNumber: String? = null,
  val categoriesChanged: List<String>? = null,
  val nextReviewDate: LocalDate? = null,
)

data class HMPPSDomainEvent(
  val eventType: String? = null,
  val additionalInformation: AdditionalInformation,
  val version: String,
  val occurredAt: String,
  val description: String
) {
  constructor(
    eventType: String,
    additionalInformation: AdditionalInformation,
    occurredAt: Instant,
    description: String
  ) : this(
    eventType,
    additionalInformation,
    "1.0",
    occurredAt.toOffsetDateFormat(),
    description
  )
}

enum class IncentivesDomainEventType(val value: String) {
  IEP_REVIEW_INSERTED("incentives.iep-review.inserted"),
  IEP_REVIEW_UPDATED("incentives.iep-review.updated"),
  IEP_REVIEW_DELETED("incentives.iep-review.deleted"),
  PRISONER_NEXT_REVIEW_DATE_CHANGED("incentives.prisoner.next-review-date-changed"),
}

enum class IepReviewReason {
  USER_CREATED_NOMIS, // NOTE: Used by Syscon sync service to discriminate reviews already synced
}

fun Instant.toOffsetDateFormat(): String =
  atZone(ZoneId.of("Europe/London")).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
