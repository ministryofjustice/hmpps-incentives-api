package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch

import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerBasicInfo
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
  override val prisonId: String,
  val alerts: List<PrisonerAlert> = emptyList(),
) : PrisonerBasicInfo,
  PrisonerInfoForNextReviewDate {
  override val hasAcctOpen = alerts.any(PrisonerAlert::isOpenAcct)
}

data class PageOfPrisoners(
  val content: List<Prisoner>,
  val totalElements: Int,
  val last: Boolean,
)
