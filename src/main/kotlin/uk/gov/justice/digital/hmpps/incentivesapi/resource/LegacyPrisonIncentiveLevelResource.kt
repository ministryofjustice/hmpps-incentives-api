package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.LegacyPrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonIncentiveLevelAuditedService

@RestController
@RequestMapping("/iep", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "[Legacy] Prison incentive levels", description = "Incentive levels available in prisons")
@Deprecated("Use `/incentive/prison-levels/…`")
class LegacyPrisonIncentiveLevelResource(
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService,
) {
  @Deprecated("Use `/incentive/prison-levels/{prisonId}`")
  @GetMapping("/levels/{prisonId}")
  @Operation(
    summary = "Lists active incentive levels in this prison",
    description = "Not all globally active incentive levels will necessarily be included",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Active prison incentive levels returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to use this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prison incentive level not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getPrisonIncentiveLevels(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
  ): List<LegacyPrisonIncentiveLevel> {
    return prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId).mapIndexed { index, prisonIncentiveLevel ->
      LegacyPrisonIncentiveLevel(
        iepLevel = prisonIncentiveLevel.levelCode,
        iepDescription = prisonIncentiveLevel.levelName,
        sequence = index + 1,
        default = prisonIncentiveLevel.defaultOnAdmission,
        active = prisonIncentiveLevel.active,
      )
    }
  }
}
