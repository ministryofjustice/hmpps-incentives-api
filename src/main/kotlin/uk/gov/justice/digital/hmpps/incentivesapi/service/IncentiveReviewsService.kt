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
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.toMap
import uk.gov.justice.digital.hmpps.incentivesapi.util.paginateWith
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Comparator
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview as IncentiveReviewDTO

@Service
class IncentiveReviewsService(
  private val prisonerSearchService: PrisonerSearchService,
  private val locationsService: LocationsService,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService,
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val nextReviewDateGetterService: NextReviewDateGetterService,
  private val behaviourService: BehaviourService,
  private val clock: Clock,
) {

  companion object {
    const val DEFAULT_PAGE_SIZE = 20
    const val POSITIVE_BEHAVIOUR = "POS"
    const val NEGATIVE_BEHAVIOUR = "NEG"
    const val UNKNOWN_LOCATION = "Unknown location"
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
    val deferredPrisoners = async {
      prisonerSearchService.getPrisonersAtLocation(prisonId, cellLocationPrefix)
    }
    val deferredLocationDescription = async {
      getLocationDescription(cellLocationPrefix)
    }

    val prisonIncentiveLevels = prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)
    val prisoners = deferredPrisoners.await()

    if (prisoners.isEmpty()) {
      return@coroutineScope IncentiveReviewResponse(
        locationDescription = deferredLocationDescription.await(),
        levels = buildLevels(prisonIncentiveLevels, emptyMap(), emptyMap()),
        reviews = emptyList(),
      )
    }

    val bookingIds = prisoners.map(Prisoner::bookingId)

    val deferredBehavioursAndLastReviews = async {
      val reviews = incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds = bookingIds)
      behaviourService.getBehaviours(reviews.toList())
    }
    val deferredIncentiveLevels = async { getIncentiveLevelsForPrisoners(bookingIds) }
    val deferredNextReviewDates = async { nextReviewDateGetterService.getMany(prisoners) }

    val incentiveLevels = deferredIncentiveLevels.await()
    val bookingIdsMissingIncentiveLevel = bookingIds subtract incentiveLevels.keys
    if (bookingIdsMissingIncentiveLevel.isNotEmpty()) {
      throw ListOfDataNotFoundException("incentive levels", bookingIdsMissingIncentiveLevel)
    }

    val nextReviewDates = deferredNextReviewDates.await()
    val (behavioursSinceLastReview, lastRealReviews) = deferredBehavioursAndLastReviews.await()
    val today = LocalDate.now(clock)

    val daysSinceLastRealReview = lastRealReviews.mapValues { (_, lastRealReviewDate) ->
      lastRealReviewDate?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today).toInt() }
    }

    val allReviews = prisoners.map { prisoner ->
      toReviewDto(
        prisoner = prisoner,
        incentiveLevels = incentiveLevels,
        behavioursSinceLastReview = behavioursSinceLastReview,
        nextReviewDates = nextReviewDates,
        daysSinceLastRealReview = daysSinceLastRealReview,
      )
    }

    val comparator = sort.orDefault() comparingIn order
    val reviewsForLevel = allReviews
      .filter { it.levelCode == levelCode }
      .sortedWith(comparator)

    val reviewCountsByLevel = allReviews
      .groupingBy { it.levelCode }
      .eachCount()

    val overdueCountsByLevel = allReviews
      .filter { nextReviewDates[it.bookingId]!!.isBefore(today) }
      .groupingBy { it.levelCode }
      .eachCount()

    IncentiveReviewResponse(
      locationDescription = deferredLocationDescription.await(),
      levels = buildLevels(prisonIncentiveLevels, reviewCountsByLevel, overdueCountsByLevel),
      reviews = reviewsForLevel paginateWith PageRequest.of(page, size),
    )
  }

  private suspend fun getLocationDescription(cellLocationPrefix: String): String = try {
    val locationKey = cellLocationPrefix.removeSuffix("-")
    val location = locationsService.getByKey(locationKey)
    location.localName ?: location.pathHierarchy
  } catch (@Suppress("unused") e: NotFound) {
    UNKNOWN_LOCATION
  }

  private fun buildLevels(
    prisonIncentiveLevels: List<PrisonIncentiveLevel>,
    reviewCountsByLevel: Map<String, Int>,
    overdueCountsByLevel: Map<String, Int>,
  ): List<IncentiveReviewLevel> = prisonIncentiveLevels.map { prisonIncentiveLevel ->
    IncentiveReviewLevel(
      levelCode = prisonIncentiveLevel.levelCode,
      levelName = prisonIncentiveLevel.levelName,
      reviewCount = reviewCountsByLevel[prisonIncentiveLevel.levelCode] ?: 0,
      overdueCount = overdueCountsByLevel[prisonIncentiveLevel.levelCode] ?: 0,
    )
  }

  private fun toReviewDto(
    prisoner: Prisoner,
    incentiveLevels: Map<Long, IncentiveReview>,
    behavioursSinceLastReview: Map<BookingTypeKey, CaseNoteSummary>,
    nextReviewDates: Map<Long, LocalDate>,
    daysSinceLastRealReview: Map<Long, Int?>,
  ): IncentiveReviewDTO = IncentiveReviewDTO(
    prisonerNumber = prisoner.prisonerNumber,
    bookingId = prisoner.bookingId,
    firstName = WordUtils.capitalizeFully(prisoner.firstName),
    lastName = WordUtils.capitalizeFully(prisoner.lastName),
    levelCode = incentiveLevels[prisoner.bookingId]!!.levelCode,
    positiveBehaviours =
    behavioursSinceLastReview[BookingTypeKey(prisoner.bookingId, POSITIVE_BEHAVIOUR)]?.totalCaseNotes ?: 0,
    negativeBehaviours =
    behavioursSinceLastReview[BookingTypeKey(prisoner.bookingId, NEGATIVE_BEHAVIOUR)]?.totalCaseNotes ?: 0,
    hasAcctOpen = prisoner.hasAcctOpen,
    isNewToPrison = daysSinceLastRealReview[prisoner.bookingId] == null,
    nextReviewDate = nextReviewDates[prisoner.bookingId]!!,
    daysSinceLastReview = daysSinceLastRealReview[prisoner.bookingId],
  )
  private suspend fun getIncentiveLevelsForPrisoners(bookingIds: List<Long>): Map<Long, IncentiveReview> =
    incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .toMap(IncentiveReview::bookingId)
}

@Suppress("unused") // not all enum variants are referred to in code
enum class IncentiveReviewSort(
  val field: String,
  private val selector: (IncentiveReviewDTO) -> Comparable<*>,
  private val defaultOrder: Sort.Direction,
) {
  NEXT_REVIEW_DATE("nextReviewDate", IncentiveReviewDTO::nextReviewDate, Sort.Direction.ASC),
  DAYS_SINCE_LAST_REVIEW("daysSinceLastReview", { it.daysSinceLastReview ?: Int.MAX_VALUE }, Sort.Direction.DESC),
  FIRST_NAME("firstName", IncentiveReviewDTO::firstName, Sort.Direction.ASC),
  LAST_NAME("lastName", IncentiveReviewDTO::lastName, Sort.Direction.ASC),
  PRISONER_NUMBER("prisonerNumber", IncentiveReviewDTO::prisonerNumber, Sort.Direction.ASC),
  POSITIVE_BEHAVIOURS("positiveBehaviours", IncentiveReviewDTO::positiveBehaviours, Sort.Direction.DESC),
  NEGATIVE_BEHAVIOURS("negativeBehaviours", IncentiveReviewDTO::negativeBehaviours, Sort.Direction.DESC),
  HAS_ACCT_OPEN("hasAcctOpen", IncentiveReviewDTO::hasAcctOpen, Sort.Direction.DESC),
  IS_NEW_TO_PRISON("isNewToPrison", IncentiveReviewDTO::isNewToPrison, Sort.Direction.DESC),
  ;

  infix fun comparingIn(order: Sort.Direction?): Comparator<IncentiveReviewDTO> =
    if ((order ?: defaultOrder).isDescending) {
      compareBy(selector).reversed()
    } else {
      compareBy(selector)
    }
}

fun IncentiveReviewSort?.orDefault() = this ?: IncentiveReviewSort.NEXT_REVIEW_DATE
