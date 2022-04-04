package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDateTime

class PrisonerIepLevelReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val iepLevelRepository: IepLevelRepository = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private val prisonerIepLevelReviewService = PrisonerIepLevelReviewService(
    prisonApiService,
    prisonerIepLevelRepository,
    iepLevelRepository,
    authenticationFacade
  )

  @Nested
  inner class getPrisonerIepLevelHistory {
    @Test
    fun `will call prison api if useNomisData is true`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonApiService.getIEPSummaryForPrisoner(any(), any())).thenReturn(iepSummaryWithDetail)

        // When
        prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = true)

        // Then
        verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(1234567, true)
        verifyNoInteractions(prisonerIepLevelRepository)
      }
    }

    @Test
    fun `will call prison api if useNomisData is true and will not return iep details if withDetails is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonApiService.getIEPSummaryForPrisoner(any(), any())).thenReturn(iepSummary)

        // When
        prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = true, withDetails = false)

        // Then
        verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(1234567, false)
        verifyNoInteractions(prisonerIepLevelRepository)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(any())).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderBySequenceDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isEqualTo(2)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false and will not return iep details if withDetails is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(any())).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false, withDetails = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderBySequenceDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isZero()
      }
    }
  }

  private val iepTime: LocalDateTime = LocalDateTime.now().minusDays(10)
  private val iepSummary = IepSummary(
    bookingId = 1L,
    daysSinceReview = 60,
    iepDate = iepTime.toLocalDate(),
    iepLevel = "Enhanced",
    iepTime = iepTime,
    iepDetails = emptyList(),
  )
  private val iepSummaryWithDetail = iepSummary.copy(
    iepDetails = listOf(
      IepDetail(
        bookingId = 1L,
        sequence = 2,
        agencyId = "MDI",
        iepLevel = "Enhanced",
        iepDate = iepTime.toLocalDate(),
        iepTime = iepTime,
        userId = "TEST_USER",
        auditModuleName = "PRISON_API"
      ),
      IepDetail(
        bookingId = 1L,
        sequence = 1,
        agencyId = "LEI",
        iepLevel = "Standard",
        iepDate = iepTime.minusDays(100).toLocalDate(),
        iepTime = iepTime.minusDays(100),
        userId = "TEST_USER",
        auditModuleName = "PRISON_API"
      ),
    )
  )

  private val previousLevel = PrisonerIepLevel(
    iepCode = "BAS",
    prisonId = "LEI",
    locationId = "LEI-1-1-001",
    bookingId = 1234567,
    sequence = 1,
    current = false,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now().minusDays(2),
    prisonerNumber = "A1234AB"
  )

  private val currentLevel = PrisonerIepLevel(
    iepCode = "STD",
    prisonId = "MDI",
    locationId = "MDI-1-1-004",
    bookingId = 1234567,
    sequence = 2,
    current = true,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now(),
    prisonerNumber = "A1234AB"
  )

  private val currentAndPreviousLevels = flowOf(previousLevel, currentLevel)
}
