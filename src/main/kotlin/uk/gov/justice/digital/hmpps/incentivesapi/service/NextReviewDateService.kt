package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import java.time.LocalDate

private const val BASIC = "Basic"

data class NextReviewDateInput(
  val iepDetails: List<IepDetail>,
  val hasAcctOpen: Boolean,
)

class NextReviewDateService {

  fun calculate(input: NextReviewDateInput): LocalDate {
    val (iepDetails) = input
    val lastReviewDate = iepDetails[0].iepDate

    var nextReviewDateCandidates = mutableListOf<LocalDate?>(
      lastReviewDate.plusYears(1),
    )

    val rules = listOf(
      ::ruleForBasic,
      ::ruleForAcct,
    )
    for (rule in rules) {
      nextReviewDateCandidates.add(rule(input))
    }

    // Returns the earliest next review date that applies
    return nextReviewDateCandidates.filterNotNull().min()
  }

  private fun ruleForBasic(input: NextReviewDateInput): LocalDate? {
    val (iepDetails) = input
    val lastReview = iepDetails[0]

    if (lastReview.iepLevel != BASIC) {
      return null
    }

    // "if not suitable to return to Standard level further reviews must be undertaken at least every 28 days thereafter"
    if (iepDetails.size >= 2 && iepDetails[1].iepLevel == BASIC) {
      // IEP level was 'Basic' for last 2 reviews
      return lastReview.iepDate.plusDays(28)
    }

    // "PF 5.16 Prisoners placed on Basic must be reviewed within 7 days"
    return lastReview.iepDate.plusDays(7)
  }

  private fun ruleForAcct(input: NextReviewDateInput): LocalDate? {
    val (iepDetails, hasAcctOpen) = input
    val lastReviewDate = iepDetails[0].iepDate

    // "Exception for those identified as at Risk of Suicide and Self-harm (ACCT) and for young people,
    // where further reviews must be undertaken at least every 14 days thereafter"
    return if (hasAcctOpen) lastReviewDate.plusDays(14) else null
  }
}
