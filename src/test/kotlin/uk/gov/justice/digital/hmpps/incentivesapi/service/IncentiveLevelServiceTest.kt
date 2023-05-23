package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class IncentiveLevelServiceTest {

  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val incentiveLevelRepository: IncentiveLevelRepository = mock()
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val incentiveReviewsService = IncentiveLevelService(
    clock,
    incentiveLevelRepository,
    prisonIncentiveLevelService,
  )

  @Test
  fun `query database if feature flag is true`(): Unit = runBlocking {
    // Given
    whenever(incentiveLevelRepository.findAllByOrderBySequence()).thenReturn(
      flowOf(
        IncentiveLevel(
          code = "",
          name = "Standard",
          sequence = 10,
          new = true,
        ),
      ),
    )

    // When
    incentiveReviewsService.getAllIncentiveLevelsMapByCode()

    // Then
    verify(incentiveLevelRepository, times(1)).findAllByOrderBySequence()
    verifyNoInteractions(prisonApiService)
  }
}
