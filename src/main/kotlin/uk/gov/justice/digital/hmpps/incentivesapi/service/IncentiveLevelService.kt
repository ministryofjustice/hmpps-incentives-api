package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate as IncentiveLevelUpdateDTO

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

  @Transactional
  suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdateDTO): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)
      ?.let {
        incentiveLevelRepository.save(it.withUpdate(update))
      }
      ?.toDTO()
  }
}

private fun IncentiveLevel.withUpdate(update: IncentiveLevelUpdateDTO): IncentiveLevel = copy(
  code = code,
  description = update.description ?: description,
  active = update.active ?: active,
)

private fun IncentiveLevel.toDTO(): IncentiveLevelDTO = IncentiveLevelDTO(
  code = code,
  description = description,
  active = active,
)

private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
