package uk.gov.justice.digital.hmpps.incentivesapi.integration

import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AuditEvent
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
        IncentiveLevel(code = "BAS", description = "Basic", sequence = 1, required = true, new = true),
        IncentiveLevel(code = "STD", description = "Standard", sequence = 2, required = true, new = true),
        IncentiveLevel(code = "ENH", description = "Enhanced", sequence = 3, required = true, new = true),
        IncentiveLevel(code = "EN2", description = "Enhanced 2", sequence = 4, new = true),
        IncentiveLevel(code = "EN3", description = "Enhanced 3", sequence = 5, new = true),
        IncentiveLevel(code = "ENT", description = "Entry", active = false, sequence = 99, new = true),
      ),
    ).collect()
  }

  protected fun assertNoAuditMessageSent() {
    val queueSize = auditQueue.sqsClient.getQueueAttributes(auditQueue.queueUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]?.toInt()
    assertThat(queueSize).isEqualTo(0)
  }

  private fun assertAuditMessageSent(eventType: String): AuditEvent {
    val queueSize = auditQueue.sqsClient.getQueueAttributes(auditQueue.queueUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]?.toInt()
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
