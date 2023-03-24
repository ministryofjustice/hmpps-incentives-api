package uk.gov.justice.digital.hmpps.incentivesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveLevelService
import uk.gov.justice.digital.hmpps.incentivesapi.util.ensure

@RestController
@RequestMapping("/incentive", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Incentive levels", description = "Incentive levels and their global associated information")
class IncentiveLevelResource(
  private val incentiveLevelService: IncentiveLevelService,
) {
  @GetMapping("levels")
  @Operation(
    summary = "Lists all incentive levels, optionally including inactive ones",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive levels returned",
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
  suspend fun getIncentiveLevels(
    @Schema(description = "Include inactive incentive levels", example = "true", required = false, defaultValue = "false", type = "boolean", pattern = "^[true|false]$")
    @RequestParam(defaultValue = "false", value = "with-inactive", required = false)
    withInactive: Boolean = false,
  ): List<IncentiveLevel> {
    return if (withInactive) {
      incentiveLevelService.getAllIncentiveLevels()
    } else {
      incentiveLevelService.getActiveIncentiveLevels()
    }
  }

  @PostMapping("levels")
  @PreAuthorize("hasRole('MAINTAIN_INCENTIVE_LEVELS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a new incentive level",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Incentive level created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid payload",
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
  suspend fun createIncentiveLevel(
    @RequestBody incentiveLevel: IncentiveLevel,
  ): IncentiveLevel {
    ensure {
      ("code" to incentiveLevel.code).hasLengthAtLeast(1).hasLengthAtMost(6)
      ("description" to incentiveLevel.description).hasLengthAtLeast(1)

      if (!incentiveLevel.active && incentiveLevel.required) {
        errors.add("A level must be active if it is required")
      }
    }
    return incentiveLevelService.createIncentiveLevel(incentiveLevel)
  }

  @PatchMapping("level-order")
  @PreAuthorize("hasRole('MAINTAIN_INCENTIVE_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Sets the order of incentive levels",
    description = "All existing incentive level codes must be provided",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive levels reordered",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Not enough level codes provided",
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
        description = "Incentive level with this code not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun setOrderOfIncentiveLevels(
    @RequestBody incentiveLevelCodes: List<String>,
  ): List<IncentiveLevel> {
    ensure {
      ("incentiveLevelCodes" to incentiveLevelCodes).hasSizeAtLeast(2)
    }

    return incentiveLevelService.setOrderOfIncentiveLevels(incentiveLevelCodes)
  }

  @GetMapping("levels/{code}")
  @Operation(
    summary = "Returns an incentive level by code",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive level returned",
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
        description = "Incentive level with this code not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getIncentiveLevel(
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    code: String,
  ): IncentiveLevel {
    return incentiveLevelService.getIncentiveLevel(code)
      ?: throw NoDataWithCodeFoundException("incentive level", code)
  }

  @PutMapping("levels/{code}")
  @PreAuthorize("hasRole('MAINTAIN_INCENTIVE_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Updates an incentive level",
    description = "Payload must include all required fields",
    // TODO: decide and explain what happens to associated per-prison data
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive level updated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid payload", // TODO: maybe also when deactivating and there are prisons with level activated?
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
        description = "Incentive level not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun updateIncentiveLevel(
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    code: String,
    @RequestBody
    incentiveLevel: IncentiveLevel,
  ): IncentiveLevel {
    ensure {
      if (code != incentiveLevel.code) {
        errors.add("Incentive level codes must match in URL and payload")
      }
    }
    return partiallyUpdateIncentiveLevel(code, incentiveLevel.toUpdate())
  }

  @PatchMapping("levels/{code}")
  @PreAuthorize("hasRole('MAINTAIN_INCENTIVE_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Updates an incentive level",
    description = "Partial updates are allowed",
    // TODO: decide and explain what happens to associated per-prison data
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive level updated",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid payload", // TODO: maybe also when deactivating and there are prisons with level activated?
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
        description = "Incentive level not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun partiallyUpdateIncentiveLevel(
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    code: String,
    @RequestBody
    update: IncentiveLevelUpdate,
  ): IncentiveLevel {
    ensure {
      update.description?.let {
        ("description" to it).hasLengthAtLeast(1)
      }

      update.active?.let { active ->
        update.required?.let { required ->
          if (!active && required) {
            errors.add("A level must be active if it is required")
          }
        }
      }
    }
    return incentiveLevelService.updateIncentiveLevel(code, update)
      ?: throw NoDataWithCodeFoundException("incentive level", code)
  }

  @DeleteMapping("levels/{code}")
  @PreAuthorize("hasRole('MAINTAIN_INCENTIVE_LEVELS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Deactivates an incentive level",
    // TODO: decide and explain what happens to associated per-prison data
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Incentive level deactivated",
      ),
      // ApiResponse(
      //   responseCode = "400",
      //   description = "This incentive level is active in some prison so cannot be deactivated",
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
        description = "Incentive level not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun deactivateIncentiveLevel(
    @Schema(description = "Incentive level code", example = "STD", required = true, minLength = 3, maxLength = 6)
    @PathVariable
    code: String,
  ): IncentiveLevel {
    return partiallyUpdateIncentiveLevel(code, IncentiveLevelUpdate(active = false, required = false))
  }
}
