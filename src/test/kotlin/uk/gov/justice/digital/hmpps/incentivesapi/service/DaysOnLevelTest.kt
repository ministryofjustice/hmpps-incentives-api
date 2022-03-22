package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import java.time.LocalDateTime

class DaysOnLevelTest {

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {

      val iepTime = LocalDateTime.now().minusDays(60)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(60)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(60)
    }

    @Test
    fun `calc days on level when initial entry into prison`() {

      val iepTime = LocalDateTime.now().minusDays(3)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(3)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(3)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later but up a level`() {

      val iepTime = LocalDateTime.now().minusDays(3)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(0)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(0)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but same level`() {

      val iepTime = LocalDateTime.now().minusDays(10)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(1)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(10)
    }

    @Test
    fun `calc days on level when new level added today`() {

      val iepTime = LocalDateTime.now()
      val previousIep = iepTime.minusDays(60)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(0)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(0)
    }

    @Test
    fun `calc days on level when mulitple reviews resulting in same level`() {

      val iepTime = LocalDateTime.now().minusDays(30)
      val previousIep = iepTime.minusDays(60)
      val firstIep = previousIep.minusDays(60)
      val iepSummary = IepSummary(
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

      assertThat(iepSummary.daysSinceReview()).isEqualTo(30)
      assertThat(iepSummary.daysOnLevel()).isEqualTo(90)
    }
  }
}
