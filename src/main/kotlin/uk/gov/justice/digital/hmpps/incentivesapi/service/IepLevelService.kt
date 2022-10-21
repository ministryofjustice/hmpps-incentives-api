package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
}
