package uk.gov.justice.digital.hmpps.incentivesapi.dto.casenoteapi

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

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
