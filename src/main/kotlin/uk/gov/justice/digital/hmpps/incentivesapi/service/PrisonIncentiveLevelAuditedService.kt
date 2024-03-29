package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel as PrisonIncentiveLevelDTO
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevelUpdate as PrisonIncentiveLevelUpdateDTO

/**
 * Audited facade for PrisonIncentiveLevelAuditedService
 *
 * Publishes HMPPS domain events and audit messages when modifications are made
 */
@Service
class PrisonIncentiveLevelAuditedService(
  private val clock: Clock,
  private val auditService: AuditService,
  private val snsService: SnsService,
  incentiveLevelRepository: IncentiveLevelRepository,
  prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository,
  countPrisonersService: CountPrisonersService,
) : PrisonIncentiveLevelService(
  clock = clock,
  incentiveLevelRepository = incentiveLevelRepository,
  prisonIncentiveLevelRepository = prisonIncentiveLevelRepository,
  countPrisonersService = countPrisonersService,
) {
  override suspend fun updatePrisonIncentiveLevel(
    prisonId: String,
    levelCode: String,
    update: PrisonIncentiveLevelUpdateDTO,
  ): PrisonIncentiveLevelDTO? {
    return super.updatePrisonIncentiveLevel(prisonId, levelCode, update)
      ?.also {
        auditAndPublishPrisonLevelChangeEvent(it)
      }
  }

  override suspend fun deactivateAllPrisonIncentiveLevels(prisonId: String): List<PrisonIncentiveLevelDTO> {
    return super.deactivateAllPrisonIncentiveLevels(prisonId)
      .onEach {
        auditAndPublishPrisonLevelChangeEvent(it)
      }
  }

  private suspend fun auditAndPublishPrisonLevelChangeEvent(prisonIncentiveLevel: PrisonIncentiveLevelDTO) {
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
}
