package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch

import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerInfoForNextReviewDate
import java.time.LocalDate

data class Prisoner(
  override val bookingId: Long,
  override val prisonerNumber: String,
  override val dateOfBirth: LocalDate,
  override val receptionDate: LocalDate,
  val firstName: String,
  val middleNames: String? = null,
  val lastName: String,
  val prisonId: String,
  val alerts: List<PrisonerAlert> = emptyList(),
) : PrisonerInfoForNextReviewDate {
  override val hasAcctOpen = alerts.any(PrisonerAlert::isOpenAcct)
}

data class PrisonerAlert(
  val alertType: String,
  val alertCode: String,
  val active: Boolean,
  val expired: Boolean,
) {
  companion object {
    const val ACCT_ALERT_CODE = "HA"
  }

  val isOpenAcct = alertCode == ACCT_ALERT_CODE && active && !expired
}

data class PageOfPrisoners(
  val content: List<Prisoner>,
  val totalElements: Int,
  val last: Boolean,
)
