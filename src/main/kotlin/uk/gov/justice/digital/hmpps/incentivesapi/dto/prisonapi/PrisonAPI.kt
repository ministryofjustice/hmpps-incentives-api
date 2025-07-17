package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import org.springframework.format.annotation.DateTimeFormat
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
