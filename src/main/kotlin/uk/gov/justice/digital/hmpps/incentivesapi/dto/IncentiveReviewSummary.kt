package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Incentive Review Summary for Prisoner")
data class IncentiveReviewSummary(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = true, example = "12345")
  val id: Long,
  @Schema(description = "Incentive Level Code", example = "STD", required = true)
  val iepCode: String,
  @Schema(description = "Incentive Level", example = "Standard", required = true)
  val iepLevel: String,
  @Schema(description = "Prisoner number (NOMS)", required = true, example = "A1234BC")
  val prisonerNumber: String,
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "Date when last review took place", required = true, example = "2021-12-31")
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime,
  @Schema(description = "DEPRECATED - Location of prisoner at the time when review was created in Incentives service (i.e. their cell)", example = "1-2-003", required = false, deprecated = true)
  val locationId: String? = null,
  @Schema(description = "Incentive Review History (descending in time)", required = true)
  @JsonProperty("iepDetails")
  var incentiveReviewDetails: List<IncentiveReviewDetail>,
  @Schema(description = "Date of next review", example = "2022-12-31", required = true)
  val nextReviewDate: LocalDate,
) {

  // NOTE: This is part of the public `GET /iep/reviews/` API response format.
  //       Ignore IntelliJ saying it's "not used".
  @get:Schema(description = "Days since last review", example = "23", required = true)
  @get:JsonProperty
  val daysSinceReview: Int
    get() {
      return daysSinceReviewCalc(Clock.systemDefaultZone())
    }

  @JsonIgnore
  fun daysSinceReviewCalc(clock: Clock): Int {
    val today = LocalDate.now(clock).atStartOfDay()
    return Duration.between(iepDate.atStartOfDay(), today).toDays().toInt()
  }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed incentive review details")
data class IncentiveReviewDetail(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = true, example = "12345")
  val id: Long,
  @Schema(description = "Incentive Level", required = true, example = "Standard")
  val iepLevel: String,
  @Schema(description = "Incentive Level Code", required = true, example = "STD")
  val iepCode: String,
  @Schema(description = "Review comments", required = false, example = "A review took place")
  val comments: String? = null,
  @Schema(description = "Prisoner number (NOMS)", required = true, example = "A1234BC")
  val prisonerNumber: String,
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "Date when last review took place", required = true, example = "2021-12-31")
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime,
  @Schema(description = "Prison ID", required = true, example = "MDI")
  val agencyId: String,
  @Schema(description = "DEPRECATED - Location of prisoner at the time when review was created in Incentives service (i.e. their cell)", required = false, example = "1-2-003", deprecated = true)
  val locationId: String? = null,
  @Schema(description = "Username of the reviewer", required = true, example = "USER_1_GEN")
  val userId: String?,
  @Schema(description = "Type of Incentive Level change", required = true, example = "REVIEW")
  override val reviewType: ReviewType,
  @Schema(description = "Internal audit field holding which system/screen recorded the review", required = true, example = SYSTEM_USERNAME)
  val auditModuleName: String = SYSTEM_USERNAME,
) : IsRealReview

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Current Incentive Level")
data class CurrentIncentiveLevel(
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "Incentive Level", required = true, example = "Standard")
  val iepLevel: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to add a new incentive review")
data class CreateIncentiveReviewRequest(
  @Schema(
    description = "Incentive Level",
    required = true,
    allowableValues = ["BAS", "STD", "ENH", "EN2", "EN3"],
    example = "STD",
    minLength = 2,
    maxLength = 6,
    nullable = false,
  )
  val iepLevel: String,

  @Schema(description = "Comment about review", required = true, example = "A review took place", minLength = 1)
  val comment: String,

  @Schema(description = "Review Type", example = "REVIEW", required = false, defaultValue = "REVIEW")
  val reviewType: ReviewType? = ReviewType.REVIEW,

  @Schema(description = "Date and time of the review, if not provided will default to today's time", required = false, example = "2023-06-07", defaultValue = "now")
  val reviewTime: LocalDateTime? = null,

  @Schema(description = "The username of the member of staff undertaking the review, if not provided will use the user in the JWT access token", required = false, example = "ASMITH", minLength = 1, maxLength = 30)
  @field:Size(min = 1, max = 30, message = "Reviewed by must be a maximum of 30 characters")
  val reviewedBy: String? = null,
) {
  init {
    ensure {
      ("iepLevel" to iepLevel).hasLengthAtLeast(2).hasLengthAtMost(6)
      ("comment" to comment).hasLengthAtLeast(1)
    }
  }
}
