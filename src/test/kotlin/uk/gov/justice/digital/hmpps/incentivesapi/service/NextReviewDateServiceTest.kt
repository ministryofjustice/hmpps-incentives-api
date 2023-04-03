package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate

class NextReviewDateServiceTest {

  @Test
  fun `when IEP level is not Basic, returns +1 year`() {
    val input = NextReviewDateInput(
      incentiveRecords = listOf(
        incentiveRecord("2015-08-01", "ACI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2015-07-01", "ACI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2010-07-01", "MDI", iepCode = "ENH", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2000-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
      ),
      hasAcctOpen = false,
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2000-07-01"),
    )
    val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusYears(1)

    val nextReviewDate = NextReviewDateService(input).calculate()

    assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
  }

  @Test
  fun `when IEP level is not Basic, returns +1 year (data from NOMIS)`() {
    val input = NextReviewDateInput(
      incentiveRecords = listOf(
        incentiveRecord("2015-08-01", "ACI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2015-07-01", "ACI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2010-07-01", "MDI", iepCode = "ENH", reviewType = ReviewType.MIGRATED),
        incentiveRecord("2000-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
      ),
      hasAcctOpen = false,
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2000-07-01"),
    )
    val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusYears(1)

    val nextReviewDate = NextReviewDateService(input).calculate()

    assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
  }

  @Nested
  inner class BasicRuleTest {

    @Test
    fun `when IEP level is Basic and there is no previous review, returns +7 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2000-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and there is no previous review, returns +7 days (data from NOMIS)`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2000-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and there is no previous 'real' review, returns +7 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2023-01-29", "ACI", iepCode = "BAS", reviewType = ReviewType.REVIEW),
          incentiveRecord("2022-12-01", "ACI", iepCode = "STD", reviewType = ReviewType.TRANSFER),
          incentiveRecord("2022-11-29", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2022-11-29"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic but previous review is at different level, returns +7 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2010-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
          incentiveRecord("2000-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.now(),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and previous IEP level was also Basic, returns +28 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2010-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
          incentiveRecord("2000-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(28)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }

  @Nested
  inner class AcctRuleTest {

    @Test
    fun `when prisoner has open ACCT but they're not on Basic, returns +1 year`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2000-07-01", "MDI", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusYears(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when last two IEP levels were Basic but prisoner has open ACCT, returns +14 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2010-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
          incentiveRecord("2000-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(14)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when last two IEP levels were Basic but is 'young person', returns +14 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          // 16yo on last review date
          incentiveRecord("2016-07-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
          incentiveRecord("2016-05-01", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-05-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(14)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic, previous review is at different level but has open ACCT, returns +7 days`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2023-01-07", "MDI", iepCode = "BAS", reviewType = ReviewType.REVIEW),
          incentiveRecord("2022-12-01", "MDI", iepCode = "STD", reviewType = ReviewType.REVIEW),
          incentiveRecord("2000-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic, previous review is at different level but has open ACCT, returns +7 days (data from NOMIS)`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2023-01-07", "MDI", iepCode = "BAS", reviewType = ReviewType.MIGRATED),
          incentiveRecord("2000-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.incentiveRecords[0].reviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }

  @Nested
  inner class NewPrisonerRulesTest {

    @Test
    fun `when prisoner is new (age 18+), returns +3 months`() {
      val receptionDate = LocalDate.now().minusMonths(6)
      val input = NextReviewDateInput(
        incentiveRecords = emptyList(),
        hasAcctOpen = true,
        // At reception, was 18th birthday, not a "young person" anymore
        dateOfBirth = receptionDate.minusYears(18),
        receptionDate = receptionDate,
      )
      val expectedNextReviewDate = input.receptionDate.plusMonths(3)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `has no real reviews yet (age 18+), returns +3 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2018-08-15", "ACI", iepCode = "STD", reviewType = ReviewType.TRANSFER),
          incentiveRecord("2018-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        // At reception, was 18th birthday, not a "young person" anymore
        receptionDate = LocalDate.parse("2018-07-01"),
      )
      val expectedNextReviewDate = input.receptionDate.plusMonths(3)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is new and 'young person' (under age of 18), returns +1 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = emptyList(),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        // At reception, was almost 18yo, so still a "young person"
        receptionDate = LocalDate.parse("2018-06-30"),
      )
      val expectedNextReviewDate = input.receptionDate.plusMonths(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `has no real reviews yet and is 'young person' (under age of 18), returns +1 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2018-08-15", "ACI", iepCode = "STD", reviewType = ReviewType.TRANSFER),
          incentiveRecord("2018-06-30", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        // At reception, was almost 18yo, so still a "young person"
        receptionDate = LocalDate.parse("2018-06-30"),
      )
      val expectedNextReviewDate = input.receptionDate.plusMonths(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }

  @Nested
  inner class ReadmissionsRulesTest {

    @Test
    fun `when prisoner is readmitted (age 18+), returns +3 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          // When readmitted, was 18th birthday, not a "young person" anymore
          incentiveRecord("2018-07-01", "ACI", iepCode = "STD", reviewType = ReviewType.READMISSION),
          incentiveRecord("2016-08-01", "MDI", iepCode = "STD", reviewType = ReviewType.REVIEW),
          incentiveRecord("2016-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = input.incentiveRecords.first().reviewDate
      val expectedNextReviewDate = readmissionDate.plusMonths(3)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is readmitted and 'young person' (under age of 18), returns +1 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          // When readmitted, was almost 18yo, so still a "young person"
          incentiveRecord("2018-06-30", "ACI", iepCode = "STD", reviewType = ReviewType.READMISSION),
          incentiveRecord("2016-08-01", "MDI", iepCode = "STD", reviewType = ReviewType.REVIEW),
          incentiveRecord("2016-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = input.incentiveRecords.first().reviewDate
      val expectedNextReviewDate = readmissionDate.plusMonths(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is readmitted (age 18+) then transferred, it still returns +3 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2018-07-02", "MDI", iepCode = "STD", reviewType = ReviewType.TRANSFER),
          // When readmitted, was 18th birthday, not a "young person" anymore
          incentiveRecord("2018-07-01", "ACI", iepCode = "STD", reviewType = ReviewType.READMISSION),
          incentiveRecord("2016-08-01", "MDI", iepCode = "STD", reviewType = ReviewType.REVIEW),
          incentiveRecord("2016-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = LocalDate.parse("2018-07-01")
      val expectedNextReviewDate = readmissionDate.plusMonths(3)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is readmitted and 'young person' (under age of 18), then transferred it still returns +1 months`() {
      val input = NextReviewDateInput(
        incentiveRecords = listOf(
          incentiveRecord("2018-07-02", "MDI", iepCode = "STD", reviewType = ReviewType.TRANSFER),
          // When readmitted, was almost 18yo, so still a "young person"
          incentiveRecord("2018-06-30", "ACI", iepCode = "STD", reviewType = ReviewType.READMISSION),
          incentiveRecord("2016-08-01", "MDI", iepCode = "STD", reviewType = ReviewType.REVIEW),
          incentiveRecord("2016-07-01", "MDI", iepCode = "STD", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = LocalDate.parse("2018-06-30")
      val expectedNextReviewDate = readmissionDate.plusMonths(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }
}

private fun incentiveRecord(
  iepDateString: String,
  prisonId: String,
  iepCode: String = "STD",
  reviewType: ReviewType = ReviewType.REVIEW,
): PrisonerIepLevel {
  val iepDate = LocalDate.parse(iepDateString)
  return PrisonerIepLevel(
    id = 111,
    prisonerNumber = "A1234BC",
    iepCode = iepCode,
    reviewTime = iepDate.atTime(10, 0),
    reviewType = reviewType,
    prisonId = prisonId,
    bookingId = 1234567L,
  )
}
