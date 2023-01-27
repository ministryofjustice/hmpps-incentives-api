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
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveSummaryService
import uk.gov.justice.digital.hmpps.incentivesapi.service.SortColumn
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/incentives-summary", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_INCENTIVES')")
@Tag(name = "Incentive Review Summary", description = "List of incentive review information for a given location within a prison and on a given level")
@Deprecated("Use `/incentives-reviews`")
class IncentiveSummaryResource(private val incentiveSummaryService: IncentiveSummaryService) {
  @GetMapping("/prison/{prisonId}/location/{locationId}")
  @Deprecated("Deprecated endpoint for incentive review summary")
  @Operation(
    summary = "[Deprecated] Summaries IEP Incentive information at a specific location within a prison",
    description = "Location should be a Wing, Landing or Cell",
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
    @PathVariable
    prisonId: String,

    @Schema(description = "Location Id", required = true, example = "MDI-1", minLength = 5)
    @PathVariable
    locationId: String,

    @Schema(
      description = "Sort By", required = false, defaultValue = "NAME", example = "NAME",
      allowableValues = ["NUMBER", "NAME", "DAYS_ON_LEVEL", "POS_BEHAVIOURS", "NEG_BEHAVIOURS", "DAYS_SINCE_LAST_REVIEW", "INCENTIVE_WARNINGS", "INCENTIVE_ENCOURAGEMENTS", "PROVEN_ADJUDICATIONS"],
    )
    @RequestParam(required = false, defaultValue = "NAME")
    sortBy: SortColumn,

    @Schema(description = "Sort Direction", required = false, defaultValue = "ASC", example = "ASC")
    @RequestParam(required = false, defaultValue = "ASC")
    sortDirection: Sort.Direction
  ): BehaviourSummary {
    ensure {
      ("prisonId" to prisonId).hasLengthAtLeast(3).hasLengthAtMost(5)
      ("locationId" to locationId).hasLengthAtLeast(5)
    }

    return incentiveSummaryService.getIncentivesSummaryByLocation(prisonId, locationId, sortBy, sortDirection)
  }
}
