package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import java.time.LocalDateTime

class IepSummaryDateTest {

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {
      val iepTime = LocalDateTime.now().minusDays(60)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate(),
        iepLevel = "Enhanced",
        iepTime = iepTime,
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "LEI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = iepTime.minusDays(100).toLocalDate(),
            iepTime = iepTime.minusDays(100),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(60)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(60)
    }

    @Test
    fun `calc days on level when initial entry into prison`() {
      val iepTime = LocalDateTime.now().minusDays(3)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate(),
        iepLevel = "Basic",
        iepTime = iepTime,
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            comments = "Default IEP",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(3)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(3)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later but up a level`() {
      val iepTime = LocalDateTime.now().minusDays(3)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate().plusDays(3),
        iepLevel = "Standard",
        iepTime = iepTime.plusDays(3),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = iepTime.toLocalDate().plusDays(3),
            iepTime = iepTime.plusDays(3),
            comments = "First Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            comments = "corrected back to Enhanced",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(0)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but same level`() {
      val iepTime = LocalDateTime.now().minusDays(10)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate().plusDays(9),
        iepLevel = "Basic",
        iepTime = iepTime.plusDays(9),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = iepTime.toLocalDate().plusDays(9),
            iepTime = iepTime.plusDays(9),
            comments = "New Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = iepTime.toLocalDate().plusDays(6),
            iepTime = iepTime.plusDays(6),
            comments = "Admitted into MDI",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "LEI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            comments = "Initial review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(1)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(10)
    }

    @Test
    fun `calc days on level when new level added today`() {
      val iepTime = LocalDateTime.now()
      val previousIep = iepTime.minusDays(60)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate(),
        iepLevel = "Enhanced",
        iepTime = iepTime,
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = previousIep.toLocalDate(),
            iepTime = previousIep,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(0)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when mulitple reviews resulting in same level`() {
      val latestIepTime = LocalDateTime.now().minusDays(30)
      val previousIepTime = latestIepTime.minusDays(60)
      val firstIepTime = previousIepTime.minusDays(60)

      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = latestIepTime.toLocalDate(),
        iepLevel = "Enhanced",
        iepTime = latestIepTime,
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = latestIepTime.toLocalDate(),
            iepTime = latestIepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = previousIepTime.toLocalDate(),
            iepTime = previousIepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Entry",
            iepCode = "ENT~",
            iepDate = firstIepTime.toLocalDate(),
            iepTime = firstIepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        )
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(30)
      assertThat(daysOnLevel(iepSummary.iepDetails)).isEqualTo(90)
    }

    @Test
    fun `days on level cannot be calculated when iepDetail history is missing`() {
      val iepTime = LocalDateTime.now().minusDays(3)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate(),
        iepLevel = "Basic",
        iepTime = iepTime,
        iepDetails = emptyList(),
      )

      assertThat(iepSummary.daysSinceReview).isEqualTo(3)
      assertThatThrownBy { daysOnLevel(iepSummary.iepDetails) }
        .isInstanceOf(UnsupportedOperationException::class.java)
        .hasMessageContaining("Empty collection")
    }
  }

  @Nested
  inner class `Calc next review date for someone` {
    @Test
    fun `not on basic, nor newly in prison who has not been transferred since the last review`() {
      val iepTime = LocalDateTime.now().minusDays(60)
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = iepTime.toLocalDate(),
        iepLevel = "Standard",
        iepTime = iepTime,
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = iepTime.toLocalDate(),
            iepTime = iepTime,
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = iepTime.minusDays(100).toLocalDate(),
            iepTime = iepTime.minusDays(100),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        )
      )

      assertThat(iepSummary.nextReviewDate).isEqualTo(iepTime.toLocalDate().plusYears(1))
    }
  }
}
