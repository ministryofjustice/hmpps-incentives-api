package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class PrisonerAtLocation(
  val bookingId: Long,
  val facialImageId: Long,
  val firstName: String,
  val lastName: String,
  val offenderNo: String,
  val agencyId: String,
  val assignedLivingUnitId: Long,
)

data class Location(
  val agencyId: String,
  val locationId: Long,
  val description: String,
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

data class IepLevel(
  @Schema(description = "IEP Code for an IEP level", example = "STD")
  val iepLevel: String,
  @Schema(description = "Description of the IEP Level", example = "Standard")
  val iepDescription: String,
  @Schema(description = "Sequence to display the IEP Levels for this prison in LOV or other tables", example = "1")
  val sequence: Int?,
  @Schema(description = "Indicates that this IEP level is the default for this prison", example = "true")
  @JsonAlias("defaultLevel")
  @JsonProperty
  val default: Boolean = false,
  @Schema(description = "Indicates that this IEP level is the active", example = "true")
  val active: Boolean = true
)

data class PrisonLocation(

  val agencyId: String,
  val locationPrefix: String,
  val description: String,
  val locationType: String,
  val userDescription: String?

) {
  fun getLocationDescription() = userDescription ?: description
}

data class IepReviewInNomis(
  val iepLevel: String,
  val comment: String,
  val reviewTime: LocalDateTime,
  val reviewerUserName: String,
)

data class IncentiveLevel(
  val domain: String,
  val code: String,
  val description: String,
  val activeFlag: String,
  val listSeq: Int,
)
