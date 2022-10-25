package uk.gov.justice.digital.hmpps.incentivesapi.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Incentive review information for a prisoner")
data class IncentiveReview(
  @Schema(description = "Prisoner number", example = "A1234BC")
  val prisonerNumber: String,
  @Schema(description = "Internal reference for a period in prison", example = "1234567")
  val bookingId: Long,

  @Schema(description = "Prisoner’s first name", example = "John")
  val firstName: String,
  @Schema(description = "Prisoner’s last name", example = "Smith")
  val lastName: String,

  @Schema(description = "Whether the prisoner has an ACCT open alert", example = "true")
  val acctOpenStatus: Boolean,
)

@Schema(description = "Incentive reviews list for prisoners at a given location")
data class IncentiveReviewResponse(
  @Schema(description = "Prisoner incentive reviews")
  val reviews: List<IncentiveReview>,

  @Schema(description = "Total number of reviews at given location", example = "102")
  val reviewCount: Int,

  @Schema(description = "Description of given location", example = "Houseblock 1")
  val locationDescription: String,
)
