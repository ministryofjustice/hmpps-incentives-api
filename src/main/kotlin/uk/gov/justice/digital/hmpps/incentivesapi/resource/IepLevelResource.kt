package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CurrentIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPatchRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.service.AuditService
import uk.gov.justice.digital.hmpps.incentivesapi.service.AuditType
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.service.IepLevelService
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentivesDomainEventType
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerIepLevelReviewService
import uk.gov.justice.digital.hmpps.incentivesapi.service.SnsService
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

@RestController
@RequestMapping("/iep", produces = [MediaType.APPLICATION_JSON_VALUE])
class IepLevelResource(
  private val iepLevelService: IepLevelService,
  private val prisonerIepLevelReviewService: PrisonerIepLevelReviewService,
  private val snsService: SnsService,
  private val auditService: AuditService
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
    @RequestParam(defaultValue = "true", value = "use-nomis-data", required = false) useNomisData: Boolean = true,
    @Schema(description = "Toggle to return IEP detail entries in response (or not)", example = "true", required = false, defaultValue = "true")
    @RequestParam(defaultValue = "true", value = "with-details", required = false) withDetails: Boolean = true,
  ): IepSummary =
    prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId, useNomisData, withDetails)

  @GetMapping("/reviews/id/{id}")
  @Operation(
    summary = "Returns a specified IEP Review",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level Information returned"
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
  suspend fun getReviewById(
    @Schema(description = "Review ID (internal)", example = "1000", required = true)
    @PathVariable(value = "id", required = true) id: Long
  ): IepDetail =
    prisonerIepLevelReviewService.getReviewById(id)

  @PostMapping("/reviews/bookings")
  @Operation(
    summary = "Returns a history of IEP reviews for a list of prisoners",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Level Information returned per prisoner"
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
  suspend fun getCurrentIEPLevelForPrisoner(
    @Schema(description = "List of booking Ids", example = "[2342342, 212312]", required = true)
    @RequestBody @Valid @NotEmpty bookingIds: List<Long>,
    @Schema(description = "Use NOMIS data", example = "true", required = false, defaultValue = "true", hidden = true)
    @RequestParam(defaultValue = "true", value = "use-nomis-data", required = false) useNomisData: Boolean = true
  ): Flow<CurrentIepLevel> =
    prisonerIepLevelReviewService.getCurrentIEPLevelForPrisoners(bookingIds, useNomisData)

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
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Adds a new IEP Review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
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
    @RequestBody @Valid iepReview: IepReview,

  ): IepDetail {
    val iepDetail = prisonerIepLevelReviewService.addIepReview(bookingId, iepReview)
    sendEventAndAudit(iepDetail, IncentivesDomainEventType.IEP_REVIEW_INSERTED, AuditType.IEP_REVIEW_ADDED)
    return iepDetail
  }

  @PostMapping("/reviews/prisoner/{prisonerNumber}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Adds a new IEP Review for this specific prisoner by prisoner number",
    description = "Prisoner Number is an unique reference for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
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
    @RequestBody @Valid iepReview: IepReview,

  ): IepDetail {
    val iepDetail = prisonerIepLevelReviewService.addIepReview(prisonerNumber, iepReview)
    sendEventAndAudit(iepDetail, IncentivesDomainEventType.IEP_REVIEW_INSERTED, AuditType.IEP_REVIEW_ADDED)
    return iepDetail
  }

  @PostMapping("/migration/booking/{bookingId}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Migrates an IEP Review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "IEP Review Migrated"
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
  suspend fun migrateIepReview(
    @Schema(description = "Booking Id", example = "2342342", required = true)
    @PathVariable bookingId: Long,
    @Schema(description = "IEP Review", required = true)
    @RequestBody @Valid syncPostRequest: SyncPostRequest,
  ): IepDetail =
    prisonerIepLevelReviewService.persistPrisonerIepLevel(bookingId, syncPostRequest)

  @PostMapping("/sync/booking/{bookingId}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Synchronise (NOMIS -> Incentives) an IEP Review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS, requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "IEP Review Synchronised"
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
  suspend fun syncPostIepReview(
    @Schema(description = "Booking Id", example = "2342342", required = true)
    @PathVariable bookingId: Long,
    @Schema(description = "IEP Review", required = true)
    @RequestBody @Valid syncPostRequest: SyncPostRequest,
  ): IepDetail = prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest)

  @PatchMapping("/sync/booking/{bookingId}/id/{id}")
  @PreAuthorize("hasRole('MAINTAIN_IEP') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Update an existing IEP review for this specific prisoner by booking Id",
    description = "Booking ID is an internal ID for a prisoner in NOMIS, ID is the ID of the IEP review. Requires MAINTAIN_IEP role and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "IEP Review updated"
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect data specified to update the IEP review",
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
  suspend fun syncPatchIepReview(
    @Schema(description = "Booking Id", example = "2342342", required = true)
    @PathVariable bookingId: Long,
    @Schema(description = "ID", example = "12345", required = true)
    @PathVariable id: Long,
    @Schema(description = "IEP Review changes", required = true)
    @RequestBody @Valid syncPatchRequest: SyncPatchRequest,
  ): IepDetail = prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, id, syncPatchRequest)

  private suspend fun sendEventAndAudit(iepDetail: IepDetail, eventType: IncentivesDomainEventType, auditType: AuditType) {
    snsService.sendIepReviewEvent(iepDetail.id!!, iepDetail.iepTime, eventType)

    auditService.sendMessage(
      auditType,
      iepDetail.id.toString(),
      iepDetail
    )
  }
}
