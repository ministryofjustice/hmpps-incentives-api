package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevelUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonIncentiveLevelService
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/incentive/prison-levels", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Prison incentive levels", description = "Incentive levels and their per-prison associated information")
class PrisonIncentiveLevelResource(
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService,
) {
  @GetMapping("{prisonId}")
  @Operation(
    summary = "Lists active incentive levels in this prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison incentive levels returned",
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
  suspend fun getPrisonIncentiveLevels(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
  ): List<PrisonIncentiveLevel> {
    return prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)
  }

  @GetMapping("{prisonId}/level/{levelCode}")
  @Operation(
    summary = "Returns an active incentive level in this prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison incentive level returned",
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
        description = "Active prison incentive level not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getPrisonIncentiveLevel(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    levelCode: String,
  ): PrisonIncentiveLevel {
    return prisonIncentiveLevelService.getActivePrisonIncentiveLevel(prisonId, levelCode)
      ?: throw NoDataWithCodeFoundException("active prison incentive level", levelCode)
  }

  @PutMapping("{prisonId}/level/{levelCode}")
  @PreAuthorize("hasRole('MAINTAIN_PRISON_IEP_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Updates prison incentive level information",
    description = "Payload must include all required fields",
    // TODO: decide and explain what happens to prisoners if level is deactivated
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison incentive level updated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid payload", // TODO: maybe also when deactivating and there are prisoners on level?
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
      ApiResponse(
        responseCode = "404",
        description = "Incentive level not found globally", // TODO: ensure prison exists?
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun updatePrisonIncentiveLevel(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    levelCode: String,
    @RequestBody
    prisonIncentiveLevel: PrisonIncentiveLevel,
  ): PrisonIncentiveLevel {
    ensure {
      if (prisonId != prisonIncentiveLevel.prisonId) {
        errors.add("Prison ids must match in URL and payload")
      }
      if (levelCode != prisonIncentiveLevel.levelCode) {
        errors.add("Incentive level codes must match in URL and payload")
      }
    }
    return partiallyUpdatePrisonIncentiveLevel(prisonId, levelCode, prisonIncentiveLevel.toUpdate())
  }

  @PatchMapping("{prisonId}/level/{levelCode}")
  @PreAuthorize("hasRole('MAINTAIN_PRISON_IEP_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Updates prison incentive level information",
    description = "Partial updates are allowed",
    // TODO: decide and explain what happens to prisoners if level is deactivated
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison incentive level updated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid payload", // TODO: maybe also when deactivating and there are prisoners on level?
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
      ApiResponse(
        responseCode = "404",
        description = "Incentive level not found globally", // TODO: ensure prison exists?
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun partiallyUpdatePrisonIncentiveLevel(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    levelCode: String,
    @RequestBody
    update: PrisonIncentiveLevelUpdate,
  ): PrisonIncentiveLevel {
    ensure {
      update.active?.let { active ->
        update.defaultOnAdmission?.let { defaultOnAdmission ->
          if (!active && defaultOnAdmission) {
            errors.add("A level cannot be made inactive and still be the default for admission")
          }
        }
      }

      update.remandTransferLimitInPence?.let {
        ("remandTransferLimitInPence" to it).isAtLeast(0)
      }
      update.remandSpendLimitInPence?.let {
        ("remandSpendLimitInPence" to it).isAtLeast(0)
      }
      update.convictedTransferLimitInPence?.let {
        ("convictedTransferLimitInPence" to it).isAtLeast(0)
      }
      update.convictedSpendLimitInPence?.let {
        ("convictedSpendLimitInPence" to it).isAtLeast(0)
      }

      update.visitOrders?.let {
        ("visitOrders" to it).isAtLeast(0)
      }
      update.privilegedVisitOrders?.let {
        ("privilegeVisitOrders" to it).isAtLeast(0)
      }
    }

    return prisonIncentiveLevelService.updatePrisonIncentiveLevel(prisonId, levelCode, update)
      ?: throw NoDataWithCodeFoundException("incentive level", levelCode)
  }

  @DeleteMapping("{prisonId}/level/{levelCode}")
  @PreAuthorize("hasRole('MAINTAIN_PRISON_IEP_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Deactivate an incentive level for a prison",
    // TODO: decide and explain what happens to prisoners if level is deactivated
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prison incentive level deactivated",
      ),
      // ApiResponse(
      //   responseCode = "400",
      //   description = "There are prisoners on this incentive level at this prison",
      //   content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      // ),
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
        description = "Incentive level not found globally", // TODO: ensure prison exists?
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun deactivatePrisonIncentiveLevel(
    @Schema(description = "Prison id", example = "MDI", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    prisonId: String,
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    levelCode: String,
  ): PrisonIncentiveLevel {
    return partiallyUpdatePrisonIncentiveLevel(prisonId, levelCode, PrisonIncentiveLevelUpdate(active = false))
  }
}