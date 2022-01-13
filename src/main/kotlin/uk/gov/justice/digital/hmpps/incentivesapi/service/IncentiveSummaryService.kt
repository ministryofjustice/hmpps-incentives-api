package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.data.IncentiveSummary

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiService
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
  fun getIncentivesSummaryByLocation(prisonId: String, locationId: String): IncentiveSummary {
    val findPrisonersAtLocation = prisonApiService.findPrisonersAtLocation(prisonId, locationId)

    return IncentiveSummary(
      prisonId = prisonId,
      locationId = locationId,
      locationDescription = locationId
    )
  }
}
