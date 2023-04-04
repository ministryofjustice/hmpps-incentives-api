package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate
import java.time.Period

data class NextReviewDateInput(
  val hasAcctOpen: Boolean,
  val dateOfBirth: LocalDate,
  val receptionDate: LocalDate,
  val incentiveRecords: List<PrisonerIepLevel>,
)

class NextReviewDateService(private val input: NextReviewDateInput) {

  fun calculate(): LocalDate {
    if (isReadmission()) {
      val readmissionDate = reviews(includeReadmissions = true).first().reviewDate
      return ruleForNewPrisoners(readmissionDate)
    }

    if (isNewPrisoner()) {
      return ruleForNewPrisoners(input.receptionDate)
    }

    if (isOnBasic()) {
      return rulesForBasic()
    }

    return lastReviewDate().plusYears(1)
  }

  private fun rulesForBasic(): LocalDate {
    val lastReviewDate = lastReviewDate()

    // "if not suitable to return to Standard level further reviews must be undertaken at least every 28 days thereafter"
    if (wasConfirmedBasic()) {
      // "Exception for those identified as at Risk of Suicide and Self-harm (ACCT) and for young people,
      // where further reviews must be undertaken at least every 14 days thereafter"
      if (input.hasAcctOpen || wasYoungPersonOnDate(lastReviewDate)) {
        return lastReviewDate.plusDays(14)
      }

      return lastReviewDate.plusDays(28)
    }

    // "PF 5.16 Prisoners placed on Basic must be reviewed within 7 days"
    return lastReviewDate.plusDays(7)
  }

  private fun ruleForNewPrisoners(arrivalDate: LocalDate): LocalDate {
    // NOTE: We care about the age on arrival (not the age "now") because by the time we calculate the
    // next review date the prisoner may no longer be a "young person" but the correct next review date would
    // still be "within 1 month" from arrival

    // "or for young people, within 1 month."
    if (wasYoungPersonOnDate(arrivalDate)) {
      return arrivalDate.plusMonths(1)
    }

    // "PF 5.18 An initial review can take place at any time, subject to the prison reviewing the incentive level
    // of all new prisoners within 3 months from the time they arrive in prison or receive a prison sentence,"
    return arrivalDate.plusMonths(3)
  }

  private fun wasYoungPersonOnDate(date: LocalDate): Boolean {
    val ageOnDate = Period.between(input.dateOfBirth, date).years
    return ageOnDate < 18
  }

  private fun lastReview(): PrisonerIepLevel? {
    return reviews().firstOrNull()
  }

  private fun isOnBasic(): Boolean {
    return lastReview()?.iepCode == IncentiveLevel.BasicCode
  }

  private fun lastReviewDate(): LocalDate {
    return lastReview()?.reviewDate ?: input.receptionDate
  }

  private fun wasConfirmedBasic(): Boolean {
    val reviews = reviews()

    return isOnBasic() && reviews.size >= 2 && reviews[1].iepCode == IncentiveLevel.BasicCode
  }

  private fun isNewPrisoner(): Boolean {
    return reviews().isEmpty()
  }

  private fun isReadmission(): Boolean {
    // NOTE: Readmission/recalls "reviews" are not real incentive reviews, that's why they need to be explicitly included here
    return reviews(includeReadmissions = true).firstOrNull()?.reviewType == ReviewType.READMISSION
  }

  private fun reviews(includeReadmissions: Boolean = false): List<PrisonerIepLevel> {
    return input.incentiveRecords.filter { incentiveRecord ->
      incentiveRecord.isRealReview() ||
        (includeReadmissions && incentiveRecord.reviewType == ReviewType.READMISSION)
    }
  }
}
