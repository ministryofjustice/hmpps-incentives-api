package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CurrentIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerIncentiveReviewService
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/iep", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Maintain incentive reviews (Deprecated)", description = "Retrieve and add incentive review records. Deprecated - Use /incentive-reviews instead.")
@Deprecated("Use /incentive-reviews instead")
class UnprotectedIncentiveReviewsResource(
  private val prisonerIncentiveReviewService: PrisonerIncentiveReviewService,
) {
  @GetMapping("/reviews/booking/{bookingId}")
  @Deprecated("Use /incentive-reviews/booking/{bookingId} instead")
  @Operation(
    summary = "Returns a history of IEP reviews for a prisoner",
    description = "Booking ID is an internal ID for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level History Information returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level History",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  suspend fun getPrisonerIepLevelHistory(
    @Schema(description = "Booking Id", example = "3000002", required = true, type = "integer", format = "int64", pattern = "^[0-9]{1,20}$")
    @PathVariable
    bookingId: Long,
    @Schema(description = "Toggle to return IEP detail entries in response (or not)", example = "true", required = false, defaultValue = "true", type = "boolean", pattern = "^true|false$")
    @RequestParam(defaultValue = "true", value = "with-details", required = false)
    withDetails: Boolean = true,
  ): IncentiveReviewSummary =
    prisonerIncentiveReviewService.getPrisonerIncentiveHistory(bookingId, withDetails)

  @GetMapping("/reviews/id/{id}")
  @Operation(
    summary = "Returns a specified IEP Review",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level Information returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level History",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  @Deprecated("Use /incentive-reviews/id/{id} instead")
  suspend fun getReviewById(
    @Schema(description = "Review ID (internal)", example = "1000", required = true, type = "integer", format = "int64", pattern = "^[0-9]{1,20}$")
    @PathVariable(value = "id", required = true)
    id: Long,
  ): IncentiveReviewDetail =
    prisonerIncentiveReviewService.getReviewById(id)

  @PostMapping("/reviews/bookings")
  @Operation(
    summary = "Returns a history of incentive reviews for a list of prisoners",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive Level Information returned per prisoner",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return incentive level history",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  @Deprecated("Use /incentive-reviews/bookings instead")
  suspend fun getCurrentIEPLevelForPrisoner(
    @ArraySchema(schema = Schema(description = "List of booking Ids", required = true, type = "array"), arraySchema = Schema(type = "integer", format = "int64", pattern = "^[0-9]{1,20}$", additionalProperties = Schema.AdditionalPropertiesValue.FALSE))
    @RequestBody
    bookingIds: List<Long>,
  ): List<CurrentIncentiveLevel> {
    ensure {
      ("bookingIds" to bookingIds).isNotEmpty()
    }
    return prisonerIncentiveReviewService.getCurrentIncentiveLevelForPrisoners(bookingIds)
  }

  @GetMapping("/reviews/prisoner/{prisonerNumber}")
  @Operation(
    summary = "Returns a history of IEP reviews for a prisoner",
    description = "Prisoner Number is an unique reference for a prisoner in NOMIS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level History Information returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to return IEP Level History",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  @Deprecated("Use /incentive-reviews/prisoner/{prisonerNumber} instead")
  suspend fun getPrisonerIepLevelHistory(
    @Schema(description = "Prisoner Number", example = "A1234AB", required = true, pattern = "^[A-Z0-9]{7}$")
    @PathVariable
    prisonerNumber: String,
  ): IncentiveReviewSummary =
    prisonerIncentiveReviewService.getPrisonerIncentiveHistory(prisonerNumber)

  @PostMapping("/reviews/booking/{bookingId}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Adds a new Incentive Review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Incentive Review Added",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to add new incentive review",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  suspend fun addIncentiveReview(
    @Schema(description = "Booking Id", example = "3000002", required = true, type = "integer", format = "int64", pattern = "^[0-9]{1,20}$")
    @PathVariable
    bookingId: Long,
    @Schema(
      description = "Incentive Review",
      required = true,
      implementation = CreateIncentiveReviewRequest::class,
    )
    @RequestBody
    createIncentiveReviewRequest: CreateIncentiveReviewRequest,
  ): IncentiveReviewDetail = prisonerIncentiveReviewService.addIncentiveReview(bookingId, createIncentiveReviewRequest)

  @PostMapping("/reviews/prisoner/{prisonerNumber}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Adds a new Incentive Review for this specific prisoner by prisoner number",
    description = "Prisoner Number is an unique reference for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Incentive Review Added",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to add new incentive review",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
    ],
  )
  suspend fun addIncentiveReview(
    @Schema(description = "Prisoner Number", example = "A1234AB", required = true, pattern = "^[A-Z0-9]{1,20}$")
    @PathVariable
    prisonerNumber: String,
    @Schema(
      description = "Incentive Review",
      required = true,
      implementation = CreateIncentiveReviewRequest::class,
    )
    @RequestBody
    createIncentiveReviewRequest: CreateIncentiveReviewRequest,
  ): IncentiveReviewDetail = prisonerIncentiveReviewService.addIncentiveReview(prisonerNumber, createIncentiveReviewRequest)
}
