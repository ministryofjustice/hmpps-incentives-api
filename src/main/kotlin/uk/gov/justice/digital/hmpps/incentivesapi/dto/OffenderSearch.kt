package uk.gov.justice.digital.hmpps.incentivesapi.dto
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerInfoForNextReviewDate
import java.time.LocalDate

data class OffenderSearchPrisoner(
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

data class OffenderSearchPrisonerList(
  val content: List<OffenderSearchPrisoner>,
  val totalElements: Int,
  val last: Boolean,
)
