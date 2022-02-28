package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepPrisonRepository

@Service
class IepLevelService(
  private val iepLevelRepository: IepLevelRepository,
  private val iepPrisonRepository: IepPrisonRepository
) {

  @Transactional(readOnly = true)
  suspend fun getIepLevelsForPrison(prisonId: String): List<IepLevel> {
    return coroutineScope {
      val iepLevelMap = iepLevelRepository.findAllByActiveIsTrueOrderBySequence().toList().associateBy { it.iepCode }

      withContext(Dispatchers.Default) {
        iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)
          .map {
            val iepLevel = iepLevelMap[it.iepCode]!!
            IepLevel(iepLevel = it.iepCode, iepDescription = iepLevel.iepDescription, sequence = iepLevel.sequence, default = it.defaultIep)
          }
      }.toList().sortedWith(compareBy(IepLevel::sequence))
    }
  }
}
