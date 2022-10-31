package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel

@Service
class IepLevelService(
  private val prisonApiService: PrisonApiService,
) {

  @Transactional(readOnly = true)
  suspend fun getIepLevelsForPrison(prisonId: String, useClientCredentials: Boolean = false): List<IepLevel> {
    return prisonApiService.getIepLevelsForPrison(prisonId, useClientCredentials)
      .toList()
      .sortedWith(compareBy(IepLevel::sequence))
  }

  /**
   * Because not all levels are enabled at all prisons, and we want to maintain a
   * person’s level when they are transferred, we need to find the "nearest highest" level
   * according to HMPPS policy. Falls back to prison's default level when there's nothing else to do.
   */
  suspend fun findNearestHighestLevel(prisonId: String, levelCode: String): String {
    var maybeDefaultLevelCode: String? = null
    val levelCodesAvailableInPrison = getIepLevelsForPrison(prisonId, true)
      .filter { it.active }
      .map {
        if (it.default) {
          maybeDefaultLevelCode = it.iepLevel
        }
        it.iepLevel
      }
    val defaultLevelCode = maybeDefaultLevelCode
      // NOMIS traditionally takes the first if no default was found
      ?: levelCodesAvailableInPrison.firstOrNull()
      // there are no available incentive levels at all at this prison
      ?: throw DataIntegrityException("$prisonId has no available incentive levels")

    data class KnownLevel(val code: String, val available: Boolean)
    val allKnownLevels = prisonApiService.getIepLevels().toList()
      .sortedBy { it.sequence }
      .map {
        KnownLevel(
          code = it.iepLevel,
          available = it.active && levelCodesAvailableInPrison.contains(it.iepLevel),
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
