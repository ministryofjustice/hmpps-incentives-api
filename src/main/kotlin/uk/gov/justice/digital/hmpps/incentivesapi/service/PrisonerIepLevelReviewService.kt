package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.first
import org.springframework.stereotype.Service

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService
) {
  suspend fun getPrisonerIepLevelHistory(bookingId: Long): IepSummary {
    return prisonApiService.getIEPSummaryPerPrisoner(listOf(bookingId)).first()
  }
}
