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
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.incentivesapi.service.MergeInformation
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerMergeEvent
import java.time.Duration
import java.time.Instant

internal class PrisonOffenderEventListenerIntTest : SqsIntegrationTestBase() {

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

  @Test
  fun `prisoner with ADMISSION reason is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val locationId = 77777L
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")

    // When
    publishPrisonerReceivedMessage("ADMISSION")

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }

    // Then
    await.atMost(Duration.ofSeconds(30)) untilCallTo {
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
    val locationId = 77777L
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubIEPSummaryForBooking(bookingId = bookingId)

    // When
    publishPrisonerReceivedMessage("TRANSFERRED")
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/locations/$locationId?includeInactive=true") } matches { it == 1 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/$bookingId/iepSummary?withDetails=true") } matches { it == 1 }

    // Then
    await.atMost(Duration.ofSeconds(30)) untilCallTo {
      runBlocking {
        val booking = repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        assertThat(booking?.reviewType).isEqualTo(ReviewType.TRANSFER)
      }
    }
  }

  @Test
  fun `prisoner with MERGE numbers is processed`(): Unit = runBlocking {
    // Given
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val removedNomsNumber = "A4432FD"
    val locationId = 77777L

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)

    // When
    publishPrisonerMergedMessage(prisonerNumber, removedNomsNumber)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { prisonApiMockServer.getCountFor("/api/bookings/offenderNo/$prisonerNumber") } matches { it == 1 }

  }

  private fun publishPrisonerReceivedMessage(reason: String) =
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          HMPPSDomainEvent(
            eventType = "prison-offender-events.prisoner.received",
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
              .withStringValue("prison-offender-events.prisoner.received"),
          )
        )
    )

  private fun publishPrisonerMergedMessage(nomsNumber: String, removedNomsNumber: String) =
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          PrisonerMergeEvent(
            eventType = "prison-offender-events.prisoner.merged",
            additionalInformation = MergeInformation(
              nomsNumber = nomsNumber,
              removedNomsNumber = removedNomsNumber,
              reason = "MERGE"
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
