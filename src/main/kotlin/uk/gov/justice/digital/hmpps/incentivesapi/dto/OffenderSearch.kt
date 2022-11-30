package uk.gov.justice.digital.hmpps.incentivesapi.dto

import java.time.LocalDate

data class OffenderSearchPrisoner(
  val prisonerNumber: String,
  val bookingId: Long,
  val firstName: String,
  val middleNames: String? = null,
  val lastName: String,
  val prisonId: String,
  val prisonName: String,
  val cellLocation: String? = null,
  val locationDescription: String,
  val alerts: List<OffenderSearchPrisonerAlert> = emptyList(),
) {
  val acctOpen = alerts.any { it.alertCode == "HA" && it.active && !it.expired }
}

data class OffenderSearchPrisonerAlert(
  val alertType: String,
  val alertCode: String,
  val active: Boolean,
  val expired: Boolean,
)

data class OffenderSearchPrisonerList(
  val totalElements: Int,
  val content: List<OffenderSearchPrisoner>,
)
