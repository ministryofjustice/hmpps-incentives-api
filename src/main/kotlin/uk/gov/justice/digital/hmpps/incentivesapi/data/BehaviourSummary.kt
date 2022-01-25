package uk.gov.justice.digital.hmpps.incentivesapi.data

import io.swagger.v3.oas.annotations.media.Schema

data class BehaviourSummary(
  @Schema(description = "Prison Id", example = "WWI")
  val prisonId: String,
  @Schema(description = "Location within the prison", example = "A")
  val locationId: String,
  @Schema(description = "Location within the prison description", example = "A Wing")
  val locationDescription: String = "",

  @Schema(description = "Breakdown of behaviours at this location by IEP level")
  val incentiveLevelSummary: List<IncentiveLevelSummary> = listOf()
) {

  @Schema(
    description = "Total count of all the positive case note behaviour entries recorded for this location",
    example = "20"
  )
  fun getTotalPositiveBehaviours(): Int = incentiveLevelSummary.sumOf { it.prisonerBehaviours.sumOf { p -> p.positiveBehaviours } }
  @Schema(
    description = "Total count of all the negative case note behaviour entries recorded for this location",
    example = "35"
  )
  fun getTotalNegativeBehaviours(): Int = incentiveLevelSummary.sumOf { it.prisonerBehaviours.sumOf { p -> p.negativeBehaviours } }
  @Schema(
    description = "Total count of just the positive case note behaviour entries recorded of the sub type <em>Incentive Encouragements</em> for this location",
    example = "6"
  )
  fun getTotalIncentiveEncouragements(): Int = incentiveLevelSummary.sumOf { it.prisonerBehaviours.sumOf { p -> p.incentiveEncouragements } }
  @Schema(
    description = "Total count of just the negative case note behaviour entries recorded of the sub type  <em>Incentive Warning</em> for this location",
    example = "14"
  )
  fun getTotalIncentiveWarnings(): Int = incentiveLevelSummary.sumOf { it.prisonerBehaviours.sumOf { p -> p.incentiveWarnings } }
}

data class IncentiveLevelSummary(
  @Schema(description = "IEP Level", example = "Standard")
  val level: String,
  @Schema(description = "List of all prisoners at this location at this IEP level")
  val prisonerBehaviours: List<PrisonerIncentiveSummary> = listOf()
) {

  @Schema(description = "Number of prisoners at this IEP level", example = "70")
  fun getNumberAtThisLevel() = prisonerBehaviours.size
}

data class PrisonerIncentiveSummary(
  @Schema(description = "Prisoner Number - Often called NOMS Number/ID", example = "A1234DG")
  val prisonerNumber: String,
  @Schema(
    description = "Internal reference for a period in prison (Not to be confused with the BOOK or Book Number)",
    example = "2322121"
  )
  val bookingId: Long,
  @Schema(description = "Internal reference for looking up the prisoners latest photo", example = "112121")
  val imageId: Long,
  @Schema(description = "Prisoners First Name", example = "John")
  val firstName: String,
  @Schema(description = "Prisoners Last Name", example = "Smith")
  val lastName: String,
  @Schema(
    description = "Calculated attribute that determines the number of days the prisoner has been on their current IEP level for their current offender booking. Historical data for a prisoner IEP reviews is only available via the prison API on a per prisoner, prison or prison location (i.e Wing) basis.",
    example = "10"
  )
  val daysOnLevel: Int?,
  @Schema(
    description = "A simple calculation using the current date and calculating the number of elapsed days since the date of the prisoners last IEP review. <br/>Note: Assumption that if an IEP record exist in NOMIS then an IEP review has taken place",
    example = "50"
  )
  val daysSinceLastReview: Int,
  @Schema(description = "Count of all the positive case note behaviour entries recorded", example = "7")
  val positiveBehaviours: Int,
  @Schema(
    description = "Count of just the positive case note behaviour entries recorded of the sub type <em>Incentive Encouragements</em",
    example = "1"
  )
  val incentiveEncouragements: Int,
  @Schema(description = "Count of all the negative case note behaviour entries recorded", example = "5")
  val negativeBehaviours: Int,
  @Schema(
    description = "Count of just the negative case note behaviour entries recorded of the sub type  <em>Incentive Warning</em>",
    example = "2"
  )
  val incentiveWarnings: Int,
  @Schema(
    description = "A count of the proven adjudications for the offender at the current prison where the hearing result is <em>PROVEN</em>",
    example = "14"
  )
  val provenAdjudications: Int
)
