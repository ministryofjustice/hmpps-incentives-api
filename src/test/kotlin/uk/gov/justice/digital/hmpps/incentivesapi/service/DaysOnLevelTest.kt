package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDateTime

class DaysOnLevelTest {
  private val prisonApiService: PrisonApiService = mock()
  private val iepLevelService: IepLevelService = mock()
  private val incentiveSummaryService = IncentiveSummaryService(prisonApiService, iepLevelService)

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {

      val iepTime = LocalDateTime.now().minusDays(60)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 60,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
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
      )

      assertThat(daysSinceReview).isEqualTo(60)
      assertThat(calcDaysOnLevel).isEqualTo(60)
    }

    @Test
    fun `calc days on level when moved prison and record should be ignored`() {

      val iepTime = LocalDateTime.now().minusDays(1)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1000,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 5,
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 4,
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = iepTime.minusDays(1).toLocalDate(),
              iepTime = iepTime.minusDays(1),
              userId = "TRANSFERRED",
              auditModuleName = "OIDADMIS"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 3,
              agencyId = "LEI",
              iepLevel = "Enhanced",
              iepDate = iepTime.minusDays(100).toLocalDate(),
              iepTime = iepTime.minusDays(100),
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 2,
              agencyId = "LEI",
              iepLevel = "Standard",
              iepDate = iepTime.minusDays(150).toLocalDate(),
              iepTime = iepTime.minusDays(150),
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "LEI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().minusDays(1),
              iepTime = iepTime.minusDays(1),
              userId = "TRANSFERRED",
              auditModuleName = "OIDADMIS"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(101)
      assertThat(calcDaysOnLevel).isEqualTo(101)
    }

    @Test
    fun `calc days on level when moved prison corrected less then 30 days`() {

      val iepTime = LocalDateTime.now().minusDays(6)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1000,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 5,
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "corrected back to Enhanced",
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 4,
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = iepTime.minusDays(1).toLocalDate(),
              iepTime = iepTime.minusDays(1),
              userId = "TRANSFER",
              auditModuleName = "OIDADMIS"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 3,
              agencyId = "LEI",
              iepLevel = "Enhanced",
              iepDate = iepTime.minusDays(100).toLocalDate(),
              iepTime = iepTime.minusDays(100),
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 2,
              agencyId = "LEI",
              iepLevel = "Standard",
              iepDate = iepTime.minusDays(150).toLocalDate(),
              iepTime = iepTime.minusDays(150),
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "LEI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().minusDays(1),
              iepTime = iepTime.minusDays(1),
              userId = "TRANSFERRED",
              auditModuleName = "OIDADMIS"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(106)
      assertThat(calcDaysOnLevel).isEqualTo(106)
    }

    @Test
    fun `calc days on level when initial entry into prison`() {

      val iepTime = LocalDateTime.now().minusDays(3)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Basic",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "Default IEP",
              userId = "ADMISSION",
              auditModuleName = "OIDADMIS"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(3)
      assertThat(calcDaysOnLevel).isEqualTo(3)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later`() {

      val iepTime = LocalDateTime.now().minusDays(3)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Basic",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().plusDays(3),
              iepTime = iepTime.plusDays(3),
              comments = "First Review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "corrected back to Enhanced",
              userId = "ADMISSION",
              auditModuleName = "OIDADMIS"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(0)
      assertThat(calcDaysOnLevel).isEqualTo(0)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later but up a level`() {

      val iepTime = LocalDateTime.now().minusDays(3)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Standard",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = iepTime.toLocalDate().plusDays(3),
              iepTime = iepTime.plusDays(3),
              comments = "First Review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "corrected back to Enhanced",
              userId = "ADMISSION",
              auditModuleName = "OIDADMIS"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(0)
      assertThat(calcDaysOnLevel).isEqualTo(0)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but up a level`() {

      val iepTime = LocalDateTime.now().minusDays(10)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Standard",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 3,
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = iepTime.toLocalDate().plusDays(9),
              iepTime = iepTime.plusDays(9),
              comments = "New Review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 2,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().plusDays(6),
              iepTime = iepTime.plusDays(6),
              comments = "Admitted into MDI",
              userId = "ADMISSION",
              auditModuleName = "OIDADMIS"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "LEI",
              iepLevel = "Standard",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "Initial review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(10)
      assertThat(calcDaysOnLevel).isEqualTo(10)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but same level`() {

      val iepTime = LocalDateTime.now().minusDays(10)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = -1,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Basic",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 3,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().plusDays(9),
              iepTime = iepTime.plusDays(9),
              comments = "New Review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 2,
              agencyId = "MDI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate().plusDays(6),
              iepTime = iepTime.plusDays(6),
              comments = "Admitted into MDI",
              userId = "ADMISSION",
              auditModuleName = "OIDADMIS"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "LEI",
              iepLevel = "Basic",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              comments = "Initial review",
              userId = "TESTUSER",
              auditModuleName = "PRISON_API"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(1)
      assertThat(calcDaysOnLevel).isEqualTo(10)
    }

    @Test
    fun `calc days on level when new level added today`() {

      val iepTime = LocalDateTime.now()
      val previousIep = iepTime.minusDays(60)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 0,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
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
              agencyId = "MDI",
              iepLevel = "Standard",
              iepDate = previousIep.toLocalDate(),
              iepTime = previousIep,
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(0)
      assertThat(calcDaysOnLevel).isEqualTo(0)
    }

    @Test
    fun `calc days on level when mulitple reviews resulting in same level`() {

      val iepTime = LocalDateTime.now().minusDays(30)
      val previousIep = iepTime.minusDays(60)
      val firstIep = previousIep.minusDays(60)
      val (daysSinceReview, calcDaysOnLevel) = incentiveSummaryService.calcReviewAndDaysOnLevel(
        iepSummary = IepSummary(
          bookingId = 1L,
          daysSinceReview = 30,
          iepDate = iepTime.toLocalDate(),
          iepLevel = "Enhanced",
          iepTime = iepTime,
          iepDetails = listOf(
            IepDetail(
              bookingId = 1L,
              sequence = 3,
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = iepTime.toLocalDate(),
              iepTime = iepTime,
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 2,
              agencyId = "MDI",
              iepLevel = "Enhanced",
              iepDate = previousIep.toLocalDate(),
              iepTime = previousIep,
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
            IepDetail(
              bookingId = 1L,
              sequence = 1,
              agencyId = "MDI",
              iepLevel = "Entry",
              iepDate = firstIep.toLocalDate(),
              iepTime = firstIep,
              userId = "TEST_USER",
              auditModuleName = "PRISON_API"
            ),
          )
        )
      )

      assertThat(daysSinceReview).isEqualTo(30)
      assertThat(calcDaysOnLevel).isEqualTo(90)
    }
  }
}
