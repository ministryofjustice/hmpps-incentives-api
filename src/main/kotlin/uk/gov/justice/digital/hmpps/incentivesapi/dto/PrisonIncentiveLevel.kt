package uk.gov.justice.digital.hmpps.incentivesapi.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Publicly exposed representation of an incentive level’s configuration in a prison
 */
data class PrisonIncentiveLevel(
  @Schema(description = "The incentive level this refers to", example = "STD", minLength = 1, maxLength = 6)
  val levelCode: String,
  @Schema(description = "The prison this refers to", example = "MDI", minLength = 1, maxLength = 6)
  val prisonId: String,
  @Schema(description = "Indicates that this incentive level is enabled in this prison", example = "true", defaultValue = "true")
  val active: Boolean = true,
  @Schema(description = "Indicates that this incentive level is the default for new admissions", example = "true", defaultValue = "false")
  val defaultOnAdmission: Boolean = false,

  @Schema(description = "The amount transferred weekly from the private cash account to the spends account for a remand prisoner to use", example = "5500", minimum = "0", type = "integer", format = "int32")
  val remandTransferLimitInPence: Int,
  @Schema(description = "The maximum amount allowed in the spends account for a remand prisoner", example = "55000", minimum = "0", type = "integer", format = "int32")
  val remandSpendLimitInPence: Int,
  @Schema(description = "The amount transferred weekly from the private cash account to the spends account for a convicted prisoner to use", example = "1800", minimum = "0", type = "integer", format = "int32")
  val convictedTransferLimitInPence: Int,
  @Schema(description = "The maximum amount allowed in the spends account for a convicted prisoner", example = "18000", minimum = "0", type = "integer", format = "int32")
  val convictedSpendLimitInPence: Int,

  @Schema(description = "The number of weekday visits for a convicted prisoner", example = "2", minimum = "0", type = "integer", format = "int32")
  val visitOrders: Int,
  @Schema(description = "The number of privileged/weekend visits for a convicted prisoner", example = "1", minimum = "0", type = "integer", format = "int32")
  val privilegedVisitOrders: Int,
)
