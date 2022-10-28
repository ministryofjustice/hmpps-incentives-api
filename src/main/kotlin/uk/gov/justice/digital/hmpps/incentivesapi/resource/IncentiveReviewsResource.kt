package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewsService
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/incentives-reviews", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_INCENTIVES')")
class IncentiveReviewsResource(private val incentiveReviewsService: IncentiveReviewsService) {
  @GetMapping("/prison/{prisonId}/location/{cellLocationPrefix}")
  @Operation(
    summary = "List of incentive review information for a given location within a prison",
    description = "location should be a cell ID prefix like `MDI-1`",
    responses = [
      ApiResponse(responseCode = "200", description = "Reviews returned"),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request parameters",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Request does not have necessary permissions",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  suspend fun getReviews(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5)
    @PathVariable
    prisonId: String,

    @Schema(description = "Cell location ID prefix", required = true, example = "MDI-1", minLength = 5)
    @PathVariable
    cellLocationPrefix: String,

    @Schema(description = "Page (starts at 1)", defaultValue = "1", minimum = "1", example = "2", type = "integer", required = false, format = "int32")
    @RequestParam(required = false, defaultValue = "1")
    page: Int,

    @Schema(description = "Page size", defaultValue = "20", minimum = "1", maximum = "100", example = "20", type = "integer", required = false, format = "int32")
    @RequestParam(required = false, defaultValue = "20")
    pageSize: Int,
  ): IncentiveReviewResponse {
    ensure {
      ("prisonId" to prisonId).hasLengthAtLeast(3).hasLengthAtMost(5)
      ("cellLocationPrefix" to cellLocationPrefix).hasLengthAtLeast(5)
      ("page" to page).isAtLeast(1)
      ("pageSize" to pageSize).isAtLeast(1).isAtMost(100)
    }

    return incentiveReviewsService.reviews(prisonId, cellLocationPrefix, page, pageSize)
  }
}
