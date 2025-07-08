package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Publicly exposed representation of an incentive level
 */
data class IncentiveLevel(
  @param:Schema(description = "Unique id for the incentive level", example = "STD", minLength = 1, maxLength = 6)
  @param:JsonProperty(required = true)
  val code: String,
  @param:Schema(description = "Name of the incentive level", example = "Standard", minLength = 1, maxLength = 30)
  @param:JsonProperty(required = true)
  val name: String,
  @param:Schema(
    description = "Indicates that the incentive level is active (true if not supplied); " +
      "inactive levels are historic levels no longer in use",
    example = "true",
  )
  @param:JsonProperty(required = false, defaultValue = "true")
  val active: Boolean = true,
  @param:Schema(
    description = "Indicates that all prisons must have this level active (false if not supplied)",
    example = "false",
  )
  @param:JsonProperty(required = false, defaultValue = "false")
  val required: Boolean = false,
) {
  companion object {
    const val BASIC_CODE = "BAS"
  }

  fun toUpdate() = IncentiveLevelUpdate(
    name = name,
    active = active,
    required = required,
  )
}

/**
 * Update payload for IncentiveLevel
 */
data class IncentiveLevelUpdate(
  @param:Schema(
    description = "Name of the incentive level",
    example = "Standard",
    minLength = 1,
    maxLength = 30,
    required = false,
  )
  val name: String? = null,
  @param:Schema(
    description = "Indicates that the incentive level is active; inactive levels are historic levels no longer in use",
    example = "true",
    required = false,
  )
  val active: Boolean? = null,
  @param:Schema(
    description = "Indicates that all prisons must have this level active",
    example = "true",
    required = false,
  )
  val required: Boolean? = null,
)
