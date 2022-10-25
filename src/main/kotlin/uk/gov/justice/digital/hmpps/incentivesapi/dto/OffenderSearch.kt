package uk.gov.justice.digital.hmpps.incentivesapi.dto

data class OffenderSearchPrisoner(
  val prisonerNumber: String,
  val bookingId: String,
  val firstName: String,
  val middleNames: String,
  val lastName: String,
  val status: String,
  val inOutStatus: String,
  val prisonId: String,
  val prisonName: String,
  val cellLocation: String,
  val locationDescription: String,
  val alerts: List<OffenderSearchPrisonerAlert>,
) {
  val acctOpen = alerts.find { it.alertCode == "HA" && it.active && !it.expired } != null
}

data class OffenderSearchPrisonerAlert(
  val alertType: String,
  val alertCode: String,
  val active: Boolean,
  val expired: Boolean,
)

data class OffenderSearchPrisonerResponse(
  val totalElements: Int,
  val content: List<OffenderSearchPrisoner>,
)
