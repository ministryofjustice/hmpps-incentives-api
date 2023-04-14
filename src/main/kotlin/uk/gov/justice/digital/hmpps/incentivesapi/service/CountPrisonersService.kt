package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.fold
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository

// TODO: move into PrisonerIepLevelReviewService once IepLevelService is removed
@Service
class CountPrisonersService(
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val offenderSearchService: OffenderSearchService,
) {
  /**
   * Returns the number of prisoners on given level at a prison
   */
  suspend fun countOfPrisonersOnLevelInPrison(prisonId: String, levelCode: String): Int {
    return offenderSearchService.findOffendersAtLocation(prisonId, prisonId)
      .fold(0) { countSoFar, offenders ->
        val bookingIds = offenders.map { it.bookingId }
        countSoFar + prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
          .count {
            it.iepCode == levelCode
          }
      }
  }
}
