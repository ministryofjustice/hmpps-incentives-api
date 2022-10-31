package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import java.time.LocalDateTime

internal class NextReviewDateServiceTest {

  @Test
  fun `when IEP level is not Basic, returns +1 year`() {
    val input = NextReviewDateInput(
      iepDetails = listOf(
        iepDetail(iepLevel = "Standard", iepTime = LocalDateTime.now()),
      ),
      hasAcctOpen = false,
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
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
        ),
        hasAcctOpen = false,
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic but previous review is at different level, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Standard", iepTime = LocalDateTime.now().minusDays(10)),
        ),
        hasAcctOpen = false,
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and previous IEP level was also Basic, returns +28 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now().minusDays(10)),
        ),
        hasAcctOpen = false,
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
          iepDetail(iepLevel = "Standard", iepTime = LocalDateTime.now()),
        ),
        hasAcctOpen = true,
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusYears(1)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when last two IEP levels were Basic but prisoner has open ACCT, returns +14 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now().minusDays(10)),
        ),
        hasAcctOpen = true,
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(14)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic, previous review is at different level but has open ACCT, returns +7 days`() {
      val input = NextReviewDateInput(
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Standard", iepTime = LocalDateTime.now().minusDays(10)),
        ),
        hasAcctOpen = true,
      )
      val expectedNextReviewDate = input.iepDetails[0].iepDate.plusDays(7)

      val nextReviewDate = NextReviewDateService(input).calculate()

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }
}

fun iepDetail(iepLevel: String, iepTime: LocalDateTime): IepDetail {
  return IepDetail(
    iepLevel = iepLevel,
    iepTime = iepTime,
    iepDate = iepTime.toLocalDate(),
    agencyId = "MDI",
    bookingId = 1234567L,
    userId = null,
  )
}
