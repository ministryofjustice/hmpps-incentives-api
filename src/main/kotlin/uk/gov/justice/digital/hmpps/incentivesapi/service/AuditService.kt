package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Instant

@Service
class AuditService(
  @Value("\${spring.application.name}")
  private val serviceName: String,
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  private val authenticationFacade: AuthenticationFacade,
) {
  private val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") as HmppsQueue }
  private val auditSqsClient by lazy { auditQueue.sqsClient }
  private val auditQueueUrl by lazy { auditQueue.queueUrl }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun sendMessage(auditType: AuditType, id: String, details: Any, username: String? = null) {
    val auditEvent = AuditEvent(
      what = auditType.name,
      who = username ?: authenticationFacade.getUsername(),
      service = serviceName,
      details = objectMapper.writeValueAsString(details),
    )
    log.debug("Audit {} ", auditEvent)

    val result =
      auditSqsClient.sendMessage(
        SendMessageRequest(
          auditQueueUrl,
          auditEvent.toJson(),
        ),
      )

    telemetryClient.trackEvent(
      auditEvent.what,
      mapOf("messageId" to result.messageId, "id" to id),
      null,
    )
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}

data class AuditEvent(
  val what: String,
  val `when`: Instant = Instant.now(),
  val who: String,
  val service: String,
  val details: String? = null,
)

enum class AuditType {
  IEP_REVIEW_ADDED,
  IEP_REVIEW_UPDATED,
  IEP_REVIEW_DELETED,
  PRISONER_NUMBER_MERGE,
  INCENTIVE_LEVEL_ADDED,
  INCENTIVE_LEVEL_UPDATED,
  INCENTIVE_LEVELS_REORDERED,
  PRISON_INCENTIVE_LEVEL_UPDATED,
}
