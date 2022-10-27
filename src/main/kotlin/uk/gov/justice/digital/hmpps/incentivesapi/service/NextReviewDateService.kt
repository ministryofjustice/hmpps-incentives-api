package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import java.time.LocalDate

private const val BASIC = "Basic"

data class NextReviewDateInput(
  val iepDetails: List<IepDetail>,
)

class NextReviewDateService {

  fun calculate(input: NextReviewDateInput): LocalDate {
    val (iepDetails) = input
    val lastReviewDate = iepDetails[0].iepDate

    if (iepDetails[0].iepLevel == BASIC) {
      return nextReviewDateForBasic(input)
    }

    return lastReviewDate.plusYears(1)
  }

  private fun nextReviewDateForBasic(input: NextReviewDateInput): LocalDate {
    val (iepDetails) = input
    val lastReviewDate = iepDetails[0].iepDate

    val lastReviewLevel = iepDetails[0].iepLevel
    if (lastReviewLevel != BASIC) {
      throw IllegalArgumentException("Programming error: private method nextReviewDateForBasic() called when lastReviewLevel was $lastReviewLevel")
    }

    // "if not suitable to return to Standard level further reviews must be undertaken at least every 28 days thereafter"
    if (iepDetails.size >= 2 && iepDetails[1].iepLevel == BASIC) {
      // IEP level was 'Basic' for last 2 reviews
      return lastReviewDate.plusDays(28)
    }

    // "PF 5.16 Prisoners placed on Basic must be reviewed within 7 days"
    return lastReviewDate.plusDays(7)
  }
}
