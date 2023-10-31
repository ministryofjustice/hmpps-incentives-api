package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIncentiveLevelRepository

@Service
class CountPrisonersService(
  private val prisonerIncentiveLevelRepository: PrisonerIncentiveLevelRepository,
  private val offenderSearchService: OffenderSearchService,
) {
  /**
   * True if there are currently any prisoners on a given incentive level at a prison
   */
  suspend fun prisonersExistOnLevelInPrison(prisonId: String, levelCode: String): Boolean {
    var prisonersExistOnLevel = false
    offenderSearchService.findOffendersAtLocation(prisonId)
      .takeWhile { offenders ->
        val bookingIds = offenders.map { it.bookingId }
        prisonersExistOnLevel = if (bookingIds.isEmpty()) {
          false
        } else {
          prisonerIncentiveLevelRepository.somePrisonerCurrentlyOnLevel(bookingIds, levelCode)
        }
        !prisonersExistOnLevel
      }
      .collect()
    return prisonersExistOnLevel
  }
}
