package uk.gov.justice.digital.hmpps.incentivesapi.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

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

  @Schema(description = "Prisoner’s incentive level code", example = "STD")
  val levelCode: String,

  @Schema(description = "Count of all the positive case note behaviour entries recorded in the last 3 months", example = "7")
  val positiveBehaviours: Int,
  @Schema(description = "Count of all the negative case note behaviour entries recorded in the last 3 months", example = "7")
  val negativeBehaviours: Int,

  @Schema(description = "Whether the prisoner has an ACCT open alert", example = "true")
  val hasAcctOpen: Boolean,

  @Schema(description = "Date of next review", example = "2022-12-31")
  var nextReviewDate: LocalDate,
)

@Schema(description = "Incentive reviews list for prisoners at a given location")
data class IncentiveReviewResponse(
  @Schema(description = "Prisoner incentive reviews")
  val reviews: List<IncentiveReview>,

  @Schema(description = "Total number of reviews at given location", example = "102")
  val reviewCount: Int,

  @Schema(description = "Total number of overdue prisoner reviews at given location", example = "102")
  val overdueCount: Int,

  @Schema(description = "Total number of prisoners by incentive level", example = "{\"BAS\": 10, \"STD\": 72, \"ENH\": 20}")
  val prisonersCounts: Map<String, Int>,

  @Schema(description = "Total number of overdue prisoner reviews by incentive level", example = "{\"BAS\": 1, \"STD\": 10, \"ENH\": 0}")
  val overdueCounts: Map<String, Int>,

  @Schema(description = "Description of given location", example = "Houseblock 1")
  val locationDescription: String,
)
