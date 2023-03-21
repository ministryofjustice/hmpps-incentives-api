package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataWithCodeFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.associateByTo
import java.time.Clock
import java.time.LocalDateTime
import javax.validation.ValidationException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate as IncentiveLevelUpdateDTO

@Service
class IncentiveLevelService(
  private val clock: Clock,
  private val incentiveLevelRepository: IncentiveLevelRepository,
) {
  suspend fun getAllIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByOrderBySequence().toListOfDTO()
  }

  suspend fun getActiveIncentiveLevels(): List<IncentiveLevelDTO> {
    return incentiveLevelRepository.findAllByActiveIsTrueOrderBySequence().toListOfDTO()
  }

  suspend fun getIncentiveLevel(code: String): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)?.toDTO()
  }

  @Transactional
  suspend fun createIncentiveLevel(dto: IncentiveLevelDTO): IncentiveLevelDTO {
    if (incentiveLevelRepository.existsById(dto.code)) {
      throw ValidationException("Incentive level with code ${dto.code} already exists")
    }
    val highestSequence = incentiveLevelRepository.findMaxSequence() ?: 0
    val incentiveLevel = dto.toNewEntity(clock, highestSequence + 1)
    return incentiveLevelRepository.save(incentiveLevel).toDTO()
  }

  @Transactional
  suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdateDTO): IncentiveLevelDTO? {
    return incentiveLevelRepository.findById(code)
      ?.let {
        incentiveLevelRepository.save(it.withUpdate(clock, update))
      }
      ?.toDTO()
  }

  @Transactional
  suspend fun setOrderOfIncentiveLevels(incentiveLevelCodes: List<String>): List<IncentiveLevelDTO> {
    val allIncentiveLevels = mutableMapOf<String, IncentiveLevel>()
    incentiveLevelRepository.findAll().associateByTo(allIncentiveLevels, IncentiveLevel::code)

    val allIncentiveLevelsInDesiredOrder = incentiveLevelCodes.map { code ->
      allIncentiveLevels.remove(code)
        ?: throw NoDataWithCodeFoundException("incentive level", code)
    }
    if (allIncentiveLevels.isNotEmpty()) {
      val missing = allIncentiveLevels.keys.joinToString("`, `", prefix = "`", postfix = "`")
      throw ValidationException("All incentive levels required when setting order. Missing: $missing")
    }

    val incentiveLevelsWithNewSequences = allIncentiveLevelsInDesiredOrder.mapIndexed { index, incentiveLevel ->
      incentiveLevel.copy(
        sequence = index + 1,

        new = false,
        whenUpdated = LocalDateTime.now(clock),
      )
    }

    return incentiveLevelRepository.saveAll(incentiveLevelsWithNewSequences).toListOfDTO()
  }
}

// conversions between IncentiveLevel entity and IncentiveLevel data transfer object are _only_ done in this service

private fun IncentiveLevel.withUpdate(clock: Clock, update: IncentiveLevelUpdateDTO): IncentiveLevel = copy(
  code = code,
  description = update.description ?: description,
  active = update.active ?: active,

  new = false,
  whenUpdated = LocalDateTime.now(clock),
)

private fun IncentiveLevel.toDTO(): IncentiveLevelDTO = IncentiveLevelDTO(
  code = code,
  description = description,
  active = active,
)

private fun IncentiveLevelDTO.toNewEntity(clock: Clock, sequence: Int): IncentiveLevel = IncentiveLevel(
  code = code,
  description = description,
  sequence = sequence,
  active = active,

  new = true,
  whenUpdated = LocalDateTime.now(clock),
)

private suspend fun Flow<IncentiveLevel>.toListOfDTO(): List<IncentiveLevelDTO> = map { it.toDTO() }.toList()
