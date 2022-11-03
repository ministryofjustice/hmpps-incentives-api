package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import java.time.LocalDate

private const val BASIC = "Basic"

data class NextReviewDateInput(
  val hasAcctOpen: Boolean,
  val receptionDate: LocalDate,
  val iepDetails: List<IepDetail>,
)

class NextReviewDateService(private val input: NextReviewDateInput) {

  fun calculate(): LocalDate {
    if (isNewPrisoner()) {
      return ruleForNewPrisoners()
    }

    if (isOnBasic()) {
      return rulesForBasic()
    }

    return lastReviewDate().plusYears(1)
  }

  private fun rulesForBasic(): LocalDate {
    // "if not suitable to return to Standard level further reviews must be undertaken at least every 28 days thereafter"
    if (wasConfirmedBasic()) {
      // "Exception for those identified as at Risk of Suicide and Self-harm (ACCT) and for young people,
      // where further reviews must be undertaken at least every 14 days thereafter"
      if (input.hasAcctOpen) {
        return lastReviewDate().plusDays(14)
      }

      return lastReviewDate().plusDays(28)
    }

    // "PF 5.16 Prisoners placed on Basic must be reviewed within 7 days"
    return lastReviewDate().plusDays(7)
  }

  private fun ruleForNewPrisoners(): LocalDate {
    // "PF 5.18 An initial review can take place at any time, subject to the prison reviewing the incentive level
    // of all new prisoners within 3 months from the time they arrive in prison or receive a prison sentence."
    return input.receptionDate.plusMonths(3)
  }

  private fun lastReview(): IepDetail {
    return input.iepDetails.first()
  }

  private fun isOnBasic(): Boolean {
    return lastReview().iepLevel == BASIC
  }

  private fun lastReviewDate(): LocalDate {
    return lastReview().iepDate
  }

  private fun wasConfirmedBasic(): Boolean {
    val iepDetails = input.iepDetails

    return isOnBasic() && iepDetails.size >= 2 && iepDetails[1].iepLevel == BASIC
  }

  private fun isNewPrisoner(): Boolean {
    return input.iepDetails.isEmpty()
  }
}
