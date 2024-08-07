package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import org.springframework.format.annotation.DateTimeFormat
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

data class PrisonerInfo(
  val bookingId: Long,
  val facialImageId: Long,
  val firstName: String,
  val lastName: String,
  val offenderNo: String,
  val agencyId: String,
)

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

data class PrisonerExtraInfo(
  override val bookingId: Long,
  override val dateOfBirth: LocalDate,
  override val receptionDate: LocalDate,
  val offenderNo: String,
  val alerts: List<PrisonerAlert> = emptyList(),
) : PrisonerInfoForNextReviewDate {
  override val hasAcctOpen = alerts.any(PrisonerAlert::isOpenAcct)
  override val prisonerNumber = offenderNo
}

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
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  val fromDate: LocalDateTime,
)
