package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorCode
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.config.ValidationExceptionWithErrorCode
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.associateByTo
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate as IncentiveLevelUpdateDTO

/**
 * Manages globally-defined incentive levels.
 *
 * NB: Conversions between IncentiveLevel entity and data transfer object are _only_ done in this service
 */
@Service
@Transactional(readOnly = true)
@Suppress("ktlint:standard:discouraged-comment-location")
class IncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService, // OK to inject unaudited version as audited subclass passes in audited form instead
) {
  /**
   * Returns all incentive levels, including inactive ones, in globally-defined order
   */
  suspend fun getAllIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByOrderBySequence().toListOfDTO()
  }

  /**
   * Returns all incentive levels, including inactive ones, as a map of code-to-details
   */
  suspend fun getAllIncentiveLevelsMapByCode(): Map<String, IncentiveLevelDTO> {
    return getAllIncentiveLevels().associateBy(IncentiveLevelDTO::code)
  }

  /**
   * Returns all active incentive levels, in globally-defined order
   */
  suspend fun getActiveIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByActiveIsTrueOrderBySequence().toListOfDTO()
  }

  /**
   * Returns an incentive level, whether active or not, by code
   */
  suspend fun getIncentiveLevel(code: String): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)?.toDTO()
  }

  /**
   * Creates a new incentive level; will fail if code already exists
   */
  @Transactional
  suspend fun createIncentiveLevel(dto: IncentiveLevelDTO): IncentiveLevelDTO {
    if (!dto.active && dto.required) {
      throw ValidationExceptionWithErrorCode(
        "A level must be active if it is required",
        ErrorCode.IncentiveLevelActiveIfRequired,
      )
    }
    if (incentiveLevelRepository.existsById(dto.code)) {
      throw ValidationExceptionWithErrorCode(
        "Incentive level with code ${dto.code} already exists",
        ErrorCode.IncentiveLevelCodeNotUnique,
      )
    }

    val highestSequence = incentiveLevelRepository.findMaxSequence() ?: 0
    val incentiveLevel = dto.toNewEntity(highestSequence + 1)
    return incentiveLevelRepository.save(incentiveLevel)
      .toDTO()
      .also {
        if (it.required) {
          prisonIncentiveLevelService.activatePrisonIncentiveLevelInActivePrisons(it.code)
        }
      }
  }

  /**
   * Updates an existing incentive level; will fail if code does not exist
   */
  @Transactional
  suspend fun updateIncentiveLevel(
    code: String,
    update: IncentiveLevelUpdateDTO,
  ): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)
      ?.let { originalIncentiveLevel ->
        val incentiveLevel = originalIncentiveLevel.withUpdate(update)

        if (!incentiveLevel.active && incentiveLevel.required) {
          throw ValidationExceptionWithErrorCode(
            "A level must be active if it is required",
            ErrorCode.IncentiveLevelActiveIfRequired,
          )
        }
        if (originalIncentiveLevel.active && !incentiveLevel.active) {
          val prisonIds = prisonIncentiveLevelService.getPrisonIdsWithActivePrisonIncentiveLevel(incentiveLevel.code)
          if (prisonIds.isNotEmpty()) {
            throw ValidationExceptionWithErrorCode(
              "A level must remain active if it is active in some prison",
              ErrorCode.IncentiveLevelActiveIfActiveInPrison,
              moreInfo = prisonIds.joinToString(","),
            )
          }
        }

        incentiveLevelRepository.save(incentiveLevel)
          .toDTO()
          .also {
            if (it.required && !originalIncentiveLevel.required) {
              prisonIncentiveLevelService.activatePrisonIncentiveLevelInActivePrisons(it.code)
            }
          }
      }
  }

  /**
   * Reorders incentive levels; will fail is not provided with all known codes
   */
  @Transactional
  suspend fun setOrderOfIncentiveLevels(incentiveLevelCodes: List<String>): List<IncentiveLevelDTO> {
    val allIncentiveLevels = mutableMapOf<String, IncentiveLevel>()
    incentiveLevelRepository.findAll().associateByTo(allIncentiveLevels, IncentiveLevel::code)

    val allIncentiveLevelsInDesiredOrder =
      incentiveLevelCodes.map { code ->
        allIncentiveLevels.remove(code)
          ?: throw NoDataWithCodeFoundException("incentive level", code)
      }
    if (allIncentiveLevels.isNotEmpty()) {
      val missing = allIncentiveLevels.keys.joinToString("`, `", prefix = "`", postfix = "`")
      throw ValidationExceptionWithErrorCode(
        "All incentive levels required when setting order. Missing: $missing",
        ErrorCode.IncentiveLevelReorderNeedsFullSet,
      )
    }

    val incentiveLevelsWithNewSequences =
      allIncentiveLevelsInDesiredOrder.mapIndexed { index, incentiveLevel ->
        incentiveLevel.copy(
          sequence = index + 1,
          new = false,
          whenUpdated = LocalDateTime.now(clock),
        )
      }

    return incentiveLevelRepository.saveAll(incentiveLevelsWithNewSequences)
      .toListOfDTO()
  }

  private fun IncentiveLevel.withUpdate(update: IncentiveLevelUpdateDTO): IncentiveLevel =
    copy(
      code = code,
      name = update.name ?: name,
      active = update.active ?: active,
      required = update.required ?: required,
      new = false,
      whenUpdated = LocalDateTime.now(clock),
    )

  private fun IncentiveLevel.toDTO(): IncentiveLevelDTO =
    IncentiveLevelDTO(
      code = code,
      name = name,
      active = active,
      required = required,
    )

  private fun IncentiveLevelDTO.toNewEntity(sequence: Int): IncentiveLevel =
    IncentiveLevel(
      code = code,
      name = name,
      sequence = sequence,
      active = active,
      required = required,
      new = true,
      whenUpdated = LocalDateTime.now(clock),
    )

  private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
}
