package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.apache.commons.text.WordUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.incentivesapi.config.ListOfDataNotFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.toMap
import uk.gov.justice.digital.hmpps.incentivesapi.util.paginateWith
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Comparator

@Service
class IncentiveReviewsService(
  private val offenderSearchService: OffenderSearchService,
  private val prisonApiService: PrisonApiService,
  private val iepLevelService: IepLevelService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val nextReviewDateGetterService: NextReviewDateGetterService,
  private val behaviourService: BehaviourService,
  private val clock: Clock,
) {
  companion object {
    const val DEFAULT_PAGE_SIZE = 20
  }

  /**
   * Returns incentive review information for a given location within a prison and on a given level
   * NB: page is 0-based
   */
  suspend fun reviews(
    prisonId: String,
    cellLocationPrefix: String,
    levelCode: String,
    sort: IncentiveReviewSort? = null,
    order: Sort.Direction? = null,
    page: Int = 0,
    size: Int = DEFAULT_PAGE_SIZE,
  ): IncentiveReviewResponse = coroutineScope {
    val deferredOffenders = async {
      // all offenders at location are required to determine total number with overdue reviews
      offenderSearchService.findOffenders(prisonId, cellLocationPrefix)
    }
    val deferredLocationDescription = async {
      try {
        prisonApiService.getLocation(cellLocationPrefix).description
      } catch (e: NotFound) {
        "Unknown location"
      }
    }

    val offenders = deferredOffenders.await()

    val bookingIds = offenders.map(OffenderSearchPrisoner::bookingId)

    val deferredBehaviourCaseNotesSinceLastReview = async {
      val reviews = prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds = bookingIds)
      behaviourService.getBehaviours(reviews.toList())
    }
    val deferredIncentiveLevels = async { getIncentiveLevelsForOffenders(bookingIds) }
    val deferredNextReviewDates = async { nextReviewDateGetterService.getMany(offenders) }

    val incentiveLevels = deferredIncentiveLevels.await()
    val bookingIdsMissingIncentiveLevel = bookingIds subtract incentiveLevels.keys
    if (bookingIdsMissingIncentiveLevel.isNotEmpty()) {
      throw ListOfDataNotFoundException("incentive levels", bookingIdsMissingIncentiveLevel)
    }

    val nextReviewDates = deferredNextReviewDates.await()
    val (behaviourCaseNotesSinceLastReview, lastRealReviews) = deferredBehaviourCaseNotesSinceLastReview.await()

    val today = LocalDate.now(clock)
    val daysSinceLastRealReview = lastRealReviews.mapValues { (_, lastRealReviewDate) ->
      lastRealReviewDate?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today).toInt() }
    }

    val allReviews = offenders
      .map {
        IncentiveReview(
          prisonerNumber = it.prisonerNumber,
          bookingId = it.bookingId,
          firstName = WordUtils.capitalizeFully(it.firstName),
          lastName = WordUtils.capitalizeFully(it.lastName),
          levelCode = incentiveLevels[it.bookingId]!!.iepCode,
          positiveBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(it.bookingId, "POS")]?.totalCaseNotes ?: 0,
          negativeBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(it.bookingId, "NEG")]?.totalCaseNotes ?: 0,
          hasAcctOpen = it.hasAcctOpen,
          isNewToPrison = daysSinceLastRealReview[it.bookingId] == null,
          nextReviewDate = nextReviewDates[it.bookingId]!!,
          daysSinceLastReview = daysSinceLastRealReview[it.bookingId],
        )
      }

    val comparator = IncentiveReviewSort.orDefault(sort) comparingIn order
    val reviewsAtLevel = allReviews
      .filter { it.levelCode == levelCode }
      .sortedWith(comparator)

    // Count overdue reviews and total reviews by Incentive levels
    val prisonersCounts: MutableMap<String, Int> = mutableMapOf()
    val overdueCounts: MutableMap<String, Int> = mutableMapOf()
    allReviews.forEach { review ->
      val currentReviewLevel = review.levelCode

      if (!prisonersCounts.containsKey(currentReviewLevel)) {
        prisonersCounts[currentReviewLevel] = 0
      }
      if (!overdueCounts.containsKey(currentReviewLevel)) {
        overdueCounts[currentReviewLevel] = 0
      }

      prisonersCounts[currentReviewLevel] = prisonersCounts[currentReviewLevel]!! + 1
      if (nextReviewDates[review.bookingId]!!.isBefore(today)) {
        overdueCounts[currentReviewLevel] = overdueCounts[currentReviewLevel]!! + 1
      }
    }

    val prisonLevels = iepLevelService.getIepLevelsForPrison(prisonId, useClientCredentials = true)
    val levels: List<IncentiveReviewLevel> = prisonLevels.map { iepLevel ->
      IncentiveReviewLevel(
        levelCode = iepLevel.iepLevel,
        levelName = iepLevel.iepDescription,
        reviewCount = prisonersCounts[iepLevel.iepLevel] ?: 0,
        overdueCount = overdueCounts[iepLevel.iepLevel] ?: 0,
      )
    }

    val reviewsPage = reviewsAtLevel paginateWith PageRequest.of(page, size)
    val locationDescription = deferredLocationDescription.await()
    IncentiveReviewResponse(
      locationDescription = locationDescription,
      levels = levels,
      reviews = reviewsPage,
    )
  }

  private suspend fun getIncentiveLevelsForOffenders(bookingIds: List<Long>): Map<Long, PrisonerIepLevel> =
    prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .toMap(PrisonerIepLevel::bookingId)
}

@Suppress("unused") // not all enum variants are referred to in code
enum class IncentiveReviewSort(
  val field: String,
  private val selector: (IncentiveReview) -> Comparable<*>,
  private val defaultOrder: Sort.Direction,
) {
  NEXT_REVIEW_DATE("nextReviewDate", IncentiveReview::nextReviewDate, Sort.Direction.ASC),
  DAYS_SINCE_LAST_REVIEW("daysSinceLastReview", { it.daysSinceLastReview ?: Int.MAX_VALUE }, Sort.Direction.DESC),
  FIRST_NAME("firstName", IncentiveReview::firstName, Sort.Direction.ASC),
  LAST_NAME("lastName", IncentiveReview::lastName, Sort.Direction.ASC),
  PRISONER_NUMBER("prisonerNumber", IncentiveReview::prisonerNumber, Sort.Direction.ASC),
  POSITIVE_BEHAVIOURS("positiveBehaviours", IncentiveReview::positiveBehaviours, Sort.Direction.DESC),
  NEGATIVE_BEHAVIOURS("negativeBehaviours", IncentiveReview::negativeBehaviours, Sort.Direction.DESC),
  HAS_ACCT_OPEN("hasAcctOpen", IncentiveReview::hasAcctOpen, Sort.Direction.DESC),
  IS_NEW_TO_PRISON("isNewToPrison", IncentiveReview::isNewToPrison, Sort.Direction.DESC);

  companion object {
    fun orDefault(sort: IncentiveReviewSort?) = sort ?: NEXT_REVIEW_DATE
  }

  infix fun comparingIn(order: Sort.Direction?): Comparator<IncentiveReview> =
    if ((order ?: defaultOrder).isDescending) {
      compareBy(selector).reversed()
    } else {
      compareBy(selector)
    }
}
