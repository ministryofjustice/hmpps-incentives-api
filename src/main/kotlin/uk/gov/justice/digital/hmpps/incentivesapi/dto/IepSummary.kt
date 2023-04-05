package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "IEP Review Summary for Prisoner")
data class IepSummary(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = true, example = "12345")
  val id: Long,
  @Schema(description = "IEP Code", example = "STD", required = true)
  val iepCode: String,
  @Schema(description = "IEP Level", example = "Standard", required = true)
  val iepLevel: String,
  @Schema(description = "Prisoner number (NOMS)", required = true, example = "A1234BC")
  val prisonerNumber: String,
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "Date when last review took place", required = true, example = "2021-12-31")
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime,
  @Schema(description = "Location  of prisoner when review took place within prison (i.e. their cell)", example = "1-2-003", required = false)
  val locationId: String? = null,
  @Schema(description = "IEP Review History (descending in time)", required = true)
  var iepDetails: List<IepDetail>,
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
@Schema(description = "Detail IEP review details")
data class IepDetail(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = true, example = "12345")
  val id: Long,
  @Schema(description = "IEP Level", required = true, example = "Standard")
  val iepLevel: String,
  @Schema(description = "IEP Code", required = true, example = "STD")
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
  @Schema(description = "Location  of prisoner when review took place within prison (i.e. their cell)", required = false, example = "1-2-003")
  val locationId: String? = null,
  @Schema(description = "Username of the reviewer", required = true, example = "USER_1_GEN")
  val userId: String?,
  @Schema(description = "Type of IEP Level change", required = true, example = "REVIEW")
  override val reviewType: ReviewType,
  @Schema(description = "Internal audit field holding which system/screen recorded the review", required = true, example = SYSTEM_USERNAME)
  val auditModuleName: String = SYSTEM_USERNAME,
) : IsRealReview

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Current IEP Level")
data class CurrentIepLevel(
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "IEP Level", required = true, example = "Standard")
  val iepLevel: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to add a new IEP Review")
data class IepReview(
  @Schema(
    description = "IEP Level",
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
) {
  init {
    ensure {
      ("iepLevel" to iepLevel).hasLengthAtLeast(2).hasLengthAtMost(6)
      ("comment" to comment).hasLengthAtLeast(1)
    }
  }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Patch request to synchronise an IEP Review from NOMIS")
data class SyncPatchRequest(
  @Schema(description = "Date and time when the review took place", required = false, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime?,

  @Schema(description = "Comment about review", required = false, example = "A review took place")
  val comment: String?,

  @Schema(description = "Flag to indicate this is the current review for the prisoner", example = "true", required = false)
  val current: Boolean?,
)
