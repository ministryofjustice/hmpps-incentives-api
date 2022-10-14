package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepPrisonRepository
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel

@Service
class IepLevelService(
  private val iepLevelRepository: IepLevelRepository,
  private val iepPrisonRepository: IepPrisonRepository
) {

  @Transactional(readOnly = true)
  suspend fun getIepLevelsForPrison(prisonId: String): List<IepLevel> {
    val iepLevelMap = iepLevelRepository.findAll().toList().associateBy { it.iepCode }

    return iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)
      .filter { iepLevelMap[it.iepCode]?.active == true }
      .map {
        val iepLevel = iepLevelMap[it.iepCode]!!
        IepLevel(
          iepLevel = it.iepCode,
          iepDescription = iepLevel.iepDescription,
          sequence = iepLevel.sequence,
          default = it.defaultIep
        )
      }
      .toList()
      .sortedWith(compareBy(IepLevel::sequence))
  }
}
