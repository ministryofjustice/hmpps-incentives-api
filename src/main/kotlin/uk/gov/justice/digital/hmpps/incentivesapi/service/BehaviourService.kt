package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import java.time.Clock
import java.time.LocalDateTime
import kotlin.collections.groupBy

const val DEFAULT_MONTHS = 3L

@Service
class BehaviourService(
  private val caseNotesApiService: CaseNotesApiService,
  private val clock: Clock,
) {
  private val behaviourCaseNoteMap = mapOf("POS" to "IEP_ENC", "NEG" to "IEP_WARN")

  suspend fun getBehaviours(reviews: List<IncentiveReview>): BehaviourSummary {
    val typeSubType = behaviourCaseNoteMap.map { TypeSubTypeRequest(type = it.key) }.toSet()
    val defaultReviewPeriod = LocalDateTime.now(clock).minusMonths(DEFAULT_MONTHS)

    val lastRealReviews = getLastRealReviewForPrisoners(reviews)
    val lastReviewsOrDefaultPeriods = lastRealReviews.mapValues { truncateReviewDate(it.value, defaultReviewPeriod) }

    val personIdentifierToBookingId = reviews.groupBy { it.prisonerNumber }
      .mapValues { review -> review.value.first().bookingId }

    val bookingIdToPersonIdentifier = personIdentifierToBookingId.map { it.value to it.key }.toMap()

    val result = lastReviewsOrDefaultPeriods.entries
      .groupBy({ it.value }) { it.key }
      .map { (fromDate, bookingIds) ->
        caseNotesApiService.retrieveCaseNoteCountsByFromDate(
          typeSubType,
          bookingIds.map { bookingId ->
            bookingIdToPersonIdentifier[bookingId] ?: error("No prisonerNumber found for bookingId: $bookingId")
          }.toSet(),
          from = fromDate,
        )
      }

    val caseNotesByType = transformCaseNoteResults(result, personIdentifierToBookingId)
    val caseNoteCountsByType = getCaseNoteUsageByLastReviewDate(caseNotesByType)
    return BehaviourSummary(caseNoteCountsByType, lastRealReviews)
  }

  private suspend fun transformCaseNoteResults(
    result: List<Flow<NoteUsageResponse>>,
    personIdentifierToBookingId: Map<String, Long>,
  ): List<PrisonerCaseNoteByTypeSubType> = result.flatMap { it.toList() }
    .flatMap { response ->
      response.content.flatMap { (personIdentifier, usages) ->
        usages.map { usage ->
          PrisonerCaseNoteByTypeSubType(
            bookingId = personIdentifierToBookingId[personIdentifier]
              ?: error("No bookingId found for personIdentifier: $personIdentifier"),
            caseNoteType = usage.type,
            caseNoteSubType = usage.subType,
            numCaseNotes = usage.count,
          )
        }
      }
    }

  private fun getCaseNoteUsageByLastReviewDate(caseNotesByType: List<PrisonerCaseNoteByTypeSubType>) = caseNotesByType
    .groupBy(PrisonerCaseNoteByTypeSubType::toKey)
    .mapValues { cn ->
      CaseNoteSummary(
        key = cn.key,
        totalCaseNotes = calcTypeCount(cn.value),
        numSubTypeCount = calcTypeCount(
          cn.value.filter { cnc ->
            cnc.caseNoteSubType == behaviourCaseNoteMap[cn.key.caseNoteType]
          },
        ),
      )
    }

  private fun calcTypeCount(caseNoteUsage: List<PrisonerCaseNoteByTypeSubType>): Int =
    caseNoteUsage.sumOf { it.numCaseNotes }

  private fun getLastRealReviewForPrisoners(reviews: List<IncentiveReview>) = reviews
    .groupBy { it.bookingId }
    .mapValues { review ->
      val latestRealReview = review.value.firstOrNull(IncentiveReview::isRealReview)
      latestRealReview?.reviewTime
    }

  private fun truncateReviewDate(lastReviewTime: LocalDateTime?, defaultReviewPeriod: LocalDateTime): LocalDateTime {
    return if (lastReviewTime == null || lastReviewTime.isBefore(defaultReviewPeriod)) {
      defaultReviewPeriod
    } else {
      lastReviewTime
    }
  }
}

data class PrisonerCaseNoteByTypeSubType(
  val bookingId: Long,
  val caseNoteType: String,
  val caseNoteSubType: String,
  val numCaseNotes: Int,
)

data class BehaviourSummary(
  val caseNoteCountsByType: Map<BookingTypeKey, CaseNoteSummary>,
  val lastRealReviews: Map<Long, LocalDateTime?>,
)

data class BookingTypeKey(
  val bookingId: Long,
  val caseNoteType: String,
)

data class CaseNoteSummary(
  val key: BookingTypeKey,
  val totalCaseNotes: Int,
  val numSubTypeCount: Int,
)

fun PrisonerCaseNoteByTypeSubType.toKey() = BookingTypeKey(bookingId, caseNoteType)
