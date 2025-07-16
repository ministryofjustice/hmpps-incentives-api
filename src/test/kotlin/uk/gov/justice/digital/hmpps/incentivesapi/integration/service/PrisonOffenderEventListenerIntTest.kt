package uk.gov.justice.digital.hmpps.incentivesapi.integration.service

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.incentivesapi.service.AdditionalInformationBookingMoved
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.toMap
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

@DisplayName("Event listener for prisoner-offender-search, integration tests")
class PrisonOffenderEventListenerIntTest : SqsIntegrationTestBase() {
  private val awaitAtMost30Secs
    get() = await.atMost(Duration.ofSeconds(30))

  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonerSearchMockServer.resetRequests()
    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonerSearchMockServer.resetRequests()
    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @ParameterizedTest
  @ValueSource(strings = ["NEW_ADMISSION", "READMISSION"])
  fun `process admissions`(reason: String): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(bookingId, prisonerNumber)
    prisonerSearchMockServer.stubGetPrisonerInfoByBookingId(bookingId, prisonerNumber)

    val reviewsCount = incentiveReviewRepository.count()

    // When
    publishPrisonerReceivedMessage(reason)

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        incentiveReviewRepository.count() > reviewsCount
      }
    } matches { it == true }
    awaitAtMost30Secs untilCallTo {
      prisonerSearchMockServer.getCountFor("/prisoner/$prisonerNumber")
    } matches
      { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = incentiveReviewRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        assertThat(booking?.reviewType).isEqualTo(ReviewType.INITIAL)
      }
    }
  }

  @Test
  fun `prisoner with TRANSFERRED reason is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(bookingId, prisonerNumber)
    prisonerSearchMockServer.stubGetPrisonerInfoByBookingId(bookingId, prisonerNumber)

    // When
    publishPrisonerReceivedMessage("TRANSFERRED")
    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo {
      prisonerSearchMockServer.getCountFor("/prisoner/$prisonerNumber")
    } matches
      { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = incentiveReviewRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
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

    prisonerSearchMockServer.stubGetPrisonerInfoByBookingId(bookingId, prisonerNumber)

    incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = bookingId,
        prisonerNumber = removedNomsNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(2),
      ),
    )
    incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = oldBookingId,
        prisonerNumber = prisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(50),
      ),
    )
    incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = oldBookingId,
        prisonerNumber = prisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = false,
        reviewTime = LocalDateTime.now().minusDays(200),
      ),
    )
    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(bookingId, prisonerNumber)

    // When
    publishPrisonerMergedMessage(prisonerNumber, removedNomsNumber)

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo {
      prisonerSearchMockServer.getCountFor("/prisoner/$prisonerNumber")
    } matches
      { it == 1 }
  }

  @Test
  fun `booking moved event is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294133L
    val oldPrisonerNumber = "A1244AB"
    val newPrisonerNumber = "A4432FD"

    // will have prisoner number changed
    var review1 = incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = bookingId,
        prisonerNumber = oldPrisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(1),
      ),
    )
    // will have prisoner number changed
    var review2 = incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = bookingId,
        prisonerNumber = oldPrisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = false,
        reviewTime = LocalDateTime.now().minusDays(2),
      ),
    )
    // will NOT have prisoner number changed as previous booking
    var review3 = incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = 1000245L,
        prisonerNumber = oldPrisonerNumber,
        prisonId = "MDI",
        reviewedBy = "TEST_STAFF2",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(200),
      ),
    )
    // will remain as is because previous booking under correct prisoner number
    var review4 = incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = 1294130L,
        prisonerNumber = newPrisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF2",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(1),
      ),
    )

    // When
    publishBookingMovedMessage(
      AdditionalInformationBookingMoved(
        bookingId = bookingId,
        movedFromNomsNumber = oldPrisonerNumber,
        movedToNomsNumber = newPrisonerNumber,
        bookingStartDateTime = null,
      ),
    )

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(newPrisonerNumber)
          .count()
      }
    } matches { it == 3 }

    val reviews = incentiveReviewRepository.findAll().toMap { it.id }
    review1 = reviews[review1.id]!!
    review2 = reviews[review2.id]!!
    review3 = reviews[review3.id]!!
    review4 = reviews[review4.id]!!
    assertThat(review1.prisonerNumber).isEqualTo(newPrisonerNumber)
    assertThat(review2.prisonerNumber).isEqualTo(newPrisonerNumber)
    assertThat(review3.prisonerNumber).isEqualTo(oldPrisonerNumber)
    assertThat(review4.prisonerNumber).isEqualTo(newPrisonerNumber)
    assertThat(review4.current).isTrue()
  }

  @Test
  fun `process PRISONER ALERTS UPDATED domain events`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    prisonerSearchMockServer.stubGetPrisonerInfoByBookingId(bookingId, prisonerNumber)
    nextReviewDateRepository.deleteAll()

    // Prisoner was not suitable to return to Standard level
    val lastReviewTime = LocalDateTime.now()
    // Second review after 7 days, still on Basic
    incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        prisonId = prisonId,
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = true,
        reviewTime = lastReviewTime,
        reviewType = ReviewType.REVIEW,
      ),
    )
    // First review
    incentiveReviewRepository.save(
      IncentiveReview(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        prisonId = prisonId,
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = false,
        reviewTime = lastReviewTime.minusDays(7),
        reviewType = ReviewType.REVIEW,
      ),
    )

    // When
    publishPrisonerAlertsUpdatedMessage(
      prisonerNumber,
      bookingId,
      alertsAdded = emptyList(),
      alertsRemoved = listOf(PrisonerAlert.ACCT_ALERT_CODE),
    )

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking { nextReviewDateRepository.existsById(bookingId) }
    } matches { it == true }

    assertThat(nextReviewDateRepository.findById(bookingId)?.nextReviewDate)
      .isEqualTo(lastReviewTime.plusDays(28).toLocalDate())
  }

  private fun publishPrisonerReceivedMessage(reason: String) = publishDomainEventMessage(
    eventType = "prisoner-offender-search.prisoner.received",
    additionalInformation = AdditionalInformation(
      id = 123,
      nomsNumber = "A1244AB",
      reason = reason,
    ),
    description = "A prisoner has been received into a prison with reason: admission on new charges",
  )

  private fun publishPrisonerMergedMessage(
    @Suppress("SameParameterValue")
    nomsNumber: String,
    @Suppress("SameParameterValue")
    removedNomsNumber: String,
  ) = publishDomainEventMessage(
    eventType = "prison-offender-events.prisoner.merged",
    additionalInformation = AdditionalInformation(
      nomsNumber = nomsNumber,
      removedNomsNumber = removedNomsNumber,
      reason = "MERGE",
    ),
    description = "A prisoner has been merged from $removedNomsNumber to $nomsNumber",
  )

  private fun publishPrisonerAlertsUpdatedMessage(
    @Suppress("SameParameterValue")
    nomsNumber: String,
    @Suppress("SameParameterValue")
    bookingId: Long,
    alertsAdded: List<String> = listOf(PrisonerAlert.ACCT_ALERT_CODE),
    alertsRemoved: List<String> = emptyList(),
  ) = publishDomainEventMessage(
    eventType = "prisoner-offender-search.prisoner.alerts-updated",
    additionalInformation = AdditionalInformation(
      nomsNumber = nomsNumber,
      bookingId = bookingId,
      alertsAdded = alertsAdded,
      alertsRemoved = alertsRemoved,
    ),
    description = "A prisoner record has been updated",
  )

  private fun publishDomainEventMessage(
    eventType: String,
    additionalInformation: AdditionalInformation,
    description: String,
  ) {
    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            HMPPSDomainEvent(
              eventType = eventType,
              additionalInformation = additionalInformation,
              occurredAt = Instant.now(),
              description = description,
            ),
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
          ),
        )
        .build(),
    )
  }

  private fun publishBookingMovedMessage(additionalInformation: AdditionalInformationBookingMoved) =
    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            HMPPSBookingMovedDomainEvent(
              eventType = "prison-offender-events.prisoner.booking.moved",
              additionalInformation = additionalInformation,
              occurredAt = ZonedDateTime.now(),
              version = "1.0",
              description = "a NOMIS booking has moved between prisoners",
            ),
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to
              MessageAttributeValue.builder().dataType(
                "String",
              ).stringValue("prison-offender-events.prisoner.booking.moved").build(),
          ),
        )
        .build(),
    )
}
