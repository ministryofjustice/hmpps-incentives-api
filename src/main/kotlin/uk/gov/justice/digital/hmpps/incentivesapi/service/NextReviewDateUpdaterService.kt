package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDate

/**
 * Recalculates and persists next review dates
 */
@Service
class NextReviewDateUpdaterService(
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val nextReviewDateRepository: NextReviewDateRepository,
  private val prisonApiService: PrisonApiService,
) {

  /**
   * Update next review date for the given offenders
   *
   * @param offenders is the list of offenders to update
   *
   * @return a map with bookingIds as keys and nextReviewDate as value
   * */
  suspend fun updateMany(offenders: List<OffenderSearchPrisoner>): Map<Long, LocalDate> {
    if (offenders.isEmpty()) {
      return emptyMap()
    }

    val offendersMap = offenders.associateBy(OffenderSearchPrisoner::bookingId)
    val bookingIds = offendersMap.keys.toList()

    val iepLevels: Map<String, IepLevel> = prisonApiService.getIncentiveLevels()

    // NOTE: This is to account for bookingIds potentially without any review record
    val bookingIdsNoReviews = bookingIds.associateWith { emptyList<PrisonerIepLevel>() }
    val reviewsMap = bookingIdsNoReviews + prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
      .toList()
      .groupBy(PrisonerIepLevel::bookingId)

    val nextReviewDateRecords = reviewsMap.map {
      val bookingId = it.key
      val iepDetails = it.value.toIepDetails(iepLevels)
      val offender = offendersMap[bookingId]!!

      val nextReviewDate = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender.dateOfBirth,
          receptionDate = offender.receptionDate,
          hasAcctOpen = offender.acctOpen,
          iepDetails = iepDetails,
        )
      ).calculate()

      NextReviewDate(
        bookingId = bookingId,
        nextReviewDate = nextReviewDate,
      )
    }

    nextReviewDateRepository.saveAll(nextReviewDateRecords)

    return nextReviewDateRecords.toMap()
  }
}

fun List<PrisonerIepLevel>.toIepDetails(iepLevels: Map<String, IepLevel>): List<IepDetail> {
  return this.map { review ->
    IepDetail(
      bookingId = review.bookingId,
      prisonerNumber = review.prisonerNumber,
      iepCode = review.iepCode,
      iepLevel = iepLevels[review.iepCode]?.iepDescription ?: "Unmapped",
      iepTime = review.reviewTime,
      iepDate = review.reviewTime.toLocalDate(),
      agencyId = review.prisonId,
      userId = review.reviewedBy,
    )
  }
}

fun List<NextReviewDate>.toMap(): Map<Long, LocalDate> {
  return this.associate { record -> Pair(record.bookingId, record.nextReviewDate) }
}
