package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.LocalDate

/**
 * Get the next review dates for the given offender(s). Calculates/persists these if not present in the database.
 */
@Service
class NextReviewDateGetterService(
  private val nextReviewDateRepository: NextReviewDateRepository,
  private val prisonApiService: PrisonApiService,
  private val offenderSearchService: OffenderSearchService,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {

  suspend fun get(bookingId: Long): LocalDate {
    return nextReviewDateRepository.findById(bookingId)?.nextReviewDate ?: run {
      val locationInfo = prisonApiService.getPrisonerInfo(bookingId, useClientCredentials = true)
      val prisonerNumber = locationInfo.offenderNo
      val offender = offenderSearchService.getOffender(prisonerNumber)

      return getMany(listOf(offender))[offender.bookingId]!!
    }
  }

  /**
   * Get the next review dates for the given offender(s)
   *
   * Tries to get next review dates from the database. For bookingIds without a next review date record it calculates
   * and persist the next review dates before returning it with the ones already present.
   *
   * @param offenders is the list of offenders to get the next review dates for
   *
   * @return a Map with bookingIds as keys and next review dates as values
   */
  suspend fun getMany(offenders: List<OffenderSearchPrisoner>): Map<Long, LocalDate> {
    val bookingIds = offenders.map(OffenderSearchPrisoner::bookingId)
    var existingNextReviewDates = nextReviewDateRepository.findAllById(bookingIds).toList().toMap()

    val bookingIdsWithDate = existingNextReviewDates.keys
    val bookingIdsWithoutDate = bookingIds.subtract(bookingIdsWithDate)

    val offendersWithoutDate = offenders.filter { offender -> offender.bookingId in bookingIdsWithoutDate }

    val newNextReviewDates = nextReviewDateUpdaterService.updateMany(offendersWithoutDate)

    return existingNextReviewDates + newNextReviewDates
  }
}
