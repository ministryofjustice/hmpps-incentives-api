package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDateTime

class DaysOnLevelTest {
  private val prisonApiService: PrisonApiService = mock()
  private val incentiveSummaryService = IncentiveSummaryService(prisonApiService)

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {

      val iepTime = LocalDateTime.now().minusDays(60)
      val calcDaysOnLevel = incentiveSummaryService.calcDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 60,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime
            ),
            IepDetail(
              agencyId = "LEI",
              iepLevel = "Standard",
              iepDate = iepTime.minusDays(100).toLocalDate(),
              iepTime = iepTime.minusDays(100)
            ),
          )
        )
      )

      assertThat(calcDaysOnLevel).isEqualTo(60)
    }

    @Test
    fun `calc days on level when new level added today`() {

      val iepTime = LocalDateTime.now()
      val previousIep = iepTime.minusDays(60)
      val calcDaysOnLevel = incentiveSummaryService.calcDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 0,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime
            ),
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = previousIep.toLocalDate(),
              iepTime = previousIep
            ),
          )
        )
      )

      assertThat(calcDaysOnLevel).isEqualTo(0)
    }

    @Test
    fun `calc days on level when mulitple reviews resulting in same level`() {

      val iepTime = LocalDateTime.now().minusDays(30)
      val previousIep = iepTime.minusDays(60)
      val firstIep = previousIep.minusDays(60)
      val calcDaysOnLevel = incentiveSummaryService.calcDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 30,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime
            ),
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = previousIep.toLocalDate(),
              iepTime = previousIep
            ),
            IepDetail(
              agencyId = "MDI",
              iepLevel = "Entry",
              iepDate = firstIep.toLocalDate(),
              iepTime = firstIep
            ),
          )
        )
      )

      assertThat(calcDaysOnLevel).isEqualTo(90)
    }
  }
}