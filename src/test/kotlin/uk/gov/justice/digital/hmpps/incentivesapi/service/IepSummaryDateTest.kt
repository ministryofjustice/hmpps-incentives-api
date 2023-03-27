package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IepSummaryDateTest {
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 6, 2),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 6, 2, 12, 45, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = LocalDate.of(2022, 6, 2),
            iepTime = LocalDateTime.of(2022, 6, 2, 12, 45, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "LEI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = LocalDate.of(2022, 4, 23),
            iepTime = LocalDateTime.of(2022, 4, 23, 12, 45, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(60)
    }

    @Test
    fun `calc days on level when initial entry into prison`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 7, 29),
        iepLevel = "Basic",
        iepCode = "BAS",
        iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = LocalDate.of(2022, 7, 29),
            iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
            comments = "Default IEP",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(3)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later but up a level`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 8, 1),
        iepLevel = "Standard",
        iepCode = "STD",
        iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = LocalDate.of(2022, 8, 1),
            iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
            comments = "First Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = LocalDate.of(2022, 7, 29),
            iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
            comments = "corrected back to Enhanced",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but same level`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 7, 31),
        iepLevel = "Basic",
        iepCode = "BAS",
        iepTime = LocalDateTime.of(2022, 7, 31, 19, 0, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = LocalDate.of(2022, 7, 31),
            iepTime = LocalDateTime.of(2022, 7, 31, 19, 0, 0),
            comments = "New Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = LocalDate.of(2022, 7, 28),
            iepTime = LocalDateTime.of(2022, 7, 28, 15, 10, 0),
            comments = "Admitted into MDI",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "LEI",
            iepLevel = "Basic",
            iepCode = "BAS",
            iepDate = LocalDate.of(2022, 7, 22),
            iepTime = LocalDateTime.of(2022, 7, 22, 10, 0, 0),
            comments = "Initial review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(1)
    }

    @Test
    fun `calc days on level when new level added today`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 8, 1),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = LocalDate.of(2022, 8, 1),
            iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            iepDate = LocalDate.of(2022, 6, 2),
            iepTime = LocalDateTime.of(2022, 6, 2, 11, 25, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when multiple reviews resulting in same level`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 7, 2),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 7, 2, 12, 30, 0),
        iepDetails = listOf(
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = LocalDate.of(2022, 7, 2),
            iepTime = LocalDateTime.of(2022, 7, 2, 12, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            iepDate = LocalDate.of(2022, 5, 3),
            iepTime = LocalDateTime.of(2022, 5, 3, 11, 25, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IepDetail(
            bookingId = 1L,
            agencyId = "MDI",
            iepLevel = "Entry",
            iepCode = "ENT",
            iepDate = LocalDate.of(2022, 4, 4),
            iepTime = LocalDateTime.of(2022, 4, 4, 10, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(30)
    }

    @Test
    fun `days on level cannot be calculated when iepDetail history is missing`() {
      val iepSummary = IepSummary(
        bookingId = 1L,
        iepDate = LocalDate.of(2022, 7, 29),
        iepLevel = "Basic",
        iepCode = "BAS",
        iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
        iepDetails = emptyList(),
      )

      assertThat(iepSummary.daysSinceReviewCalc(clock)).isEqualTo(3)
    }
  }
}
