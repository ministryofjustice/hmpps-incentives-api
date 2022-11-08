package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class PrisonOffenderEventListenerTest {
  private lateinit var listener: PrisonOffenderEventListener
  private val prisonerIepLevelReviewService: PrisonerIepLevelReviewService = mock()
  private val objectMapper = ObjectMapper().findAndRegisterModules().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  @BeforeEach
  fun setUp() {
    listener = PrisonOffenderEventListener(prisonerIepLevelReviewService, objectMapper)
  }

  @Test
  fun `process admission prisoner message`(): Unit = runBlocking {
    coroutineScope {
      // When
      listener.onPrisonOffenderEvent("/messages/prisonerReceivedReasonAdmission.json".readResourceAsText())

      // Then
      verify(prisonerIepLevelReviewService, times(1)).processOffenderEvent(any())
    }
  }

  @Test
  fun `process transferred prisoner message`(): Unit = runBlocking {
    coroutineScope {
      // When
      listener.onPrisonOffenderEvent("/messages/prisonerReceivedReasonTransferred.json".readResourceAsText())

      // Then
      verify(prisonerIepLevelReviewService, times(1)).processOffenderEvent(any())
    }
  }

  @Test
  fun `do not process eventType of prisoner released`(): Unit = runBlocking {
    coroutineScope {
      // When
      listener.onPrisonOffenderEvent("/messages/prisonerReleased.json".readResourceAsText())

      // Then
      verifyNoInteractions(prisonerIepLevelReviewService)
    }
  }

  @Test
  fun `process merged prisoner message`(): Unit = runBlocking {
    coroutineScope {
      // When
      listener.onPrisonOffenderEvent("/messages/prisonerMerged.json".readResourceAsText())

      // Then
      verify(prisonerIepLevelReviewService, times(1)).processOffenderEvent(any())
    }
  }

  private fun String.readResourceAsText(): String {
    return PrisonOffenderEventListenerTest::class.java.getResource(this)?.readText() ?: throw AssertionError("can not find file")
  }
}
