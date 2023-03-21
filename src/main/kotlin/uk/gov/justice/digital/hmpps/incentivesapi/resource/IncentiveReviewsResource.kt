package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewSort
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewsService
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewsService.Companion.DEFAULT_PAGE_SIZE
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/incentives-reviews", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_INCENTIVES')")
@Tag(
  name = "Incentive Review Summary",
  description = "List of incentive review information for a given location within a prison and on a given level",
)
class IncentiveReviewsResource(private val incentiveReviewsService: IncentiveReviewsService) {
  @GetMapping("/prison/{prisonId}/location/{cellLocationPrefix}/level/{levelCode}")
  @Operation(
    summary = "List of incentive review information for a given location within a prison and on a given level",
    description = "Location should be a cell ID prefix like `MDI-1`",
    responses = [
      ApiResponse(responseCode = "200", description = "Reviews information returned"),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request parameters",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Request does not have necessary permissions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getReviews(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5)
    @PathVariable
    prisonId: String,

    @Schema(description = "Cell location ID prefix", required = true, example = "MDI-1", minLength = 5)
    @PathVariable
    cellLocationPrefix: String,

    @Schema(description = "Incentive level code", required = true, example = "STD", minLength = 2)
    @PathVariable
    levelCode: String,

    @Schema(
      description = "Sort reviews by",
      required = false,
      defaultValue = "NEXT_REVIEW_DATE",
      example = "PRISONER_NUMBER",
      allowableValues = [
        "NEXT_REVIEW_DATE", "DAYS_SINCE_LAST_REVIEW",
        "FIRST_NAME", "LAST_NAME", "PRISONER_NUMBER",
        "POSITIVE_BEHAVIOURS", "NEGATIVE_BEHAVIOURS",
        "HAS_ACCT_OPEN", "IS_NEW_TO_PRISON",
      ],
    )
    @RequestParam(required = false)
    sort: IncentiveReviewSort? = null,

    @Schema(description = "Sort direction", required = false, defaultValue = "ASC", example = "ASC", allowableValues = ["ASC", "DESC"])
    @RequestParam(required = false)
    order: Sort.Direction? = null,

    @Schema(description = "Page (starts at 0)", defaultValue = "0", minimum = "0", example = "2", type = "integer", required = false, format = "int32")
    @RequestParam(required = false, defaultValue = "0")
    page: Int = 0,

    @Schema(description = "Page size", defaultValue = "$DEFAULT_PAGE_SIZE", minimum = "1", maximum = "100", example = "20", type = "integer", required = false, format = "int32")
    @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE")
    pageSize: Int = DEFAULT_PAGE_SIZE,
  ): IncentiveReviewResponse {
    ensure {
      ("prisonId" to prisonId).hasLengthAtLeast(3).hasLengthAtMost(5)
      ("cellLocationPrefix" to cellLocationPrefix).hasLengthAtLeast(5)
      ("levelCode" to levelCode).hasLengthAtLeast(2)
      ("page" to page).isAtLeast(0)
      ("pageSize" to pageSize).isAtLeast(1).isAtMost(100)
    }

    return incentiveReviewsService.reviews(prisonId, cellLocationPrefix, levelCode, sort, order, page, pageSize)
  }
}
