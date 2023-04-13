package uk.gov.justice.digital.hmpps.incentivesapi.service

import jakarta.validation.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.toIncentivesServiceDto
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
class IncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService,
  private val featureFlagsService: FeatureFlagsService,
  private val prisonApiService: PrisonApiService,
) {
  /**
   * Returns all incentive levels, including inactive ones, in globally-defined order
   */
  suspend fun getAllIncentiveLevels(): List<IncentiveLevelDTO> {
    return if (featureFlagsService.isIncentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase()) {
      incentiveLevelRepository.findAllByOrderBySequence().toListOfDTO()
    } else {
      prisonApiService.getIepLevels().sortedBy(IepLevel::sequence).map { it.toIncentivesServiceDto() }
    }
  }

  suspend fun getAllIncentiveLevelsMapByCode(): Map<String, IncentiveLevelDTO> {
    return getAllIncentiveLevels().associateBy(IncentiveLevelDTO::code)
  }

  /**
   * Returns all active incentive levels, in globally-defined order
   */
  suspend fun getActiveIncentiveLevels(): List<IncentiveLevelDTO> {
    return if (featureFlagsService.isIncentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase()) {
      incentiveLevelRepository.findAllByActiveIsTrueOrderBySequence().toListOfDTO()
    } else {
      prisonApiService.getIepLevels().filter(IepLevel::active).sortedBy(IepLevel::sequence).map { it.toIncentivesServiceDto() }
    }
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
      throw ValidationException("A level must be active if it is required")
    }
    if (incentiveLevelRepository.existsById(dto.code)) {
      throw ValidationException("Incentive level with code ${dto.code} already exists")
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
  suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdateDTO): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)
      ?.let { originalIncentiveLevel ->
        val incentiveLevel = originalIncentiveLevel.withUpdate(update)

        if (!incentiveLevel.active && incentiveLevel.required) {
          throw ValidationException("A level must be active if it is required")
        }
        if (originalIncentiveLevel.active && !incentiveLevel.active) {
          if (prisonIncentiveLevelService.getPrisonIdsWithActivePrisonIncentiveLevel(incentiveLevel.code).isNotEmpty()) {
            throw ValidationException("A level must remain active if it is active in some prison")
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
    name = update.name ?: name,
    description = update.description ?: description,
    active = update.active ?: active,
    required = update.required ?: required,

    new = false,
    whenUpdated = LocalDateTime.now(clock),
  )

  private fun IncentiveLevel.toDTO(): IncentiveLevelDTO = IncentiveLevelDTO(
    code = code,
    name = name,
    description = description,
    active = active,
    required = required,
  )

  private fun IncentiveLevelDTO.toNewEntity(sequence: Int): IncentiveLevel = IncentiveLevel(
    code = code,
    name = name,
    description = description,
    sequence = sequence,
    active = active,
    required = required,

    new = true,
    whenUpdated = LocalDateTime.now(clock),
  )

  private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
}
