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
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIncentiveLevelRepository
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
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService,
  private val prisonerIncentiveLevelRepository: PrisonerIncentiveLevelRepository,
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
      offenderSearchService.getOffendersAtLocation(prisonId, cellLocationPrefix)
    }
    val deferredLocationDescription = async {
      try {
        prisonApiService.getLocation(cellLocationPrefix.removeSuffix("-")).description
      } catch (e: NotFound) {
        "Unknown location"
      }
    }

    val offenders = deferredOffenders.await()

    val bookingIds = offenders.map(OffenderSearchPrisoner::bookingId)

    val deferredBehaviourCaseNotesSinceLastReview = async {
      val reviews = prisonerIncentiveLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds = bookingIds)
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
        IncentiveLevelReview(
          prisonerNumber = it.prisonerNumber,
          bookingId = it.bookingId,
          firstName = WordUtils.capitalizeFully(it.firstName),
          lastName = WordUtils.capitalizeFully(it.lastName),
          levelCode = incentiveLevels[it.bookingId]!!.levelCode,
          positiveBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(it.bookingId, "POS")]?.totalCaseNotes ?: 0,
          negativeBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(it.bookingId, "NEG")]?.totalCaseNotes ?: 0,
          hasAcctOpen = it.hasAcctOpen,
          isNewToPrison = daysSinceLastRealReview[it.bookingId] == null,
          nextReviewDate = nextReviewDates[it.bookingId]!!,
          daysSinceLastReview = daysSinceLastRealReview[it.bookingId],
        )
      }

    val comparator = sort.orDefault() comparingIn order
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

    val prisonIncentiveLevels = prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)
    val levels = prisonIncentiveLevels.map { prisonIncentiveLevel ->
      IncentiveReviewLevel(
        levelCode = prisonIncentiveLevel.levelCode,
        levelName = prisonIncentiveLevel.levelName,
        reviewCount = prisonersCounts[prisonIncentiveLevel.levelCode] ?: 0,
        overdueCount = overdueCounts[prisonIncentiveLevel.levelCode] ?: 0,
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

  private suspend fun getIncentiveLevelsForOffenders(bookingIds: List<Long>): Map<Long, IncentiveReview> =
    prisonerIncentiveLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .toMap(IncentiveReview::bookingId)
}

@Suppress("unused") // not all enum variants are referred to in code
enum class IncentiveReviewSort(
  val field: String,
  private val selector: (IncentiveLevelReview) -> Comparable<*>,
  private val defaultOrder: Sort.Direction,
) {
  NEXT_REVIEW_DATE("nextReviewDate", IncentiveLevelReview::nextReviewDate, Sort.Direction.ASC),
  DAYS_SINCE_LAST_REVIEW("daysSinceLastReview", { it.daysSinceLastReview ?: Int.MAX_VALUE }, Sort.Direction.DESC),
  FIRST_NAME("firstName", IncentiveLevelReview::firstName, Sort.Direction.ASC),
  LAST_NAME("lastName", IncentiveLevelReview::lastName, Sort.Direction.ASC),
  PRISONER_NUMBER("prisonerNumber", IncentiveLevelReview::prisonerNumber, Sort.Direction.ASC),
  POSITIVE_BEHAVIOURS("positiveBehaviours", IncentiveLevelReview::positiveBehaviours, Sort.Direction.DESC),
  NEGATIVE_BEHAVIOURS("negativeBehaviours", IncentiveLevelReview::negativeBehaviours, Sort.Direction.DESC),
  HAS_ACCT_OPEN("hasAcctOpen", IncentiveLevelReview::hasAcctOpen, Sort.Direction.DESC),
  IS_NEW_TO_PRISON("isNewToPrison", IncentiveLevelReview::isNewToPrison, Sort.Direction.DESC),
  ;

  infix fun comparingIn(order: Sort.Direction?): Comparator<IncentiveLevelReview> =
    if ((order ?: defaultOrder).isDescending) {
      compareBy(selector).reversed()
    } else {
      compareBy(selector)
    }
}

fun IncentiveReviewSort?.orDefault() = this ?: IncentiveReviewSort.NEXT_REVIEW_DATE
