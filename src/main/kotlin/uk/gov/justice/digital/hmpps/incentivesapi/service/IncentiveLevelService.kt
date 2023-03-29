package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.associateByTo
import java.time.Clock
import java.time.LocalDateTime
import javax.validation.ValidationException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate as IncentiveLevelUpdateDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevelUpdate as PrisonIncentiveLevelUpdateDTO

/**
 * Manages globally-defined incentive levels.
 *
 * NB: Conversions between IncentiveLevel entity and data transfer object are _only_ done in this service
 */
@Service
@Transactional(readOnly = true)
class IncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
  private val prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService,
) {
  /**
   * Returns all incentive levels, including inactive ones, in globally-defined order
   */
  suspend fun getAllIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByOrderBySequence().toListOfDTO()
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
  suspend fun createIncentiveLevel(dto: IncentiveLevelDTO): UpdatedIncentiveLevel {
    if (!dto.active && dto.required) {
      throw ValidationException("A level must be active if it is required")
    }
    if (incentiveLevelRepository.existsById(dto.code)) {
      throw ValidationException("Incentive level with code ${dto.code} already exists")
    }

    val highestSequence = incentiveLevelRepository.findMaxSequence() ?: 0
    val incentiveLevel = dto.toNewEntity(highestSequence + 1)
    val savedLevel = incentiveLevelRepository.save(incentiveLevel).toDTO()
    var prisonsAffected = emptyList<PrisonIncentiveLevel>()
    if (savedLevel.required) {
      prisonsAffected = enablePrisonIncentiveLevelEverywhere(savedLevel.code)
    }
    return UpdatedIncentiveLevel(savedLevel, prisonsAffected)
  }

  /**
   * Updates an existing incentive level; will fail if code does not exist
   */
  @Transactional
  suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdateDTO): UpdatedIncentiveLevel? {
    return incentiveLevelRepository.findById(code)
      ?.let { originalIncentiveLevel ->
        val incentiveLevel = originalIncentiveLevel.withUpdate(update)

        if (!incentiveLevel.active && incentiveLevel.required) {
          throw ValidationException("A level must be active if it is required")
        }

        val savedLevel = incentiveLevelRepository.save(incentiveLevel).toDTO()
        var prisonsAffected = emptyList<PrisonIncentiveLevel>()
        if (savedLevel.required && !originalIncentiveLevel.required) {
          prisonsAffected = enablePrisonIncentiveLevelEverywhere(savedLevel.code)
        }

        return UpdatedIncentiveLevel(savedLevel, prisonsAffected, originalIncentiveLevel)
      }
  }

  private suspend fun enablePrisonIncentiveLevelEverywhere(levelCode: String): List<PrisonIncentiveLevel> =
    prisonIncentiveLevelRepository.findPrisonIdsWithActiveLevels().map { prisonId ->
      prisonIncentiveLevelService.updatePrisonIncentiveLevel(
        prisonId,
        levelCode,
        PrisonIncentiveLevelUpdateDTO(active = true),
      )
    }
      .mapNotNull { it }
      .toList()

  /**
   * Reorders incentive levels; will fail is not provided with all known codes
   */
  @Transactional
  suspend fun setOrderOfIncentiveLevels(incentiveLevelCodes: List<String>): List<IncentiveLevelDTO> {
    val allIncentiveLevels = mutableMapOf<String, IncentiveLevel>()
    incentiveLevelRepository.findAll().associateByTo(allIncentiveLevels, IncentiveLevel::code)

    val allIncentiveLevelsInDesiredOrder = incentiveLevelCodes.map { code ->
      allIncentiveLevels.remove(code)
        ?: throw NoDataWithCodeFoundException("incentive level", code)
    }
    if (allIncentiveLevels.isNotEmpty()) {
      val missing = allIncentiveLevels.keys.joinToString("`, `", prefix = "`", postfix = "`")
      throw ValidationException("All incentive levels required when setting order. Missing: $missing")
    }

    val incentiveLevelsWithNewSequences = allIncentiveLevelsInDesiredOrder.mapIndexed { index, incentiveLevel ->
      incentiveLevel.copy(
        sequence = index + 1,

        new = false,
        whenUpdated = LocalDateTime.now(clock),
      )
    }

    return incentiveLevelRepository.saveAll(incentiveLevelsWithNewSequences)
      .toListOfDTO()
  }

  private fun IncentiveLevel.withUpdate(update: IncentiveLevelUpdateDTO): IncentiveLevel = copy(
    code = code,
    description = update.description ?: description,
    active = update.active ?: active,
    required = update.required ?: required,

    new = false,
    whenUpdated = LocalDateTime.now(clock),
  )

  private fun IncentiveLevel.toDTO(): IncentiveLevelDTO = IncentiveLevelDTO(
    code = code,
    description = description,
    active = active,
    required = required,
  )

  private fun IncentiveLevelDTO.toNewEntity(sequence: Int): IncentiveLevel = IncentiveLevel(
    code = code,
    description = description,
    sequence = sequence,
    active = active,
    required = required,

    new = true,
    whenUpdated = LocalDateTime.now(clock),
  )

  data class UpdatedIncentiveLevel(
    var updatedLevel: IncentiveLevelDTO,
    var updatedPrisons: List<PrisonIncentiveLevel>,
    var originalIncentiveLevel: IncentiveLevel? = null,
  )

  private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
}
