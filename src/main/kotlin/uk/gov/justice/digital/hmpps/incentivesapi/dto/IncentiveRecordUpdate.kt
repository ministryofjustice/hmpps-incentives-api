package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to update an Incentive record")
@Suppress("ktlint:standard:no-blank-line-in-list")
data class IncentiveRecordUpdate(
  @Schema(description = "Updated date and time of the Incentive record", required = false, example = "2021-12-31T12:34:56.789012")
  val reviewTime: LocalDateTime?,

  @Schema(description = "Updated comment", required = false, example = "A review took place")
  val comment: String?,

  @Schema(description = "Flag to indicate this is the current Incentive record for the prisoner", example = "true", required = false)
  val current: Boolean?,
)
