package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import org.springframework.format.annotation.DateTimeFormat
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerBasicInfo
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerInfoForNextReviewDate
import java.time.LocalDate
import java.time.LocalDateTime

data class Prison(
  val agencyId: String,
  val description: String,
  val longDescription: String?,
  val agencyType: String,
  val active: Boolean,
)

data class PrisonerCaseNoteByTypeSubType(
  val bookingId: Long,
  val caseNoteType: String,
  val caseNoteSubType: String,
  val numCaseNotes: Int,
)

data class CaseNoteUsageTypesRequest(
  val types: List<String>,
  val bookingFromDateSelection: List<BookingFromDatePair>,
)

data class BookingFromDatePair(
  val bookingId: Long,
  @param:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  val fromDate: LocalDateTime,
)

data class PrisonerInfo(
  override val bookingId: Long,
  val offenderNo: String,
  val agencyId: String,
  val firstName: String,
  val lastName: String,
) : PrisonerBasicInfo {
  override val prisonId = agencyId
  override val prisonerNumber = offenderNo
}

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
