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
 *
 * Business rules require every prison to have an active level that is the default for admission.
 *
 * NB: Conversions between PrisonIncentiveLevel entity and data transfer object are _only_ done in this service
 */
@Service
class PrisonIncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
  private val prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository,
) {
  suspend fun getActivePrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    return prisonIncentiveLevelRepository.findAllByPrisonIdAndActiveIsTrue(prisonId).toListOfDTO()
  }

  suspend fun getActivePrisonIncentiveLevel(prisonId: String, levelCode: String): PrisonIncentiveLevelDTO? {
    return prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCodeAndActiveIsTrue(prisonId, levelCode)?.toDTO()
  }

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
  ): PrisonIncentiveLevel = PrisonIncentiveLevel(
    levelCode = levelCode,
    prisonId = prisonId,
    active = active ?: true,
    defaultOnAdmission = defaultOnAdmission ?: false,

    // TODO: find sensible defaults or have these configured per-level on IncentiveLevel

    remandTransferLimitInPence = remandTransferLimitInPence ?: 5500,
    remandSpendLimitInPence = remandSpendLimitInPence ?: 55000,
    convictedTransferLimitInPence = convictedTransferLimitInPence ?: 1800,
    convictedSpendLimitInPence = convictedSpendLimitInPence ?: 18000,

    visitOrders = visitOrders ?: 2,
    privilegedVisitOrders = privilegedVisitOrders ?: 1,

    new = true,
    whenUpdated = LocalDateTime.now(clock),
  )

  private suspend fun Flow<PrisonIncentiveLevel>.toListOfDTO(): List<PrisonIncentiveLevelDTO> =
    map { it.toDTO() }.toList()
}
