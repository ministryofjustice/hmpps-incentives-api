package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import java.time.LocalDate

class NextReviewDateServiceTest {

  @Test
  fun `when IEP level is not Basic, returns +1 year`() {
    val input = NextReviewDateInput(
      iepDetails = listOf(
        review("2015-08-01", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        review("2015-07-01", "ACI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        review("2010-07-01", "MDI", iepCode = "ENH", iepLevel = "Enhanced", reviewType = ReviewType.MIGRATED),
        review("2000-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
      ),
      hasAcctOpen = false,
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2000-07-01"),
    )
    val expectedNextReviewDate = input.iepDetails[0].iepDate.plusYears(1)

    val nextReviewDate = NextReviewDateService(input).calculate()

    assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
  }

  @Test
  fun `when IEP level is not Basic, returns +1 year (data from NOMIS)`() {
    val input = NextReviewDateInput(
      iepDetails = listOf(
        review("2015-08-01", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        review("2015-07-01", "ACI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        review("2010-07-01", "MDI", iepCode = "ENH", iepLevel = "Enhanced", reviewType = ReviewType.MIGRATED),
        review("2000-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
      ),
      hasAcctOpen = false,
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2000-07-01"),
    )
    val expectedNextReviewDate = input.iepDetails[0].iepDate.plusYears(1)

    val nextReviewDate = NextReviewDateService(input).calculate()

    assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
  }

  @Nested
  inner class BasicRuleTest {

    @Test
    fun `when IEP level is Basic and there is no previous review, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2000-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and there is no previous review, returns +7 days (data from NOMIS)`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2000-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and there is no previous 'real' review, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2023-01-29", "ACI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.REVIEW),
          review("2022-12-01", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.TRANSFER),
          review("2022-11-29", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2022-11-29"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic but previous review is at different level, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2010-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
          review("2000-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.now(),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and previous IEP level was also Basic, returns +28 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2010-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
          review("2000-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(28)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }

  @Nested
  inner class AcctRuleTest {

    @Test
    fun `when prisoner has open ACCT but they're not on Basic, returns +1 year`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2000-07-01", "MDI", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusYears(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when last two IEP levels were Basic but prisoner has open ACCT, returns +14 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2010-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
          review("2000-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(14)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when last two IEP levels were Basic but is 'young person', returns +14 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          // 16yo on last review date
          review("2016-07-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
          review("2016-05-01", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = false,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-05-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(14)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic, previous review is at different level but has open ACCT, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2023-01-07", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.REVIEW),
          review("2022-12-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.REVIEW),
          review("2000-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic, previous review is at different level but has open ACCT, returns +7 days (data from NOMIS)`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2023-01-07", "MDI", iepCode = "BAS", iepLevel = "Basic", reviewType = ReviewType.MIGRATED),
          review("2000-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.MIGRATED),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2000-07-01"),
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

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
        iepDetails = emptyList(),
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
        iepDetails = listOf(
          review("2018-08-15", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.TRANSFER),
          review("2018-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
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
        iepDetails = emptyList(),
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
        iepDetails = listOf(
          review("2018-08-15", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.TRANSFER),
          review("2018-06-30", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
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
        iepDetails = listOf(
          // When readmitted, was 18th birthday, not a "young person" anymore
          review("2018-07-01", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.READMISSION),
          review("2016-08-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.REVIEW),
          review("2016-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = input.iepDetails.first().iepDate
      val expectedNextReviewDate = readmissionDate.plusMonths(3)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is readmitted and 'young person' (under age of 18), returns +1 months`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          // When readmitted, was almost 18yo, so still a "young person"
          review("2018-06-30", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.READMISSION),
          review("2016-08-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.REVIEW),
          review("2016-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
        ),
        hasAcctOpen = true,
        dateOfBirth = LocalDate.parse("2000-07-01"),
        receptionDate = LocalDate.parse("2016-07-01"),
      )
      val readmissionDate = input.iepDetails.first().iepDate
      val expectedNextReviewDate = readmissionDate.plusMonths(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when prisoner is readmitted (age 18+) then transferred, it still returns +3 months`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          review("2018-07-02", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.TRANSFER),
          // When readmitted, was 18th birthday, not a "young person" anymore
          review("2018-07-01", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.READMISSION),
          review("2016-08-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.REVIEW),
          review("2016-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
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
        iepDetails = listOf(
          review("2018-07-02", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.TRANSFER),
          // When readmitted, was almost 18yo, so still a "young person"
          review("2018-06-30", "ACI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.READMISSION),
          review("2016-08-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.REVIEW),
          review("2016-07-01", "MDI", iepCode = "STD", iepLevel = "Standard", reviewType = ReviewType.INITIAL),
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

private fun review(
  iepDateString: String,
  prisonId: String,
  iepCode: String = "STD",
  iepLevel: String = "Standard",
  reviewType: ReviewType = ReviewType.REVIEW,
): IepDetail {
  val iepDate = LocalDate.parse(iepDateString)
  return IepDetail(
    id = 111,
    prisonerNumber = "A1234BC",
    iepLevel = iepLevel,
    iepCode = iepCode,
    iepTime = iepDate.atTime(10, 0),
    iepDate = iepDate,
    reviewType = reviewType,
    agencyId = prisonId,
    bookingId = 1234567L,
    userId = null,
    auditModuleName = SYSTEM_USERNAME,
  )
}
