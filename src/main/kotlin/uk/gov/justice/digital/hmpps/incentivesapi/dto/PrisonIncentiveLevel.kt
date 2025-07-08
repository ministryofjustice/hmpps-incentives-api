package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException

/**
 * Publicly exposed representation of an incentive level’s configuration in a prison
 */
data class PrisonIncentiveLevel(
  @param:Schema(description = "The incentive level code this refers to", example = "STD", minLength = 1, maxLength = 6)
  @param:JsonProperty(required = true)
  val levelCode: String,
  @param:Schema(
    description = "The incentive level this refers to",
    example = "Standard",
    minLength = 1,
    readOnly = true,
  )
  @param:JsonProperty(required = false, access = JsonProperty.Access.READ_ONLY)
  val levelName: String = "",
  @param:Schema(description = "The prison this refers to", example = "MDI", minLength = 1, maxLength = 6)
  @param:JsonProperty(required = true)
  val prisonId: String,
  @param:Schema(
    description = "Indicates that this incentive level is enabled in this prison (true if not supplied)",
    example = "true",
  )
  @param:JsonProperty(required = false, defaultValue = "true")
  val active: Boolean = true,
  @param:Schema(
    description = "Indicates that this incentive level is the default for new admissions (false if not supplied)",
    example = "false",
  )
  @param:JsonProperty(required = false, defaultValue = "false")
  val defaultOnAdmission: Boolean = false,

  @param:Schema(
    description = "The amount transferred weekly from the private cash account to the spends account " +
      "for a remand prisoner to use",
    example = "5500",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val remandTransferLimitInPence: Int,
  @param:Schema(
    description = "The maximum amount allowed in the spends account for a remand prisoner",
    example = "55000",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val remandSpendLimitInPence: Int,
  @param:Schema(
    description = "The amount transferred weekly from the private cash account to the spends account " +
      "for a convicted prisoner to use",
    example = "1800",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val convictedTransferLimitInPence: Int,
  @param:Schema(
    description = "The maximum amount allowed in the spends account for a convicted prisoner",
    example = "18000",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val convictedSpendLimitInPence: Int,

  @param:Schema(
    description = "The number of weekday visits for a convicted prisoner per fortnight",
    example = "2",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val visitOrders: Int,
  @param:Schema(
    description = "The number of privileged/weekend visits for a convicted prisoner per 4 weeks",
    example = "1",
    minimum = "0",
    type = "integer",
    format = "int32",
  )
  @param:JsonProperty(required = true)
  val privilegedVisitOrders: Int,
) {
  fun toUpdate() = PrisonIncentiveLevelUpdate(
    active = active,
    defaultOnAdmission = defaultOnAdmission,
    remandTransferLimitInPence = remandTransferLimitInPence,
    remandSpendLimitInPence = remandSpendLimitInPence,
    convictedTransferLimitInPence = convictedTransferLimitInPence,
    convictedSpendLimitInPence = convictedSpendLimitInPence,
    visitOrders = visitOrders,
    privilegedVisitOrders = privilegedVisitOrders,
  )
}

fun List<PrisonIncentiveLevel>.findDefaultOnAdmission() = find(PrisonIncentiveLevel::defaultOnAdmission)
  ?: throw DataIntegrityException("No default level for new admissions")

/**
 * Update payload for PrisonIncentiveLevel
 */
data class PrisonIncentiveLevelUpdate(
  @param:Schema(
    description = "Indicates that this incentive level is enabled in this prison",
    example = "true",
    required = false,
  )
  val active: Boolean? = null,
  @param:Schema(
    description = "Indicates that this incentive level is the default for new admissions",
    example = "true",
    required = false,
  )
  val defaultOnAdmission: Boolean? = null,

  @param:Schema(
    description = "The amount transferred weekly from the private cash account to the spends account " +
      "for a remand prisoner to use",
    example = "5500",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val remandTransferLimitInPence: Int? = null,
  @param:Schema(
    description = "The maximum amount allowed in the spends account for a remand prisoner",
    example = "55000",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val remandSpendLimitInPence: Int? = null,
  @param:Schema(
    description = "The amount transferred weekly from the private cash account to the spends account " +
      "for a convicted prisoner to use",
    example = "1800",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val convictedTransferLimitInPence: Int? = null,
  @param:Schema(
    description = "The maximum amount allowed in the spends account for a convicted prisoner",
    example = "18000",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val convictedSpendLimitInPence: Int? = null,

  @param:Schema(
    description = "The number of weekday visits for a convicted prisoner per fortnight",
    example = "2",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val visitOrders: Int? = null,
  @param:Schema(
    description = "The number of privileged/weekend visits for a convicted prisoner per 4 weeks",
    example = "1",
    minimum = "0",
    type = "integer",
    format = "int32",
    required = false,
  )
  val privilegedVisitOrders: Int? = null,
)
