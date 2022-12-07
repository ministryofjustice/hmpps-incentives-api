package uk.gov.justice.digital.hmpps.incentivesapi.integration.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class PrisonOffenderEventListenerIntTest : SqsIntegrationTestBase() {
  private val awaitAtMost30Secs
    get() = await.atMost(Duration.ofSeconds(30))

  @Autowired
  private lateinit var prisonerIepLevelRepository: PrisonerIepLevelRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @ParameterizedTest
  @ValueSource(strings = ["NEW_ADMISSION", "READMISSION"])
  fun `process admissions`(reason: String): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    val locationId = 77777L
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels(prisonId)
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    val reviewsCount = prisonerIepLevelRepository.count()

    // When
    publishPrisonerReceivedMessage(reason)

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        prisonerIepLevelRepository.count() > reviewsCount
      }
    } matches { it == true }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = prisonerIepLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        assertThat(booking?.reviewType).isEqualTo(ReviewType.INITIAL)
      }
    }
  }

  @Test
  fun `prisoner with TRANSFERRED reason is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    val locationId = 77777L
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels(prisonId)
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    // When
    publishPrisonerReceivedMessage("TRANSFERRED")
    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = prisonerIepLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        assertThat(booking?.reviewType).isEqualTo(ReviewType.TRANSFER)
      }
    }
  }

  @Test
  fun `prisoner with MERGE numbers is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294133L
    val oldBookingId = 2343L
    val prisonerNumber = "A1244AB"
    val removedNomsNumber = "A4432FD"
    val locationId = 77777L

    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        bookingId = bookingId,
        prisonerNumber = removedNomsNumber,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(2),
      )
    )
    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        bookingId = oldBookingId,
        prisonerNumber = prisonerNumber,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(50),
      )
    )
    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        bookingId = oldBookingId,
        prisonerNumber = prisonerNumber,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = false,
        reviewTime = LocalDateTime.now().minusDays(200),
      )
    )
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)

    // When
    publishPrisonerMergedMessage(prisonerNumber, removedNomsNumber)

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
  }

  @Test
  fun `process PRISONER ALERTS UPDATED domain events`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)
    prisonApiMockServer.stubIepLevels()
    nextReviewDateRepository.deleteAll()

    // Prisoner was not suitable to return to Standard level
    val lastReviewTime = LocalDateTime.now()
    // Second review after 7 days, still on Basic
    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        prisonId = prisonId,
        locationId = "$prisonId-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = true,
        reviewTime = lastReviewTime,
        reviewType = ReviewType.REVIEW,
      )
    )
    // First review
    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        prisonId = prisonId,
        locationId = "$prisonId-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = false,
        reviewTime = lastReviewTime.minusDays(7),
        reviewType = ReviewType.REVIEW,
      )
    )

    // When
    publishPrisonerAlertsUpdatedMessage(prisonerNumber, bookingId, alertsAdded = emptyList(), alertsRemoved = listOf(PrisonerAlert.ACCT_ALERT_CODE))

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking { nextReviewDateRepository.existsById(bookingId) }
    } matches { it == true }

    assertThat(nextReviewDateRepository.findById(bookingId)?.nextReviewDate)
      .isEqualTo(lastReviewTime.plusDays(28).toLocalDate())
  }

  private fun publishPrisonerReceivedMessage(reason: String) =
    publishDomainEventMessage(
      eventType = "prisoner-offender-search.prisoner.received",
      additionalInformation = AdditionalInformation(
        id = 123,
        nomsNumber = "A1244AB",
        reason = reason
      ),
      description = "A prisoner has been received into a prison with reason: admission on new charges",
    )

  private fun publishPrisonerMergedMessage(nomsNumber: String, removedNomsNumber: String) =
    publishDomainEventMessage(
      eventType = "prison-offender-events.prisoner.merged",
      additionalInformation = AdditionalInformation(
        nomsNumber = nomsNumber,
        removedNomsNumber = removedNomsNumber,
        reason = "MERGE",
      ),
      description = "A prisoner has been merged from $removedNomsNumber to $nomsNumber",
    )

  private fun publishPrisonerAlertsUpdatedMessage(
    nomsNumber: String,
    bookingId: Long,
    alertsAdded: List<String> = listOf(PrisonerAlert.ACCT_ALERT_CODE),
    alertsRemoved: List<String> = emptyList(),
  ) =
    publishDomainEventMessage(
      eventType = "prisoner-offender-search.prisoner.alerts-updated",
      additionalInformation = AdditionalInformation(
        nomsNumber = nomsNumber,
        bookingId = bookingId,
        alertsAdded = alertsAdded,
        alertsRemoved = alertsRemoved,
      ),
      description = "A prisoner record has been updated",
    )

  private fun publishDomainEventMessage(eventType: String, additionalInformation: AdditionalInformation, description: String) {
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          HMPPSDomainEvent(
            eventType = eventType,
            additionalInformation = additionalInformation,
            occurredAt = Instant.now(),
            description = description
          )
        )
      )
        .withMessageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue().withDataType("String")
              .withStringValue(eventType),
          )
        )
    )
  }
}
