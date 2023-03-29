package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevelUpdate
import java.time.Clock
import java.time.LocalDateTime

@Service
class IncentiveLevelCurator(
  private val incentiveLevelService: IncentiveLevelService,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService,
  private val auditService: AuditService,
  private val snsService: SnsService,
  private val clock: Clock,
) {
  suspend fun createIncentiveLevel(incentiveLevel: IncentiveLevel): IncentiveLevel {
    val result = incentiveLevelService.createIncentiveLevel(incentiveLevel)
    auditService.sendMessage(AuditType.INCENTIVE_LEVEL_ADDED, result.updatedLevel.code, result.updatedLevel)
    publishIncentiveLevelChangeEvent(result.updatedLevel.code)

    result.updatedPrisons.forEach { prisonIncentiveLevel ->
      auditAndPublishPrisonLevelChangeEvent(prisonIncentiveLevel)
    }
    return result.updatedLevel
  }

  suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdate): IncentiveLevel? {
    return incentiveLevelService.updateIncentiveLevel(code, update)
      ?.let { result ->
        auditService.sendMessage(AuditType.INCENTIVE_LEVEL_UPDATED, code, result.updatedLevel)
        publishIncentiveLevelChangeEvent(code)
        result.updatedPrisons.forEach { prisonIncentiveLevel ->
          auditAndPublishPrisonLevelChangeEvent(prisonIncentiveLevel)
        }
        result.updatedLevel
      }
  }

  suspend fun setOrderOfIncentiveLevels(incentiveLevelCodes: List<String>): List<IncentiveLevel> {
    val orderOfIncentiveLevels = incentiveLevelService.setOrderOfIncentiveLevels(incentiveLevelCodes)
    auditService.sendMessage(
      AuditType.INCENTIVE_LEVELS_REORDERED,
      incentiveLevelCodes.joinToString(", "),
      incentiveLevelCodes,
    )
    publishIncentiveLevelReorderEvent()
    return orderOfIncentiveLevels
  }

  suspend fun updatePrisonIncentiveLevel(
    prisonId: String,
    levelCode: String,
    update: PrisonIncentiveLevelUpdate,
  ): PrisonIncentiveLevel? {
    val result = prisonIncentiveLevelService.updatePrisonIncentiveLevel(prisonId, levelCode, update)
      ?.let {
        auditAndPublishPrisonLevelChangeEvent(it)
        it
      }
    return result
  }

  private suspend fun auditAndPublishPrisonLevelChangeEvent(prisonIncentiveLevel: PrisonIncentiveLevel) {
    auditService.sendMessage(
      AuditType.PRISON_INCENTIVE_LEVEL_UPDATED,
      "${prisonIncentiveLevel.prisonId} - ${prisonIncentiveLevel.levelCode}",
      prisonIncentiveLevel,
    )

    snsService.publishDomainEvent(
      IncentivesDomainEventType.INCENTIVE_PRISON_LEVEL_CHANGED,
      "Incentive level (${prisonIncentiveLevel.levelCode}) in prison ${prisonIncentiveLevel.prisonId} has been updated",
      occurredAt = LocalDateTime.now(clock),
      AdditionalInformation(
        incentiveLevel = prisonIncentiveLevel.levelCode,
        prisonId = prisonIncentiveLevel.prisonId,
      ),
    )

  }

  private suspend fun publishIncentiveLevelChangeEvent(
    levelCode: String,
  ) {
    snsService.publishDomainEvent(
      IncentivesDomainEventType.INCENTIVE_LEVEL_CHANGED,
      "An incentive level has been changed: $levelCode",
      occurredAt = LocalDateTime.now(clock),
      AdditionalInformation(
        incentiveLevel = levelCode,
      ),
    )
  }

  private suspend fun publishIncentiveLevelReorderEvent() {
    snsService.publishDomainEvent(
      IncentivesDomainEventType.INCENTIVE_LEVELS_REORDERED,
      "Incentive levels have been re-ordered",
      occurredAt = LocalDateTime.now(clock),
    )
  }
}