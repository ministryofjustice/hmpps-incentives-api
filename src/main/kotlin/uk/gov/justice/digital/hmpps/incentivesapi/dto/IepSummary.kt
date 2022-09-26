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
  @Schema(description = "Unique ID for this review (new Incentives data model only)", example = "1000", required = false)
  val id: Long? = null,
  @Schema(description = "IEP Level", example = "Standard", required = true)
  val iepLevel: String,
  @Schema(description = "Prisoner number (NOMS)", example = "A1234DB", required = false)
  val prisonerNumber: String? = null,
  @Schema(description = "Booking ID", example = "1231232", required = true)
  val bookingId: Long,
  @Schema(description = "Date when last review took place", example = "2022-08-12", required = true)
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true)
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
  @Schema(description = "Unique ID for this review (new Incentives data model only)", example = "1000", required = false)
  val id: Long? = null,
  @Schema(description = "IEP Level", example = "Standard", required = true)
  val iepLevel: String,
  @Schema(description = "Review comments", example = "A review took place", required = false)
  val comments: String? = null,
  @Schema(description = "Prisoner number (NOMS)", example = "A1234DB", required = false)
  val prisonerNumber: String? = null,
  @Schema(description = "Booking ID", example = "1231232", required = true)
  val bookingId: Long,
  @Schema(description = "Date when last review took place", example = "2022-08-12", required = true)
  val iepDate: LocalDate,
  @Schema(description = "Date and time when last review took place", required = true)
  val iepTime: LocalDateTime,
  @Schema(description = "Prison ID", example = "MDI", required = true)
  val agencyId: String,
  @Schema(description = "Location  of prisoner when review took place within prison (i.e. their cell)", example = "1-2-003", required = false)
  val locationId: String? = null,
  @Schema(description = "Username of the reviewer", example = "AJONES", required = true)
  val userId: String?,
  @Schema(description = "Type of IEP Level change", example = "REVIEW", required = true)
  val reviewType: ReviewType? = null,
  @Schema(description = "Internal audit field holding which system/screen recorded the review", example = "Prison-API", required = true)
  val auditModuleName: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Current IEP Level")
data class CurrentIepLevel(
  @Schema(description = "Booking ID", example = "1231232", required = true)
  val bookingId: Long,
  @Schema(description = "IEP Level", example = "Standard", required = true)
  val iepLevel: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to add a new IEP Review")
data class IepReview(
  @Schema(description = "IEP Level", example = "STD", required = true, allowableValues = ["STD", "BAS", "ENH", "EN2", "ENT"])
  @field:Size(
    max = 6,
    min = 2,
    message = "IEP Level must be between 2 and 6"
  ) @field:NotBlank(message = "IEP Level is required") val iepLevel: String,
  @Schema(description = "Comment about review", example = "This is a comment", required = true)
  @field:NotBlank(message = "Comment on IEP Level review is required")
  val comment: String,
  @Schema(description = "Review Type", example = "Type of review", required = false, defaultValue = "REVIEW")
  val reviewType: ReviewType? = ReviewType.REVIEW,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to synchronise an IEP Review from NOMIS")
data class SyncPostRequest(
  @Schema(description = "Date and time when the review took place", required = true)
  val iepTime: LocalDateTime,

  @Schema(description = "Prison ID", example = "MDI", required = true)
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

  @Schema(description = "Comment about review", example = "This is a comment", required = true)
  @field:NotBlank(message = "Comment is required")
  val comment: String,

  @Schema(description = "NOMIS User Id who performed the review", example = "XYZ_GEN", required = false)
  val userId: String?,

  @Schema(description = "Review Type", example = "Type of review", required = true)
  val reviewType: ReviewType,

  @Schema(description = "Flag to indicate this is the current review for the prisoner", example = "true", required = true)
  val current: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Patch request to synchronise an IEP Review from NOMIS")
data class SyncPatchRequest(
  @Schema(description = "Date and time when the review took place", required = false)
  val iepTime: LocalDateTime?,

  @Schema(description = "Comment about review", example = "This is a comment", required = false)
  val comment: String?,

  @Schema(description = "Flag to indicate this is the current review for the prisoner", example = "true", required = false)
  val current: Boolean?,
)
