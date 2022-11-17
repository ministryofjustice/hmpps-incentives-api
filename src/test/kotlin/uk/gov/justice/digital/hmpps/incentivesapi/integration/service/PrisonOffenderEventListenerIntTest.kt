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
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
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
  private lateinit var repository: PrisonerIepLevelRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    repository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    repository.deleteAll()
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
    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId, prisonerNumber, locationId)
    offenderSearchMockServer.stubGetOffender(prisonId, prisonerNumber, bookingId)

    // When
    publishPrisonerReceivedMessage(reason)

    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
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
    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubIEPSummaryForBooking(bookingId = bookingId)
    offenderSearchMockServer.stubGetOffender(prisonId, prisonerNumber, bookingId)

    // When
    publishPrisonerReceivedMessage("TRANSFERRED")
    awaitAtMost30Secs untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    awaitAtMost30Secs untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }

    // Then
    awaitAtMost30Secs untilCallTo {
      runBlocking {
        val booking = repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
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

    repository.save(
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
    repository.save(
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
    repository.save(
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

  private fun publishPrisonerReceivedMessage(reason: String) =
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          HMPPSDomainEvent(
            eventType = "prisoner-offender-search.prisoner.received",
            additionalInformation = AdditionalInformation(
              id = 123,
              nomsNumber = "A1244AB",
              reason = reason
            ),
            occurredAt = Instant.now(),
            description = "A prisoner has been received into prison"
          )
        )
      )
        .withMessageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue().withDataType("String")
              .withStringValue("prisoner-offender-search.prisoner.received"),
          )
        )
    )

  private fun publishPrisonerMergedMessage(nomsNumber: String, removedNomsNumber: String) =
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          HMPPSDomainEvent(
            eventType = "prison-offender-events.prisoner.merged",
            additionalInformation = AdditionalInformation(
              nomsNumber = nomsNumber,
              removedNomsNumber = removedNomsNumber,
              reason = "MERGE",
            ),
            occurredAt = Instant.now(),
            description = "A prisoner has been merged from $removedNomsNumber to $nomsNumber"
          )
        )
      )
        .withMessageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue().withDataType("String")
              .withStringValue("prison-offender-events.prisoner.merged"),
          )
        )
    )
}
