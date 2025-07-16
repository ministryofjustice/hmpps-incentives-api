package uk.gov.justice.digital.hmpps.incentivesapi.config

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewNotFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.service.PrisonerNotFoundException

@RestControllerAdvice
class HmppsIncentivesApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    log.debug("Validation error (400) returned: {}", e.message)
    val message = if (e.hasFieldErrors()) {
      e.fieldError?.defaultMessage
    } else {
      e.message
    }
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: $message",
          developerMessage = (e.message),
        ),
      )
  }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    log.debug("Forbidden (403) returned with message {}", e.message)
    return ResponseEntity
      .status(FORBIDDEN)
      .body(
        ErrorResponse(
          status = FORBIDDEN,
          userMessage = "Forbidden: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  /**
   * Validation exceptions including `ParameterValidationException`
   * thrown by `ensure` blocks when field validations fail
   */
  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> {
    @Suppress("LoggingSimilarMessage")
    log.info("Validation exception: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = if (e is ValidationExceptionWithErrorCode) {
            e.errorCode
          } else {
            null
          },
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
          moreInfo = if (e is ValidationExceptionWithErrorCode) {
            e.moreInfo
          } else {
            null
          },
        ),
      )
  }

  @ExceptionHandler(WebExchangeBindException::class)
  fun handleWebExchangeBindException(e: WebExchangeBindException): ResponseEntity<ErrorResponse> {
    log.info("Validation exception: {}", e.message)
    val message = if (e.hasFieldErrors()) {
      e.fieldError?.defaultMessage
    } else {
      e.message
    }
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: $message",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(UnsupportedMediaTypeStatusException::class)
  fun handleUnsupportedMediaTypeStatusException(e: UnsupportedMediaTypeStatusException): ResponseEntity<ErrorResponse> {
    val supportedTypes = if (e.supportedMediaTypes.isEmpty()) {
      "accepted types not specified"
    } else {
      e.supportedMediaTypes.joinToString(", ", prefix = "accepted types: ")
    }
    val message = "Unsupported media type ${e.contentType}; $supportedTypes"
    log.info(message)
    return ResponseEntity
      .status(UNSUPPORTED_MEDIA_TYPE)
      .body(
        ErrorResponse(
          status = UNSUPPORTED_MEDIA_TYPE,
          userMessage = message,
          developerMessage = message,
        ),
      )
  }

  @ExceptionHandler(NotFound::class)
  fun handleNotFound(e: NotFound): ResponseEntity<ErrorResponse?>? {
    log.debug("Not found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(SubjectAccessRequestNoContentException::class)
  fun handleSubjectAccessRequestNoContentException(
    e: SubjectAccessRequestNoContentException,
  ): ResponseEntity<ErrorResponse?>? {
    log.debug("SAR No Content exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NO_CONTENT)
      .body(
        ErrorResponse(
          status = HttpStatus.NO_CONTENT,
          userMessage = "No Content: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("No resource found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "No resource found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(IncentiveReviewNotFoundException::class)
  fun handleIncentiveReviewNotFoundException(e: IncentiveReviewNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("No incentive review found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(PrisonerNotFoundException::class)
  fun handlePrisonerNotFoundIException(e: PrisonerNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Prisoner not found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(NoDataFoundException::class)
  fun handleNoDataFoundException(e: NoDataFoundException): ResponseEntity<ErrorResponse?>? {
    @Suppress("LoggingSimilarMessage")
    log.debug("No data found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(NoDataWithCodeFoundException::class)
  fun handleNoDataWithCodeFoundException(e: NoDataWithCodeFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("No data found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = e.message,
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(ListOfDataNotFoundException::class)
  fun handleListOfDataNotFoundException(e: ListOfDataNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("List of data not found exception caught: {}", e.message)
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = e.message,
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(DataIntegrityException::class)
  fun handleDataIntegrityException(e: DataIntegrityException): ResponseEntity<ErrorResponse?>? {
    log.error("Data integrity exception: {}", e.message)
    return ResponseEntity
      .status(INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = INTERNAL_SERVER_ERROR,
          userMessage = "Data integrity exception: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(java.lang.Exception::class)
  fun handleException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
    log.error("Unexpected exception", e)
    return ResponseEntity
      .status(INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = INTERNAL_SERVER_ERROR,
          userMessage = "Unexpected error: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  /**
   * Exception thrown when the request body is missing a required field
   * or provided value doesn't match valid enum values
   *
   * NOTE: This exception covers a number of possible bad inputs,
   * e.g. missing fields, values of the wrong type, values not
   * matching the ones available in an enum, etc...
   */
  @ExceptionHandler(ServerWebInputException::class)
  fun handleServerWebInputException(e: ServerWebInputException): ResponseEntity<ErrorResponse> {
    val developerMessage = "Invalid request format: ${e.cause}"
    log.error(developerMessage)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Invalid request format, " +
            "e.g. request is missing a required field or one of the fields has an invalid value",
          developerMessage = developerMessage,
        ),
      )
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class NoDataFoundException(
  id: Long,
) : Exception("No Data found for ID $id")

class NoDataWithCodeFoundException(
  dataType: String,
  code: String,
) : Exception("No $dataType found for code `$code`")

class ListOfDataNotFoundException(
  dataType: String,
  ids: Collection<Long>,
) : Exception("No $dataType found for ID(s) $ids")

class DataIntegrityException(
  message: String,
) : Exception(message)

class ValidationExceptionWithErrorCode(
  message: String,
  val errorCode: ErrorCode,
  val moreInfo: String? = null,
) : ValidationException(message)

class SubjectAccessRequestNoContentException(
  prisoner: String,
) : Exception("No information on prisoner $prisoner")

/**
 * Codes that can be used by api clients to uniquely discriminate between error types,
 * instead of relying on non-constant text descriptions.
 *
 * NB: Once defined, the values must not be changed
 */
enum class ErrorCode(
  val errorCode: Int,
) {
  IncentiveLevelActiveIfRequired(100),
  IncentiveLevelActiveIfActiveInPrison(101),
  IncentiveLevelCodeNotUnique(102),
  IncentiveLevelReorderNeedsFullSet(103),

  PrisonIncentiveLevelActiveIfRequired(200),
  PrisonIncentiveLevelActiveIfDefault(201),
  PrisonIncentiveLevelActiveIfPrisonersExist(202),
  PrisonIncentiveLevelNotGloballyActive(203),
  PrisonIncentiveLevelDefaultRequired(204),
}

@Schema(description = "Error response")
data class ErrorResponse(
  @param:Schema(description = "HTTP status code", example = "500", required = true)
  val status: Int,
  @param:Schema(
    description = "When present, uniquely identifies the type of error " +
      "making it easier for clients to discriminate without relying on error description; " +
      "see `uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorResponse` enumeration " +
      "in hmpps-incentives-api",
    example = "123",
    required = false,
  )
  val errorCode: Int? = null,
  @param:Schema(
    description = "User message for the error",
    example = "No incentive level found for code `ABC`",
    required = false,
  )
  val userMessage: String? = null,
  @param:Schema(
    description = "More detailed error message",
    example = "[Details, sometimes a stack trace]",
    required = false,
  )
  val developerMessage: String? = null,
  @param:Schema(
    description = "More information about the error",
    example = "[Rarely used, error-specific]",
    required = false,
  )
  val moreInfo: String? = null,
) {
  constructor(
    status: HttpStatus,
    errorCode: ErrorCode? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
    moreInfo: String? = null,
  ) :
    this(status.value(), errorCode?.errorCode, userMessage, developerMessage, moreInfo)
}
