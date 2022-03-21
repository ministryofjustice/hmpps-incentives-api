package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevelService
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerIepLevelReviewService
import javax.validation.constraints.Size

@RestController
@RequestMapping("/iep", produces = [MediaType.APPLICATION_JSON_VALUE])
class IepLevelResource(
  private val iepLevelService: IepLevelService,
  private val prisonerIepLevelReviewService: PrisonerIepLevelReviewService
) {
  @GetMapping("/levels/{prisonId}")
  @Operation(
    summary = "Returns the valid IEP levels for specified prison",
    description = "prison ID should be a 3 character string e.g. MDI = Moorland",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level Information returned"
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

  @GetMapping("/reviews/booking/{bookingId}")
  @Operation(
    summary = "Returns a history of IEP reviews for a prisoner",
    description = "Booking ID is an internal ID for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level History Information returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level History",
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
  suspend fun getPrisonerIepLevelHistory(
    @Schema(description = "Booking Id", example = "2342342", required = true)
    @PathVariable bookingId: Long,
    @Schema(description = "Use NOMIS data", example = "true", required = false, defaultValue = "true", hidden = true)
    @RequestParam(defaultValue = "true", value = "use-nomis-data", required = false) useNomisData: Boolean = true
  ): IepSummary =
    prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId, useNomisData)

  @GetMapping("/reviews/prisoner/{prisonerNumber}")
  @Operation(
    summary = "Returns a history of IEP reviews for a prisoner",
    description = "Prisoner Number is an unique reference for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level History Information returned"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level History",
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
  suspend fun getPrisonerIepLevelHistory(
    @Schema(description = "Prisoner Number", example = "A1234AB", required = true)
    @PathVariable prisonerNumber: String,
    @Schema(description = "Use NOMIS data", example = "true", required = false, defaultValue = "true", hidden = true)
    @RequestParam(defaultValue = "true", value = "use-nomis-data", required = false) useNomisData: Boolean = true
  ): IepSummary =
    prisonerIepLevelReviewService.getPrisonerIepLevelHistory(prisonerNumber, useNomisData)

  @PostMapping("/reviews/booking/{bookingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Adds a new IEP Review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "IEP Review Added"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to add new IEP review",
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
  suspend fun addIepReview(
    @Schema(description = "Booking Id", example = "2342342", required = true)
    @PathVariable bookingId: Long,
    @Schema(description = "IEP Review", required = true)
    @RequestBody iepReview: IepReview,

  ) = prisonerIepLevelReviewService.addIepReview(bookingId, iepReview)

  @PostMapping("/reviews/prisoner/{prisonerNumber}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Adds a new IEP Review for this specific prisoner by prisoner number",
    description = "Prisoner Number is an unique reference for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "IEP Review Added"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to add new IEP review",
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
  suspend fun addIepReview(
    @Schema(description = "Prisoner Number", example = "A1234AB", required = true)
    @PathVariable prisonerNumber: String,
    @Schema(description = "IEP Review", required = true)
    @RequestBody iepReview: IepReview,

  ) = prisonerIepLevelReviewService.addIepReview(prisonerNumber, iepReview)
}
