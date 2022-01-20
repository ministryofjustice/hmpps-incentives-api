package uk.gov.justice.digital.hmpps.incentivesapi.service

import java.time.LocalDate
import java.time.LocalDateTime

data class PrisonerAtLocation(
  val bookingId: Long,
  val facialImageId: Long,
  val firstName: String,
  val iepLevel: String,
  val lastName: String,
  val offenderNo: String,
)

data class IepSummary(
  val bookingId: Long,
  val daysSinceReview: Int,
  val iepDate: LocalDate,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val iepDetails: List<IepDetail>
)

data class IepDetail(

  val agencyId: String,
  val iepDate: LocalDate,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val userId: String
)

data class CaseNoteUsage(
  val offenderNo: String,
  val caseNoteType: String,
  val caseNoteSubType: String,
  val numCaseNotes: Int,
  val latestCaseNote: LocalDateTime?
)

data class CaseNoteUsageRequest(
  val numMonths: Int = 1,
  val offenderNos: List<String>,
  val type: String,
  val subType: String?
)

data class ProvenAdjudication(
  val bookingId: Long,
  val provenAdjudicationCount: Int
)
