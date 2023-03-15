package uk.gov.justice.digital.hmpps.incentivesapi.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Publicly exposed representation of an incentive level
 */
data class IncentiveLevel(
  @Schema(description = "Unique id for the incentive level", example = "STD", minLength = 1, maxLength = 6)
  val code: String,
  @Schema(description = "Description of the incentive level", example = "Standard", minLength = 1)
  val description: String,
  // TODO: suggest not exposing `sequence`: lists are returned in proper order
  // @Schema(description = "Order in which to display the incentive levels", example = "1")
  // val sequence: Int,
  @Schema(description = "Indicates that the incentive level is active; inactive levels are historic levels no longer in use", example = "true", defaultValue = "true")
  val active: Boolean = true,

  // TODO: add `expiredOn` but • is this needed? • does NOMIS/prison-api reference data include expired date?
  // @Schema(description = "When present, indicates when the incentive level was made inactive", example = "2020-01-01")
  // val expiredOn: LocalDate,

  // TODO: add `required` flag to indicate that all prisons must use it
  // @Schema(description = "Indicates that all prisons must have this level active", example = "true", defaultValue = "false")
  // val required: Boolean = false,
)
