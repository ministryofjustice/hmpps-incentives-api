package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel as PrisonIncentiveLevelDTO

@Service
class PrisonIncentiveLevelService(
  private val prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository,
) {
  suspend fun getActivePrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    return prisonIncentiveLevelRepository.findAllByPrisonIdAndActiveIsTrue(prisonId).toListOfDTO()
  }

  suspend fun getActivePrisonIncentiveLevel(prisonId: String, levelCode: String): PrisonIncentiveLevelDTO? {
    return prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCodeAndActiveIsTrue(prisonId, levelCode)?.toDTO()
  }
}

// conversions between PrisonIncentiveLevel entity and PrisonIncentiveLevel data transfer object are _only_ done in this service

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

private suspend fun Flow<PrisonIncentiveLevel>.toListOfDTO(): List<PrisonIncentiveLevelDTO> =
  map { it.toDTO() }.toList()
