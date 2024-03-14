package uk.gov.justice.digital.hmpps.incentivesapi.integration

import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AuditEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSMessage
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
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
  }

  @Autowired
  protected lateinit var incentiveLevelRepository: IncentiveLevelRepository

  @Autowired
  protected lateinit var prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonIncentiveLevelRepository.deleteAll()
    incentiveLevelRepository.deleteAll()
    incentiveLevelRepository.saveAll(
      listOf(
        IncentiveLevel(code = "BAS", name = "Basic", sequence = 1, required = true, new = true),
        IncentiveLevel(code = "STD", name = "Standard", sequence = 2, required = true, new = true),
        IncentiveLevel(code = "ENH", name = "Enhanced", sequence = 3, required = true, new = true),
        IncentiveLevel(code = "EN2", name = "Enhanced 2", sequence = 4, new = true),
        IncentiveLevel(code = "EN3", name = "Enhanced 3", sequence = 5, new = true),
        IncentiveLevel(code = "ENT", name = "Entry", active = false, sequence = 99, new = true),
      ),
    ).collect()
  }

  protected fun assertNoDomainEventSent() {
    val sqsClient = incentivesQueue.sqsClient
    val queueSize = sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get()
    assertThat(queueSize).isEqualTo(0)
  }

  protected fun assertDomainEventSent(eventType: String): HMPPSDomainEvent {
    val sqsClient = incentivesQueue.sqsClient
    await untilCallTo { sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get() } matches { it == 1 }

    val body = sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build()).get().messages()[0].body()
    val (message, attributes) = objectMapper.readValue(body, HMPPSMessage::class.java)
    assertThat(attributes.eventType.Value).isEqualTo(eventType)
    val domainEvent = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
    assertThat(domainEvent.eventType).isEqualTo(eventType)

    return domainEvent
  }

  protected fun getPublishedDomainEvents(): List<HMPPSDomainEvent> {
    val sqsClient = incentivesQueue.sqsClient
    await untilCallTo { sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get() } matches { it != 0 }
    val request = ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).maxNumberOfMessages(10).build()
    return sqsClient.receiveMessage(request).get().messages()
      .map {
        val (message) = objectMapper.readValue(it.body(), HMPPSMessage::class.java)
        objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      }
  }

  protected fun assertNoAuditMessageSent() {
    val queueSize = auditQueue.sqsClient.countMessagesOnQueue(auditQueue.queueUrl).get()
    assertThat(queueSize).isEqualTo(0)
  }

  private fun assertAuditMessageSent(eventType: String): AuditEvent {
    await untilCallTo { auditQueue.sqsClient.countMessagesOnQueue(auditQueue.queueUrl).get() } matches { it != 0 }
    val queueSize = auditQueue.sqsClient.countMessagesOnQueue(auditQueue.queueUrl).get()
    assertThat(queueSize).isEqualTo(1)
    val message = auditQueue.sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(auditQueue.queueUrl).build()).get().messages()[0].body()
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
    await untilCallTo { auditQueue.sqsClient.countMessagesOnQueue(auditQueue.queueUrl).get() } matches { it != 0 }
    val request = ReceiveMessageRequest.builder().queueUrl(auditQueue.queueUrl).maxNumberOfMessages(10).build()
    return auditQueue.sqsClient.receiveMessage(request).get().messages()
      .map { objectMapper.readValue(it.body(), AuditEvent::class.java) }
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
