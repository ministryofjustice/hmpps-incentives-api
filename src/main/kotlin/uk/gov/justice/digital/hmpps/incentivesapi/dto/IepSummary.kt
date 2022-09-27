package uk.gov.justice.digital.hmpps.incentivesapi.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "IEP Review Summary for Prisoner")
data class IepSummary(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = false, example = "12345")
  val id: Long? = null,
  @Schema(description = "IEP Level", example = "Standard", required = true)
  val iepLevel: String,
  @Schema(description = "Prisoner number (NOMS)", required = false, example = "A1234BC")
  val prisonerNumber: String? = null,
  @Schema(description = "Booking ID", required = true, example = "1234567")
  val bookingId: Long,
  @Schema(description = "Date when last review took place", required = true, example = "2021-12-31")
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime,
  @Schema(description = "Days Since last Review", example = "23", required = true)
  val daysSinceReview: Int,
  @Schema(description = "Location  of prisoner when review took place within prison (i.e. their cell)", example = "1-2-003", required = false)
  val locationId: String? = null,
  @Schema(description = "IEP Review History (descending in time)", required = true)
  val iepDetails: List<IepDetail>,
) {

  fun daysSinceReview(): Int {
    return daysSinceReview(iepDetails)
  }

  fun daysOnLevel(): Int {
    val currentIepDate = LocalDate.now().atStartOfDay()
    var daysOnLevel = 0

    run iepCheck@{
      iepDetails.forEach {
        if (it.iepLevel != iepLevel) {
          return@iepCheck
        }
        daysOnLevel = Duration.between(it.iepDate.atStartOfDay(), currentIepDate).toDays().toInt()
      }
    }

    return daysOnLevel
  }
}

fun daysSinceReview(iepHistory: List<IepDetail>): Int {
  val currentIepDate = LocalDate.now().atStartOfDay()
  return Duration.between(iepHistory.first().iepDate.atStartOfDay(), currentIepDate).toDays().toInt()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detail IEP review details")
data class IepDetail(
  @Schema(description = "Unique ID for this review (new Incentives data model only)", required = false, example = "12345")
  val id: Long? = null,
  @Schema(description = "IEP Level", required = true, example = "Standard")
  val iepLevel: String,
  @Schema(description = "Review comments", required = false, example = "A review took place")
  val comments: String? = null,
  @Schema(description = "Prisoner number (NOMS)", required = false, example = "A1234BC")
  val prisonerNumber: String? = null,
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
  val reviewType: ReviewType? = null,
  @Schema(description = "Internal audit field holding which system/screen recorded the review", required = true, example = "Incentives-API")
  val auditModuleName: String? = null,
)

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
    allowableValues = ["STD", "BAS", "ENH", "EN2", "ENT"],
    example = "STD",
  )
  @field:Size(
    max = 6,
    min = 2,
    message = "IEP Level must be between 2 and 6"
  ) @field:NotBlank(message = "IEP Level is required") val iepLevel: String,
  @Schema(description = "Comment about review", required = true, example = "A review took place")
  @field:NotBlank(message = "Comment on IEP Level review is required")
  val comment: String,
  @Schema(description = "Review Type", example = "REVIEW", required = false, defaultValue = "REVIEW")
  val reviewType: ReviewType? = ReviewType.REVIEW,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to synchronise an IEP Review from NOMIS")
data class SyncPostRequest(
  @Schema(description = "Date and time when the review took place", required = true, example = "2021-12-31T12:34:56.789012")
  val iepTime: LocalDateTime,

  @Schema(description = "Prison ID", required = true, example = "MDI")
  @field:NotBlank(message = "Prison ID is required")
  val prisonId: String,

  @Schema(description = "Location of prisoner when review took place within prison (i.e. their cell)", example = "1-2-003", required = true)
  @field:NotBlank(message = "Location ID is required")
  val locationId: String,

  @Schema(description = "IEP Level", example = "STD", required = true, allowableValues = ["STD", "BAS", "ENH", "EN2", "EN3", "ENT"])
  @field:Size(
    max = 6,
    min = 2,
    message = "IEP Level must be between 2 and 6"
  ) @field:NotBlank(message = "IEP Level is required") val iepLevel: String,

  @Schema(description = "Comment about review", required = true, example = "A review took place")
  @field:NotBlank(message = "Comment is required")
  val comment: String,

  @Schema(description = "NOMIS User Id who performed the review", example = "USER_1_GEN", required = false)
  val userId: String?,

  @Schema(description = "Review Type", example = "REVIEW", required = true)
  val reviewType: ReviewType,

  @Schema(description = "Flag to indicate this is the current review for the prisoner", example = "true", required = true)
  val current: Boolean,
)

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
