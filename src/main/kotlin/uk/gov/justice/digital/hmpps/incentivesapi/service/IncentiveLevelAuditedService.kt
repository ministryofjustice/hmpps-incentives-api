package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel as IncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelUpdate as IncentiveLevelUpdateDTO

/**
 * Audited facade for IncentiveLevelService
 *
 * Publishes HMPPS domain events and audit messages when modifications are made
 */
@Service
class IncentiveLevelAuditedService(
  private val clock: Clock,
  private val auditService: AuditService,
  private val snsService: SnsService,
  incentiveLevelRepository: IncentiveLevelRepository,
  prisonIncentiveLevelAuditedService: PrisonIncentiveLevelAuditedService,
  featureFlagsService: FeatureFlagsService,
  prisonApiService: PrisonApiService,
) : IncentiveLevelService(
  clock = clock,
  incentiveLevelRepository = incentiveLevelRepository,
  prisonIncentiveLevelService = prisonIncentiveLevelAuditedService,
  featureFlagsService,
  prisonApiService,
) {
  override suspend fun createIncentiveLevel(dto: IncentiveLevelDTO): IncentiveLevelDTO {
    return super.createIncentiveLevel(dto)
      .also {
        auditService.sendMessage(AuditType.INCENTIVE_LEVEL_ADDED, it.code, it)
        publishIncentiveLevelChangeEvent(it.code)
      }
  }

  override suspend fun updateIncentiveLevel(code: String, update: IncentiveLevelUpdateDTO): IncentiveLevelDTO? {
    return super.updateIncentiveLevel(code, update)
      ?.also {
        auditService.sendMessage(AuditType.INCENTIVE_LEVEL_UPDATED, it.code, it)
        publishIncentiveLevelChangeEvent(it.code)
      }
  }

  override suspend fun setOrderOfIncentiveLevels(incentiveLevelCodes: List<String>): List<IncentiveLevelDTO> {
    return super.setOrderOfIncentiveLevels(incentiveLevelCodes)
      .also {
        auditService.sendMessage(
          AuditType.INCENTIVE_LEVELS_REORDERED,
          incentiveLevelCodes.joinToString(", "),
          incentiveLevelCodes,
        )
        publishIncentiveLevelReorderEvent()
      }
  }

  private suspend fun publishIncentiveLevelChangeEvent(levelCode: String) {
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
      additionalInformation = null,
    )
  }
}
