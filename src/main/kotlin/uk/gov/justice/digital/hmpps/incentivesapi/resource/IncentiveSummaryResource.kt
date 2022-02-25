package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.data.BehaviourSummary
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
        description = "Incentive Information returned",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = BehaviourSummary::class))]
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
    @Schema(description = "Prison Id", example = "MDI", required = true)
    @PathVariable @Size(max = 3, min = 3, message = "Prison ID must be 3 characters") prisonId: String,
    @Schema(description = "Location Id", example = "MDI-1", required = true)
    @PathVariable locationId: String,
    @Schema(description = "Sort By", example = "NAME", required = false, defaultValue = "NAME")
    @RequestParam(required = false, defaultValue = "NAME") sortBy: SortColumn,
    @Schema(description = "Sort Direction", example = "ASC", required = false, defaultValue = "ASC")
    @RequestParam(required = false, defaultValue = "ASC") sortDirection: Sort.Direction,
    @Parameter(hidden = true) @Schema(accessMode = Schema.AccessMode.READ_ONLY, hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String
  ): BehaviourSummary =
    incentiveSummaryService.getIncentivesSummaryByLocation(prisonId, locationId, sortBy, sortDirection)
}
