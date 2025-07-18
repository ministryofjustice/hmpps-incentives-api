package uk.gov.justice.digital.hmpps.incentivesapi.dto

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
