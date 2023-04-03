package uk.gov.justice.digital.hmpps.incentivesapi.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AuditEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSMessage
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class IncentiveLevelResourceTestBase : SqsIntegrationTestBase() {
  companion object {
    val clock: Clock = Clock.fixed(
      Instant.parse("2022-03-15T12:34:56+00:00"),
      ZoneId.of("Europe/London"),
    )
    val now: LocalDateTime = LocalDateTime.now(clock)

    var testDomainEventQueueUrl: String? = null
  }

  @Autowired
  protected lateinit var incentiveLevelRepository: IncentiveLevelRepository

  @Autowired
  protected lateinit var prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository

  @BeforeEach
  fun subscribeToDomainEvents() {
    val sqsClient = incentivesQueue.sqsClient
    if (testDomainEventQueueUrl == null) {
      testDomainEventQueueUrl = sqsClient.createQueue("test-domain-events").queueUrl
      val snsClient = domainEventsTopic.snsClient
      snsClient.subscribe(domainEventsTopicArn, "sqs", testDomainEventQueueUrl)
    }
    sqsClient.purgeQueue(PurgeQueueRequest(testDomainEventQueueUrl))
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonIncentiveLevelRepository.deleteAll()
    incentiveLevelRepository.deleteAll()
    incentiveLevelRepository.saveAll(
      listOf(
        IncentiveLevel(code = "BAS", description = "Basic", sequence = 1, required = true, new = true),
        IncentiveLevel(code = "STD", description = "Standard", sequence = 2, required = true, new = true),
        IncentiveLevel(code = "ENH", description = "Enhanced", sequence = 3, required = true, new = true),
        IncentiveLevel(code = "EN2", description = "Enhanced 2", sequence = 4, new = true),
        IncentiveLevel(code = "EN3", description = "Enhanced 3", sequence = 5, new = true),
        IncentiveLevel(code = "ENT", description = "Entry", active = false, sequence = 99, new = true),
      ),
    ).collect()
  }

  private fun AmazonSQS.getApproxQueueSize(queueUrl: String): Int? =
    getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]?.toInt()

  protected fun assertNoDomainEventSent() {
    val sqsClient = incentivesQueue.sqsClient
    val queueSize = sqsClient.getApproxQueueSize(testDomainEventQueueUrl!!)
    assertThat(queueSize).isEqualTo(0)
  }

  protected fun assertDomainEventSent(eventType: String): HMPPSDomainEvent {
    val sqsClient = incentivesQueue.sqsClient
    val queueSize = sqsClient.getApproxQueueSize(testDomainEventQueueUrl!!)
    assertThat(queueSize).isEqualTo(1)

    val body = sqsClient.receiveMessage(testDomainEventQueueUrl).messages[0].body
    val (message, attributes) = objectMapper.readValue(body, HMPPSMessage::class.java)
    assertThat(attributes.eventType.Value).isEqualTo(eventType)
    val domainEvent = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
    assertThat(domainEvent.eventType).isEqualTo(eventType)

    return domainEvent
  }

  protected fun getPublishedDomainEvents(): List<HMPPSDomainEvent> {
    val sqsClient = incentivesQueue.sqsClient
    val request = ReceiveMessageRequest(testDomainEventQueueUrl).withMaxNumberOfMessages(10)
    return sqsClient.receiveMessage(request).messages
      .map {
        val (message) = objectMapper.readValue(it.body, HMPPSMessage::class.java)
        objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      }
  }

  protected fun assertNoAuditMessageSent() {
    val queueSize = auditQueue.sqsClient.getApproxQueueSize(auditQueue.queueUrl)
    assertThat(queueSize).isEqualTo(0)
  }

  private fun assertAuditMessageSent(eventType: String): AuditEvent {
    val queueSize = auditQueue.sqsClient.getApproxQueueSize(auditQueue.queueUrl)
    assertThat(queueSize).isEqualTo(1)

    val message = auditQueue.sqsClient.receiveMessage(auditQueue.queueUrl).messages[0].body
    val event = objectMapper.readValue(message, AuditEvent::class.java)
    assertThat(event.what).isEqualTo(eventType)
    assertThat(event.who).isEqualTo("USER_ADM")
    assertThat(event.service).isEqualTo("hmpps-incentives-api")

    return event
  }

  protected fun <T : Any> assertAuditMessageSentWithList(eventType: String): List<T> {
    val event = assertAuditMessageSent(eventType)
    return objectMapper.readValue(event.details, object : TypeReference<List<T>>() {})
  }

  protected fun assertAuditMessageSentWithMap(eventType: String): Map<String, Any> {
    val event = assertAuditMessageSent(eventType)
    return objectMapper.readValue(event.details, object : TypeReference<Map<String, Any>>() {})
  }

  protected fun getSentAuditMessages(): List<AuditEvent> {
    val request = ReceiveMessageRequest(auditQueue.queueUrl).withMaxNumberOfMessages(10)
    return auditQueue.sqsClient.receiveMessage(request).messages
      .map { objectMapper.readValue(it.body, AuditEvent::class.java) }
  }

  protected fun makePrisonIncentiveLevel(prisonId: String, levelCode: String) = runBlocking {
    prisonIncentiveLevelRepository.save(
      PrisonIncentiveLevel(
        levelCode = levelCode,
        prisonId = prisonId,
        active = levelCode != "ENT",
        defaultOnAdmission = levelCode == "STD",

        remandTransferLimitInPence = when (levelCode) {
          "BAS" -> 27_50
          "STD" -> 60_50
          else -> 66_00
        },
        remandSpendLimitInPence = when (levelCode) {
          "BAS" -> 275_00
          "STD" -> 605_00
          else -> 660_00
        },
        convictedTransferLimitInPence = when (levelCode) {
          "BAS" -> 5_50
          "STD" -> 19_80
          else -> 33_00
        },
        convictedSpendLimitInPence = when (levelCode) {
          "BAS" -> 55_00
          "STD" -> 198_00
          else -> 330_00
        },

        visitOrders = 2,
        privilegedVisitOrders = 1,

        new = true,
        whenUpdated = now.minusDays(3),
      ),
    )
  }
}
