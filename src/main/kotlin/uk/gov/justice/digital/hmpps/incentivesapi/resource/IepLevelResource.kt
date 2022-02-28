package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevelService
import javax.validation.constraints.Size

@RestController
@RequestMapping("/iep", produces = [MediaType.APPLICATION_JSON_VALUE])
class IepLevelResource(private val iepLevelService: IepLevelService) {
  @GetMapping("/levels/{prisonId}")
  @Operation(
    summary = "Returns the valid IEP levels for specified prison",
    description = "prison ID should be a 3 character string e.g. MDI = Moorland",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level Information returned",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = IepLevel::class)))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level data",
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
  suspend fun getPrisonIepLevels(
    @Schema(description = "Prison Id", example = "MDI", required = true)
    @PathVariable @Size(max = 3, min = 3, message = "Prison ID must be 3 characters") prisonId: String
  ): List<IepLevel> =
    iepLevelService.getIepLevelsForPrison(prisonId)
}
