package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository

@Service
class IepLevelService(
  private val iepLevelRepository: IepLevelRepository,
  private val prisonApiService: PrisonApiService,
) {

  @Transactional(readOnly = true)
  suspend fun getIepLevelsForPrison(prisonId: String): List<IepLevel> {
    val iepLevelMap = iepLevelRepository.findAll().toList().associateBy { it.iepCode }

    return prisonApiService.getIepLevelsForPrison(prisonId)
      .filter { iepLevelMap[it.iepLevel]?.active == true }
      .toList()
      .sortedWith(compareBy(IepLevel::sequence))
  }
}
