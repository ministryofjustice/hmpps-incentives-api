package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Publicly exposed representation of an incentive level in a prison
 * NB: this is the legacy form as previously returned by prison-api
 */
@Deprecated("Only used in deprecated resource endpoint")
data class LegacyPrisonIncentiveLevel(
  @Schema(description = "Code for this incentive level level", example = "STD")
  val iepLevel: String,
  @Schema(description = "The name of this incentive level", example = "Standard")
  val iepDescription: String,
  @Schema(description = "Order in which to sort and display incentive levels", example = "1")
  val sequence: Int,
  @Schema(description = "Indicates that this incentive level is the default for new admissions", example = "false", defaultValue = "false")
  @JsonAlias("defaultLevel")
  @JsonProperty
  val default: Boolean,
  @Schema(description = "Indicates that this incentive level is enabled in this prison", example = "true", defaultValue = "true")
  val active: Boolean,
)
