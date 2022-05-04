package uk.gov.justice.digital.hmpps.incentivesapi.integration.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.incentivesapi.service.HMPPSDomainEvent
import java.time.Instant

internal class PrisonOffenderEventListenerIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var repository: PrisonerIepLevelRepository

  @Test
  fun `prisoner received admission reason processed`(): Unit = runBlocking {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77777L)

    publishPrisonerReceivedMessage("prison-offender-events.prisoner.received", "ADMISSION")

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    // val booking = repository.findFirstByBookingIdOrderBySequenceDesc(bookingId)
    // assertThat(booking?.reviewType).isEqualTo(ReviewType.INITIAL)
  }

  private fun publishPrisonerReceivedMessage(eventType: String, reason: String) =
    domainEventsTopicSnsClient.publish(
      PublishRequest(
        domainEventsTopicArn,
        jsonString(
          HMPPSDomainEvent(
            eventType = eventType,
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
            "eventType" to MessageAttributeValue().withDataType("String").withStringValue(eventType),
          )
        )
    )
}
