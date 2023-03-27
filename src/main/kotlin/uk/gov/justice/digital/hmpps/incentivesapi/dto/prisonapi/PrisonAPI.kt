package uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerInfoForNextReviewDate
import java.time.LocalDate
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

data class Location(
  val agencyId: String,
  val locationId: Long,
  val description: String,
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
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  val fromDate: LocalDateTime,
)

data class ProvenAdjudication(
  val bookingId: Long,
  val provenAdjudicationCount: Int,
)

// TODO: this does too many things, split into: • global level as defined in NOMIS reference data • per-prison configured level
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
  val active: Boolean = true,
  // TODO: the version for NOMIS reference data should include expired date?
)

data class PrisonLocation(
  val agencyId: String,
  val locationPrefix: String,
  val description: String,
  val locationType: String,
  val userDescription: String?,
)

data class ReferenceCode(
  val domain: String,
  val code: String,
  val description: String,
  val activeFlag: String,
  val listSeq: Int,
  // TODO: does NOMIS/prison-api reference data include expired date?
)
