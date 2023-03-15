package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO

@Service
class IncentiveLevelService(
  private val incentiveLevelRepository: IncentiveLevelRepository,
) {
  suspend fun getIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByOrderBySequence().toListOfDTO()
  }

  suspend fun getActiveIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByActiveIsTrueOrderBySequence().toListOfDTO()
  }

  suspend fun getIncentiveLevel(code: String): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)?.toDTO()
  }
}

private fun IncentiveLevel.toDTO(): IncentiveLevelDTO = IncentiveLevelDTO(
  code = code,
  description = description,
  active = active,
)

private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
