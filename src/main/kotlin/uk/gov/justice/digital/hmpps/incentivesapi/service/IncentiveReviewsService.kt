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
import java.time.LocalDateTime
import java.util.Comparator

@Service
class IncentiveReviewsService(
  private val offenderSearchService: OffenderSearchService,
  private val prisonApiService: PrisonApiService,
  private val iepLevelService: IepLevelService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val nextReviewDateGetterService: NextReviewDateGetterService,
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
      getCaseNoteUsageByLastReviewDate(listOf("POS", "NEG"), getLastRealReviewForOffenders(bookingIds))
    }

    val deferredIncentiveLevels = async { getIncentiveLevelsForOffenders(bookingIds) }
    val deferredNextReviewDates = async { nextReviewDateGetterService.getMany(offenders) }

    val incentiveLevels = deferredIncentiveLevels.await()
    val bookingIdsMissingIncentiveLevel = bookingIds subtract incentiveLevels.keys
    if (bookingIdsMissingIncentiveLevel.isNotEmpty()) {
      throw ListOfDataNotFoundException("incentive levels", bookingIdsMissingIncentiveLevel)
    }

    val nextReviewDates = deferredNextReviewDates.await()
    val behaviourCaseNotesSinceLastReview = deferredBehaviourCaseNotesSinceLastReview.await()

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
          nextReviewDate = nextReviewDates[it.bookingId]!!,
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
      if (nextReviewDates[review.bookingId]!!.isBefore(LocalDate.now(clock))) {
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

  private suspend fun getLastRealReviewForOffenders(bookingIds: List<Long>): Map<Long, LocalDateTime> =
    prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
      .toList()
      .groupBy { it.bookingId }
      .map { review ->
        val latestReview = review.value.firstOrNull(PrisonerIepLevel::isRealReview) ?: review.value.first()
        review.key to latestReview
      }.associate {
        it.first to it.second.reviewTime
      }

  private suspend fun getIncentiveLevelsForOffenders(bookingIds: List<Long>): Map<Long, PrisonerIepLevel> =
    prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .toMap(keySelector = PrisonerIepLevel::bookingId)

  private suspend fun getCaseNoteUsageByLastReviewDate(caseNoteTypes: List<String>, prisonerLastReviews: Map<Long, LocalDateTime>) =
    prisonApiService.retrieveCaseNoteCountsByFromDate(caseNoteTypes, prisonerLastReviews)
      .toList()
      .groupBy(PrisonerCaseNoteByTypeSubType::toKey)
      .map { cn ->
        PrisonerCaseNoteSummary(
          key = cn.key,
          totalCaseNotes = calcTypeCount(cn.value),
        )
      }.associateBy { it.key }
  private fun calcTypeCount(caseNoteUsage: List<PrisonerCaseNoteByTypeSubType>): Int =
    caseNoteUsage.map { it.numCaseNotes }.fold(0) { acc, next -> acc + next }
}

@Suppress("unused") // not all enum variants are referred to in code
enum class IncentiveReviewSort(
  val field: String,
  private val selector: (IncentiveReview) -> Comparable<*>,
  private val defaultOrder: Sort.Direction,
) {
  NEXT_REVIEW_DATE("nextReviewDate", IncentiveReview::nextReviewDate, Sort.Direction.ASC),
  FIRST_NAME("firstName", IncentiveReview::firstName, Sort.Direction.ASC),
  LAST_NAME("lastName", IncentiveReview::lastName, Sort.Direction.ASC),
  PRISONER_NUMBER("prisonerNumber", IncentiveReview::prisonerNumber, Sort.Direction.ASC),
  POSITIVE_BEHAVIOURS("positiveBehaviours", IncentiveReview::positiveBehaviours, Sort.Direction.DESC),
  NEGATIVE_BEHAVIOURS("negativeBehaviours", IncentiveReview::negativeBehaviours, Sort.Direction.DESC),
  HAS_ACCT_OPEN("hasAcctOpen", IncentiveReview::hasAcctOpen, Sort.Direction.DESC);

  companion object {
    fun orDefault(sort: IncentiveReviewSort?) = sort ?: NEXT_REVIEW_DATE
  }

  infix fun comparingIn(order: Sort.Direction?): Comparator<IncentiveReview> = if ((order ?: defaultOrder).isDescending) {
    compareBy(selector).reversed()
  } else {
    compareBy(selector)
  }
}

data class PrisonerCaseNoteByTypeSubType(
  val bookingId: Long,
  val caseNoteType: String,
  val caseNoteSubType: String,
  val numCaseNotes: Int,
)

fun PrisonerCaseNoteByTypeSubType.toKey() = BookingTypeKey(bookingId, caseNoteType)

data class PrisonerCaseNoteSummary(
  val key: BookingTypeKey,
  val totalCaseNotes: Int,
)

data class BookingTypeKey(
  val bookingId: Long,
  val caseNoteType: String,
)
