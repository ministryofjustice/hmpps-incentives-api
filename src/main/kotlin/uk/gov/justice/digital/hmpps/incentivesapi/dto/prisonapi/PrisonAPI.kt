package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerInfoForNextReviewDate
import java.time.LocalDate

data class Prison(
  val agencyId: String,
  val description: String,
  val longDescription: String?,
  val agencyType: String,
  val active: Boolean,
)

data class PrisonerExtraInfo(
  override val bookingId: Long,
  val offenderNo: String,
  override val dateOfBirth: LocalDate,
  override val receptionDate: LocalDate,
  val alerts: List<PrisonerAlert> = emptyList(),
) : PrisonerInfoForNextReviewDate {
  override val hasAcctOpen = alerts.any(PrisonerAlert::isOpenAcct)
  override val prisonerNumber = offenderNo
}
