package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveSummaryService
import uk.gov.justice.digital.hmpps.incentivesapi.service.SortColumn
import javax.validation.constraints.Size

@RestController
@RequestMapping("/incentives-summary", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_INCENTIVES')")
class IncentiveSummaryResource(private val incentiveSummaryService: IncentiveSummaryService) {
  @GetMapping("/prison/{prisonId}/location/{locationId}")
  @Operation(
    summary = "Summaries IEP Incentive information at a specific location within a prison",
    description = "location should be a Wing, Landing or Cell",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive Information returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return incentive data",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to use this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  suspend fun getIncentiveSummary(
    @Schema(description = "Prison Id", required = true, example = "MDI", minLength = 3, maxLength = 5)
    @PathVariable @Size(max = 5, min = 3, message = "Prison ID must be 3-5 characters") prisonId: String,
    @Schema(description = "Location Id", required = true, example = "MDI-1")
    @PathVariable locationId: String,
    @Schema(description = "Sort By", required = false, defaultValue = "NAME", example = "NAME")
    @RequestParam(required = false, defaultValue = "NAME") sortBy: SortColumn,
    @Schema(description = "Sort Direction", required = false, defaultValue = "ASC", example = "ASC")
    @RequestParam(required = false, defaultValue = "ASC") sortDirection: Sort.Direction
  ): BehaviourSummary =
    incentiveSummaryService.getIncentivesSummaryByLocation(prisonId, locationId, sortBy, sortDirection)
}
