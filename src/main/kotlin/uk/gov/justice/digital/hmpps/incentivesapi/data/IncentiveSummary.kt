package uk.gov.justice.digital.hmpps.incentivesapi.data

import io.swagger.v3.oas.annotations.media.Schema

data class IncentiveSummary(
  @Schema(description = "Prison Id", example = "WWI")
  val prisonId: String,
  @Schema(description = "Location within the prison", example = "A")
  val locationId: String,
  @Schema(description = "Location within the prison description", example = "A Wing")
  val locationDescription: String

)
