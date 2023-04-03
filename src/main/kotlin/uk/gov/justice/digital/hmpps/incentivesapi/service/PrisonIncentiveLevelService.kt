package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import javax.validation.ValidationException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel as PrisonIncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevelUpdate as PrisonIncentiveLevelUpdateDTO

/**
 * Manages per-prison incentive levels and their associated information.
 *
 * Conceptually, every incentive level exists in every prison and is inactive by default.
 * This means that PrisonIncentiveLevel do not need to be created, only updated.
 * Hence, public methods only allow retrieving active prison incentive levels.
 *
 * Business rules require every prison to have an active level that is the default for admission.
 *
 * NB: Conversions between PrisonIncentiveLevel entity and data transfer object are _only_ done in this service
 */
@Service
@Transactional(readOnly = true)
class PrisonIncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
  private val prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository,
) {
  /**
   * Returns all incentive levels for given prison, including inactive ones, along with associated information, in globally-defined order
   */
  suspend fun getAllPrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    return prisonIncentiveLevelRepository.findAllByPrisonId(prisonId).toListOfDTO()
  }

  /**
   * Returns all active incentive levels for given prison, along with associated information, in globally-defined order
   */
  suspend fun getActivePrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    return prisonIncentiveLevelRepository.findAllByPrisonIdAndActiveIsTrue(prisonId).toListOfDTO()
  }

  /**
   * Returns a incentive level, whether it's active or not, for given prison and level code, along with associated information
   */
  suspend fun getPrisonIncentiveLevel(prisonId: String, levelCode: String): PrisonIncentiveLevelDTO? {
    return prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, levelCode)?.toDTO()
  }

  /**
   * Updates an incentive level for given prison and level code; will fail if data integrity is not maintained.
   * Conceptually, every incetive level exists in every prison but is considered inactive if it does not exist in the database.
   * NB: Default values may be used for associated information if not fully specified and not already in database.
   */
  @Transactional
  suspend fun updatePrisonIncentiveLevel(
    prisonId: String,
    levelCode: String,
    update: PrisonIncentiveLevelUpdateDTO,
  ): PrisonIncentiveLevelDTO? {
    update.remandTransferLimitInPence?.let {
      if (it < 0) throw ValidationException("remandTransferLimitInPence cannot be negative")
    }
    update.remandSpendLimitInPence?.let {
      if (it < 0) throw ValidationException("remandSpendLimitInPence cannot be negative")
    }
    update.convictedTransferLimitInPence?.let {
      if (it < 0) throw ValidationException("convictedTransferLimitInPence cannot be negative")
    }
    update.convictedSpendLimitInPence?.let {
      if (it < 0) throw ValidationException("convictedSpendLimitInPence cannot be negative")
    }
    update.visitOrders?.let {
      if (it < 0) throw ValidationException("visitOrders cannot be negative")
    }
    update.privilegedVisitOrders?.let {
      if (it < 0) throw ValidationException("privilegedVisitOrders cannot be negative")
    }

    return incentiveLevelRepository.findById(levelCode)?.let { incentiveLevel ->
      val prisonIncentiveLevel =
        prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, levelCode)
          ?.withUpdate(update)
          ?: update.toNewEntity(prisonId, levelCode)

      if (incentiveLevel.required && !prisonIncentiveLevel.active) {
        throw ValidationException("A level cannot be made inactive and when it is globally required")
      }
      if (!prisonIncentiveLevel.active && prisonIncentiveLevel.defaultOnAdmission) {
        throw ValidationException("A level cannot be made inactive and still be the default for admission")
      }

      if (prisonIncentiveLevel.defaultOnAdmission) {
        prisonIncentiveLevelRepository.setOtherLevelsNotDefaultForAdmission(prisonId, levelCode)
      } else {
        val currentDefaultLevelCode =
          prisonIncentiveLevelRepository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue(prisonId)?.levelCode
        if (currentDefaultLevelCode == null || currentDefaultLevelCode == prisonIncentiveLevel.levelCode) {
          // NB: this rule means that for a prison with no levels yet in the database, the first one added must be the default level
          throw ValidationException("There must be an active default level for admission in a prison")
        }
      }

      prisonIncentiveLevelRepository.save(prisonIncentiveLevel)
        .copy(levelDescription = incentiveLevel.description)
        .toDTO()
    }
  }

  /**
   * Updates prison incentive levels to be active if there is some level that’s active in the prison
   */
  suspend fun activatePrisonIncentiveLevelInActivePrisons(levelCode: String) {
    prisonIncentiveLevelRepository.findPrisonIdsWithActiveLevels().collect { prisonId ->
      updatePrisonIncentiveLevel(
        prisonId,
        levelCode,
        PrisonIncentiveLevelUpdateDTO(active = true),
      )
    }
  }

  private fun PrisonIncentiveLevel.withUpdate(update: PrisonIncentiveLevelUpdateDTO): PrisonIncentiveLevel = copy(
    active = update.active ?: active,
    defaultOnAdmission = update.defaultOnAdmission ?: defaultOnAdmission,

    remandTransferLimitInPence = update.remandTransferLimitInPence ?: remandTransferLimitInPence,
    remandSpendLimitInPence = update.remandSpendLimitInPence ?: remandSpendLimitInPence,
    convictedTransferLimitInPence = update.convictedTransferLimitInPence ?: convictedTransferLimitInPence,
    convictedSpendLimitInPence = update.convictedSpendLimitInPence ?: convictedSpendLimitInPence,

    visitOrders = update.visitOrders ?: visitOrders,
    privilegedVisitOrders = update.privilegedVisitOrders ?: privilegedVisitOrders,

    new = false,
    whenUpdated = LocalDateTime.now(clock),
  )

  private fun PrisonIncentiveLevel.toDTO(): PrisonIncentiveLevelDTO = PrisonIncentiveLevelDTO(
    levelCode = levelCode,
    levelDescription = levelDescription!!, // NB: entity will always have a level description if loaded using correct repository method
    prisonId = prisonId,
    active = active,
    defaultOnAdmission = defaultOnAdmission,

    remandTransferLimitInPence = remandTransferLimitInPence,
    remandSpendLimitInPence = remandSpendLimitInPence,
    convictedTransferLimitInPence = convictedTransferLimitInPence,
    convictedSpendLimitInPence = convictedSpendLimitInPence,

    visitOrders = visitOrders,
    privilegedVisitOrders = privilegedVisitOrders,
  )

  private fun PrisonIncentiveLevelUpdateDTO.toNewEntity(
    prisonId: String,
    levelCode: String,
  ): PrisonIncentiveLevel {
    val fallbackSpendLimits = spendLimitPolicy.getOrDefault(levelCode, spendLimitPolicy["ENH"]!!)

    return PrisonIncentiveLevel(
      levelCode = levelCode,
      prisonId = prisonId,
      active = active ?: true,
      defaultOnAdmission = defaultOnAdmission ?: false,

      remandTransferLimitInPence = remandTransferLimitInPence
        ?: fallbackSpendLimits.remandTransferLimitInPence,
      remandSpendLimitInPence = remandSpendLimitInPence
        ?: fallbackSpendLimits.remandSpendLimitInPence,
      convictedTransferLimitInPence = convictedTransferLimitInPence
        ?: fallbackSpendLimits.convictedTransferLimitInPence,
      convictedSpendLimitInPence = convictedSpendLimitInPence
        ?: fallbackSpendLimits.convictedSpendLimitInPence,

      visitOrders = visitOrders ?: 2,
      privilegedVisitOrders = privilegedVisitOrders ?: 1,

      new = true,
      whenUpdated = LocalDateTime.now(clock),
    )
  }

  private suspend fun Flow<PrisonIncentiveLevel>.toListOfDTO(): List<PrisonIncentiveLevelDTO> =
    map { it.toDTO() }.toList()
}

private data class SpendLimits(
  val remandTransferLimitInPence: Int,
  val remandSpendLimitInPence: Int,
  val convictedTransferLimitInPence: Int,
  val convictedSpendLimitInPence: Int,
)

private val spendLimitPolicy = mapOf(
  "BAS" to SpendLimits(27_50, 275_00, 5_50, 55_00),
  "STD" to SpendLimits(60_50, 605_00, 19_80, 198_00),
  "ENH" to SpendLimits(66_00, 660_00, 33_00, 330_00),
)
