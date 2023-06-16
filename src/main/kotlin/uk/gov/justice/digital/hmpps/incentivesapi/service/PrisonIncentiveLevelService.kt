package uk.gov.justice.digital.hmpps.incentivesapi.service

import jakarta.validation.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorCode
import uk.gov.justice.digital.hmpps.incentivesapi.config.ValidationExceptionWithErrorCode
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import java.time.Clock
import java.time.LocalDateTime
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
  private val countPrisonersService: CountPrisonersService,
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
   * Returns an incentive level, whether it's active or not, for given prison and level code, along with associated information
   */
  suspend fun getPrisonIncentiveLevel(prisonId: String, levelCode: String): PrisonIncentiveLevelDTO? {
    return prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, levelCode)?.toDTO()
  }

  /**
   * Returns prison ids which have given level active
   */
  suspend fun getPrisonIdsWithActivePrisonIncentiveLevel(levelCode: String): List<String> {
    return prisonIncentiveLevelRepository.findPrisonIdsWithActiveLevel(levelCode).toList()
  }

  /**
   * Updates an incentive level for given prison and level code; will fail if data integrity is not maintained.
   * Conceptually, every incentive level exists in every prison but is considered inactive if it does not exist in the database.
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
      val originalPrisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, levelCode)
      val prisonIncentiveLevel = originalPrisonIncentiveLevel
        ?.withUpdate(update)
        ?: update.toNewEntity(prisonId, levelCode)

      if (!incentiveLevel.active && prisonIncentiveLevel.active) {
        throw ValidationExceptionWithErrorCode(
          "A level cannot be made active and when it is globally inactive",
          ErrorCode.PrisonIncentiveLevelNotGloballyActive,
        )
      }
      if (incentiveLevel.required && !prisonIncentiveLevel.active) {
        throw ValidationExceptionWithErrorCode(
          "A level cannot be made inactive and when it is globally required",
          ErrorCode.PrisonIncentiveLevelActiveIfRequired,
        )
      }
      if (!prisonIncentiveLevel.active && prisonIncentiveLevel.defaultOnAdmission) {
        throw ValidationExceptionWithErrorCode(
          "A level cannot be made inactive and still be the default for admission",
          ErrorCode.PrisonIncentiveLevelActiveIfDefault,
        )
      }
      if (originalPrisonIncentiveLevel?.active == true && !prisonIncentiveLevel.active) {
        val prisonersExistOnLevel = countPrisonersService.prisonersExistOnLevelInPrison(prisonIncentiveLevel.prisonId, prisonIncentiveLevel.levelCode)
        if (prisonersExistOnLevel) {
          throw ValidationExceptionWithErrorCode(
            "A level must remain active if there are prisoners on it currently",
            ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
          )
        }
      }

      var levelCodesNoLongerDefault: List<String> = emptyList()
      if (prisonIncentiveLevel.defaultOnAdmission) {
        levelCodesNoLongerDefault = prisonIncentiveLevelRepository.setOtherLevelsNotDefaultForAdmission(prisonId, levelCode).toList()
      } else {
        val currentDefaultLevelCode =
          prisonIncentiveLevelRepository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue(prisonId)?.levelCode
        if (currentDefaultLevelCode == null || currentDefaultLevelCode == prisonIncentiveLevel.levelCode) {
          // NB: this rule means that for a prison with no levels yet in the database, the first one added must be the default level
          throw ValidationExceptionWithErrorCode(
            "There must be an active default level for admission in a prison",
            ErrorCode.PrisonIncentiveLevelDefaultRequired,
          )
        }
      }

      prisonIncentiveLevelRepository.save(prisonIncentiveLevel)
        .copy(levelName = incentiveLevel.name)
        .toDTO()
        .also {
          levelCodesNoLongerDefault.forEach { levelCode ->
            // trigger audit events by submitting a no-change update
            updatePrisonIncentiveLevel(prisonId, levelCode, PrisonIncentiveLevelUpdateDTO())
          }
        }
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

  /**
   * Deactivate all incentive levels for a given prison; useful for when prisons are closed.
   * NB: returns only the incentive levels that were deactivated
   */
  @Transactional
  suspend fun deactivateAllPrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId(prisonId).toList()
    val prisonIncentiveLevelsWithPrisoners = prisonIncentiveLevels.filter { prisonIncentiveLevel ->
      countPrisonersService.prisonersExistOnLevelInPrison(prisonId, prisonIncentiveLevel.levelCode)
    }
    if (prisonIncentiveLevelsWithPrisoners.isNotEmpty()) {
      throw ValidationExceptionWithErrorCode(
        "A level must remain active if there are prisoners on it currently",
        ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
        moreInfo = prisonIncentiveLevelsWithPrisoners.map(PrisonIncentiveLevel::levelCode).joinToString(","),
      )
    }
    return prisonIncentiveLevelRepository.saveAll(
      prisonIncentiveLevels.filter(PrisonIncentiveLevel::active)
        .map { prisonIncentiveLevel ->
          prisonIncentiveLevel.withUpdate(PrisonIncentiveLevelUpdateDTO(active = false))
        },
    ).toListOfDTO()
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
    levelName = levelName!!, // NB: entity will always have a level name if loaded using correct repository method
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
    val fallback = prisonIncentiveLevelPolicies.getOrDefault(levelCode, prisonIncentiveLevelPolicies["ENH"]!!)

    return PrisonIncentiveLevel(
      levelCode = levelCode,
      prisonId = prisonId,
      active = active ?: true,
      defaultOnAdmission = defaultOnAdmission ?: false,

      remandTransferLimitInPence = remandTransferLimitInPence
        ?: fallback.remandTransferLimitInPence,
      remandSpendLimitInPence = remandSpendLimitInPence
        ?: fallback.remandSpendLimitInPence,
      convictedTransferLimitInPence = convictedTransferLimitInPence
        ?: fallback.convictedTransferLimitInPence,
      convictedSpendLimitInPence = convictedSpendLimitInPence
        ?: fallback.convictedSpendLimitInPence,

      visitOrders = visitOrders
        ?: fallback.visitOrders,
      privilegedVisitOrders = privilegedVisitOrders
        ?: fallback.privilegedVisitOrders,

      new = true,
      whenUpdated = LocalDateTime.now(clock),
    )
  }

  private suspend fun Flow<PrisonIncentiveLevel>.toListOfDTO(): List<PrisonIncentiveLevelDTO> =
    map { it.toDTO() }.toList()
}

private data class Policy(
  val remandTransferLimitInPence: Int,
  val remandSpendLimitInPence: Int,
  val convictedTransferLimitInPence: Int,
  val convictedSpendLimitInPence: Int,

  val visitOrders: Int,
  val privilegedVisitOrders: Int,
)

private val prisonIncentiveLevelPolicies = mapOf(
  "BAS" to Policy(27_50, 275_00, 5_50, 55_00, 2, 1),
  "STD" to Policy(60_50, 605_00, 19_80, 198_00, 2, 1),
  "ENH" to Policy(66_00, 660_00, 33_00, 330_00, 2, 1),
)
