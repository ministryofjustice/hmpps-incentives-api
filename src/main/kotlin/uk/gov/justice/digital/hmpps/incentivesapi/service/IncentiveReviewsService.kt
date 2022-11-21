package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.apache.commons.text.WordUtils
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.incentivesapi.config.ListOfDataNotFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsage
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.util.flow.toMap
import java.time.Clock
import java.time.LocalDate
import java.util.Comparator

@Service
class IncentiveReviewsService(
  private val offenderSearchService: OffenderSearchService,
  private val prisonApiService: PrisonApiService,
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
      offenderSearchService.findOffenders(prisonId, cellLocationPrefix, 0, 1000)
    }
    val deferredLocationDescription = async {
      try {
        prisonApiService.getLocation(cellLocationPrefix).description
      } catch (e: NotFound) {
        "Unknown location"
      }
    }

    val offenders = deferredOffenders.await()

    val bookingIds = offenders.content.map(OffenderSearchPrisoner::bookingId)
    val prisonerNumbers = offenders.content.map(OffenderSearchPrisoner::prisonerNumber)

    val deferredPositiveCaseNotesInLast3Months = async { getCaseNoteUsage("POS", "IEP_ENC", prisonerNumbers) }
    val deferredNegativeCaseNotesInLast3Months = async { getCaseNoteUsage("NEG", "IEP_WARN", prisonerNumbers) }

    val deferredIncentiveLevels = async { getIncentiveLevelsForOffenders(bookingIds) }

    val nextReviewDates = nextReviewDateGetterService.getMany(offenders.content)
    val overdueCount = nextReviewDates.values.count { it.isBefore(LocalDate.now(clock)) }

    val incentiveLevels = deferredIncentiveLevels.await()
    val bookingIdsMissingIncentiveLevel = bookingIds subtract incentiveLevels.keys
    if (bookingIdsMissingIncentiveLevel.isNotEmpty()) {
      throw ListOfDataNotFoundException("incentive levels", bookingIdsMissingIncentiveLevel)
    }

    val positiveCaseNotesInLast3Months = deferredPositiveCaseNotesInLast3Months.await()
    val negativeCaseNotesInLast3Months = deferredNegativeCaseNotesInLast3Months.await()

    val locationDescription = deferredLocationDescription.await()

    val comparator = IncentiveReviewSort.orDefault(sort) comparingIn order

    val reviews = offenders.content
      .map {
        IncentiveReview(
          prisonerNumber = it.prisonerNumber,
          bookingId = it.bookingId,
          firstName = WordUtils.capitalizeFully(it.firstName),
          lastName = WordUtils.capitalizeFully(it.lastName),
          levelCode = incentiveLevels[it.bookingId]!!.iepCode,
          positiveBehaviours = positiveCaseNotesInLast3Months[it.prisonerNumber]?.totalCaseNotes ?: 0,
          negativeBehaviours = negativeCaseNotesInLast3Months[it.prisonerNumber]?.totalCaseNotes ?: 0,
          hasAcctOpen = it.acctOpen,
          nextReviewDate = nextReviewDates[it.bookingId]!!,
        )
      }
      .filter { it.levelCode == levelCode }
      .sortedWith(comparator)

    IncentiveReviewResponse(
      locationDescription = locationDescription,
      overdueCount = overdueCount,
      reviewCount = reviews.size,
      reviews = reviews,
    )
  }

  private suspend fun getIncentiveLevelsForOffenders(bookingIds: List<Long>): Map<Long, PrisonerIepLevel> =
    prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .toMap(keySelector = PrisonerIepLevel::bookingId)

  // Lifted from IncentiveSummaryService, which will eventually be dropped entirely, hence didn't move this to a shared service to encapsulate the logic
  private suspend fun getCaseNoteUsage(type: String, subType: String, prisonerNumbers: List<String>): Map<String, CaseNoteSummary> =
    prisonApiService.retrieveCaseNoteCounts(type, prisonerNumbers)
      .toList()
      .groupBy(CaseNoteUsage::offenderNo)
      .map { cn ->
        CaseNoteSummary(
          offenderNo = cn.key,
          totalCaseNotes = calcTypeCount(cn.value),
          numSubTypeCount = calcTypeCount(cn.value.filter { cnc -> cnc.caseNoteSubType == subType })
        )
      }.associateBy(CaseNoteSummary::offenderNo)

  private fun calcTypeCount(caseNoteUsage: List<CaseNoteUsage>): Int =
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
