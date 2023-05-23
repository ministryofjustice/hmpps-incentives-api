package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel

@Service
class NearestPrisonIncentiveLevelService(
  private val incentiveLevelService: IncentiveLevelService,
) {
  /**
   * Selects the default level (for new admissions) for a prison
   * NB: this existed largely for compatibility with NOMIS which allowed there to be no default set,
   * business rules now prevent this from happening
   */
  fun chooseDefaultLevel(prisonId: String, prisonLevels: List<PrisonIncentiveLevel>): String {
    val activePrisonLevels = prisonLevels.filter(PrisonIncentiveLevel::active)
    return activePrisonLevels.firstOrNull(PrisonIncentiveLevel::defaultOnAdmission)?.levelCode
      // fall back to standard if available
      ?: activePrisonLevels.firstOrNull { it.levelCode == "STD" }?.levelCode
      // fall back to first available level
      ?: activePrisonLevels.firstOrNull()?.levelCode
      // there are no available incentive levels at all at this prison
      ?: throw DataIntegrityException("$prisonId has no available incentive levels")
  }

  /**
   * Because not all levels are enabled at all prisons, and we want to maintain a
   * person’s level when they are transferred, we need to find the "nearest highest" level
   * according to HMPPS policy. Falls back to prison's default level when there's nothing else to do.
   */
  suspend fun findNearestHighestLevel(prisonId: String, prisonLevels: List<PrisonIncentiveLevel>, levelCode: String): String {
    val defaultLevelCode = chooseDefaultLevel(prisonId, prisonLevels)
    val levelCodesAvailableInPrison = prisonLevels.filter(PrisonIncentiveLevel::active)
      .map(PrisonIncentiveLevel::levelCode)
      .toSet()

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
