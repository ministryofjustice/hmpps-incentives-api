package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@DisplayName("Event listener for prisoner-offender-search, unit tests")
class PrisonOffenderEventListenerTest {
  private lateinit var listener: PrisonOffenderEventListener
  private val prisonerIncentiveReviewService: PrisonerIncentiveReviewService = mock()
  private val objectMapper = ObjectMapper().findAndRegisterModules().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  @BeforeEach
  fun setUp() {
    listener = PrisonOffenderEventListener(prisonerIncentiveReviewService, objectMapper)
  }

  @Test
  fun `process admission prisoner message`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/prisonerReceivedReasonAdmission.json".readResourceAsText())

    // Then
    verify(prisonerIncentiveReviewService, times(1)).processOffenderEvent(any())
  }

  @Test
  fun `process transferred prisoner message`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/prisonerReceivedReasonTransferred.json".readResourceAsText())

    // Then
    verify(prisonerIncentiveReviewService, times(1)).processOffenderEvent(any())
  }

  @Test
  fun `do not process eventType of prisoner released`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/prisonerReleased.json".readResourceAsText())

    // Then
    verifyNoInteractions(prisonerIncentiveReviewService)
  }

  @Test
  fun `process merged prisoner message`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/prisonerMerged.json".readResourceAsText())

    // Then
    verify(prisonerIncentiveReviewService, times(1)).processOffenderEvent(any())
  }

  @Test
  fun `process booking moved message`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/bookingMoved.json".readResourceAsText())

    // Then
    verify(prisonerIncentiveReviewService, times(1)).processBookingMovedEvent(any())
  }

  @Test
  fun `process prisoner alerts updated message`(): Unit = runBlocking {
    // When
    listener.onPrisonOffenderEvent("/messages/prisonerAlertsUpdated.json".readResourceAsText())

    // Then
    verify(prisonerIncentiveReviewService, times(1)).processPrisonerAlertsUpdatedEvent(any())
  }

  private fun String.readResourceAsText(): String {
    return PrisonOffenderEventListenerTest::class.java.getResource(this)?.readText()
      ?: throw AssertionError("can not find file")
  }
}
