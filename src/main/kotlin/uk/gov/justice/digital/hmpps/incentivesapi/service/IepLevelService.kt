package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel

@Service
class IepLevelService(
  private val prisonApiService: PrisonApiService,
  private val incentiveLevelService: IncentiveLevelService,
) {

  suspend fun getIepLevelsForPrison(prisonId: String, useClientCredentials: Boolean = false): List<IepLevel> {
    return prisonApiService.getIepLevelsForPrison(prisonId, useClientCredentials)
      .toList()
      .sortedWith(compareBy(IepLevel::sequence))
  }

  fun chooseDefaultLevel(prisonId: String, prisonLevels: List<IepLevel>): String {
    val activePrisonLevels = prisonLevels.filter(IepLevel::active)
    return activePrisonLevels.firstOrNull(IepLevel::default)?.iepLevel
      // fall back to standard if available
      ?: activePrisonLevels.firstOrNull { it.iepLevel == "STD" }?.iepLevel
      // fall back to first available level
      ?: activePrisonLevels.firstOrNull()?.iepLevel
      // there are no available incentive levels at all at this prison
      ?: throw DataIntegrityException("$prisonId has no available incentive levels")
  }

  /**
   * Because not all levels are enabled at all prisons, and we want to maintain a
   * person’s level when they are transferred, we need to find the "nearest highest" level
   * according to HMPPS policy. Falls back to prison's default level when there's nothing else to do.
   */
  suspend fun findNearestHighestLevel(prisonId: String, prisonLevels: List<IepLevel>, levelCode: String): String {
    val defaultLevelCode = chooseDefaultLevel(prisonId, prisonLevels)
    val levelCodesAvailableInPrison = prisonLevels.filter(IepLevel::active).map(IepLevel::iepLevel).toSet()

    data class KnownLevel(val code: String, val available: Boolean)
    val allKnownLevels = incentiveLevelService.getAllIncentiveLevels()
      .map {
        KnownLevel(
          code = it.code,
          available = it.active && levelCodesAvailableInPrison.contains(it.code),
        )
      }

    val indexOfTargetLevelCode = allKnownLevels.indexOfFirst { it.code == levelCode }
    if (indexOfTargetLevelCode == -1) {
      // target level does not appear in known levels so can only fall back to prison’s default level
      return defaultLevelCode
    }
    // find the first available level higher or same as target level
    for (someLevel in allKnownLevels.withIndex()) {
      if (someLevel.index >= indexOfTargetLevelCode && someLevel.value.available) {
        return someLevel.value.code
      }
    }
    // find the first available level lower or same as target level
    for (someLevel in allKnownLevels.withIndex().reversed()) {
      if (someLevel.index <= indexOfTargetLevelCode && someLevel.value.available) {
        return someLevel.value.code
      }
    }
    // no levels were available so can only fall back to prison’s default level
    return defaultLevelCode
  }
}
