package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IncentiveLevelReviewSummaryDateTest {
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))

  @Nested
  inner class GetDaysOnLevel {
    @Test
    fun `calc days on level when level added 60 days ago`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 222,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 6, 2),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 6, 2, 12, 45, 0),
        nextReviewDate = LocalDate.of(2022, 6, 2).plusYears(1),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 222,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 6, 2),
            iepTime = LocalDateTime.of(2022, 6, 2, 12, 45, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "LEI",
            iepLevel = "Standard",
            iepCode = "STD",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 4, 23),
            iepTime = LocalDateTime.of(2022, 4, 23, 12, 45, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(60)
    }

    @Test
    fun `calc days on level when initial entry into prison`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 111,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 7, 29),
        iepLevel = "Basic",
        iepCode = "BAS",
        iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
        nextReviewDate = LocalDate.of(2022, 7, 29).plusDays(7),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 29),
            iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
            comments = "Default IEP",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(3)
    }

    @Test
    fun `calc days on level when initial entry into prison and review 3 days later but up a level`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 222,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 8, 1),
        iepLevel = "Standard",
        iepCode = "STD",
        iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
        nextReviewDate = LocalDate.of(2022, 8, 1).plusYears(1),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 222,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 8, 1),
            iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
            comments = "First Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 29),
            iepTime = LocalDateTime.of(2022, 7, 29, 12, 45, 0),
            comments = "corrected back to Enhanced",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when entry into a new prison and review 3 days later but same level`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 333,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 7, 31),
        iepLevel = "Basic",
        iepCode = "BAS",
        iepTime = LocalDateTime.of(2022, 7, 31, 19, 0, 0),
        nextReviewDate = LocalDate.of(2022, 7, 31).plusDays(28),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 333,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 31),
            iepTime = LocalDateTime.of(2022, 7, 31, 19, 0, 0),
            comments = "New Review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 222,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Basic",
            iepCode = "BAS",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 28),
            iepTime = LocalDateTime.of(2022, 7, 28, 15, 10, 0),
            comments = "Admitted into MDI",
            userId = "ADMISSION",
            auditModuleName = "OIDADMIS",
          ),
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "LEI",
            iepLevel = "Basic",
            iepCode = "BAS",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 22),
            iepTime = LocalDateTime.of(2022, 7, 22, 10, 0, 0),
            comments = "Initial review",
            userId = "TESTUSER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(1)
    }

    @Test
    fun `calc days on level when new level added today`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 222,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 8, 1),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
        nextReviewDate = LocalDate.of(2022, 8, 1).plusYears(1),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 222,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 8, 1),
            iepTime = LocalDateTime.of(2022, 8, 1, 8, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Standard",
            iepCode = "STD",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 6, 2),
            iepTime = LocalDateTime.of(2022, 6, 2, 11, 25, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(0)
    }

    @Test
    fun `calc days on level when multiple reviews resulting in same level`() {
      val incentiveReviewSummary = IncentiveReviewSummary(
        id = 333,
        bookingId = 1L,
        prisonerNumber = "A1234BC",
        iepDate = LocalDate.of(2022, 7, 2),
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepTime = LocalDateTime.of(2022, 7, 2, 12, 30, 0),
        nextReviewDate = LocalDate.of(2022, 7, 2).plusYears(1),
        incentiveReviewDetails = listOf(
          IncentiveReviewDetail(
            id = 333,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 7, 2),
            iepTime = LocalDateTime.of(2022, 7, 2, 12, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 222,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Enhanced",
            iepCode = "ENH",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 5, 3),
            iepTime = LocalDateTime.of(2022, 5, 3, 11, 25, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
          IncentiveReviewDetail(
            id = 111,
            bookingId = 1L,
            prisonerNumber = "A1234BC",
            agencyId = "MDI",
            iepLevel = "Entry",
            iepCode = "ENT",
            reviewType = ReviewType.REVIEW,
            iepDate = LocalDate.of(2022, 4, 4),
            iepTime = LocalDateTime.of(2022, 4, 4, 10, 30, 0),
            userId = "TEST_USER",
            auditModuleName = "PRISON_API",
          ),
        ),
      )

      assertThat(incentiveReviewSummary.daysSinceReviewCalc(clock)).isEqualTo(30)
    }
  }
}
