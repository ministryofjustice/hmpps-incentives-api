package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import java.time.LocalDate
import java.time.LocalDateTime

internal class NextReviewDateServiceTest {

  @Test
  fun `when IEP level is not Basic, returns +1 year`() {
    val input = NextReviewDateInput(
      lastReviewLevel = "Standard",
      lastReviewDate = LocalDate.now(),
      iepDetails = emptyList(),
    )
    val expectedNextReviewDate = input.lastReviewDate.plusYears(1)

    val nextReviewDate = NextReviewDateService().calculate(input)

    assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
  }

  @Nested
  inner class BasicTest {
    @Test
    fun `when IEP level is Basic and no iepDetails, returns +7 days`() {
      val input = NextReviewDateInput(
        lastReviewLevel = "Basic",
        lastReviewDate = LocalDate.now(),
        iepDetails = emptyList(),
      )
      val expectedNextReviewDate = input.lastReviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService().calculate(input)

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and there is no previous review, returns +7 days`() {
      val input = NextReviewDateInput(
        lastReviewLevel = "Basic",
        lastReviewDate = LocalDate.now(),
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
        ),
      )
      val expectedNextReviewDate = input.lastReviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService().calculate(input)

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic but previous review is at different level, returns +7 days`() {
      val input = NextReviewDateInput(
        lastReviewLevel = "Basic",
        lastReviewDate = LocalDate.now(),
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Standard", iepTime = LocalDateTime.now().minusDays(10)),
        ),
      )
      val expectedNextReviewDate = input.lastReviewDate.plusDays(7)

      val nextReviewDate = NextReviewDateService().calculate(input)

      assertThat(nextReviewDate).isEqualTo(expectedNextReviewDate)
    }

    @Test
    fun `when IEP level is Basic and previous IEP level was also Basic, returns +28 days`() {
      val input = NextReviewDateInput(
        lastReviewLevel = "Basic",
        lastReviewDate = LocalDate.now(),
        iepDetails = listOf(
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now()),
          iepDetail(iepLevel = "Basic", iepTime = LocalDateTime.now().minusDays(10)),
        ),
      )
      val expectedNextReviewDate = input.lastReviewDate.plusDays(28)

      val nextReviewDate = NextReviewDateService().calculate(input)

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
