package uk.gov.justice.digital.hmpps.incentivesapi.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "What repairing a prisoner after a booking switch did, or would do in a dry run")
enum class BookingSwitchRepairOutcome {
  @Schema(description = "The prisoner's current incentive level was moved back onto the reinstated booking")
  REPAIRED,

  @Schema(description = "The prisoner's reviews were already correct, so nothing was changed")
  NOTHING_TO_DO,
}

@Schema(
  description = "Outcome of repairing a prisoner whose incentive level was left on a booking that NOMIS has " +
    "since switched away from",
)
data class BookingSwitchRepairResult(
  @param:Schema(description = "Prisoner number", example = "A1234BC")
  val prisonerNumber: String,

  @param:Schema(
    description = "The booking prisoner-search reports the prisoner is on — the one their level is reinstated to",
    example = "1234567",
  )
  val bookingId: Long,

  @param:Schema(description = "What was done")
  val outcome: BookingSwitchRepairOutcome,

  @param:Schema(description = "Whether this was a dry run, in which case nothing was written", example = "false")
  val dryRun: Boolean,

  @param:Schema(description = "The prisoner's current incentive level before the repair", example = "STD")
  val levelCodeBefore: String?,

  @param:Schema(
    description = "The prisoner's current incentive level after the repair; for a dry run, what it would become",
    example = "ENH",
  )
  val levelCodeAfter: String?,

  @param:Schema(
    description = "Ids of the reviews on the mistaken booking that are no longer current",
    example = "[2345]",
  )
  val reviewIdsStoodDown: List<Long>,

  @param:Schema(
    description = "Id of the review on the reinstated booking that was made current again, if any",
    example = "1234",
  )
  val reviewIdReinstated: Long?,

  @param:Schema(description = "Human-readable summary, useful when nothing was changed")
  val message: String,
)
