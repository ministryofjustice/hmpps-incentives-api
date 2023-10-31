package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIncentiveLevel
import java.time.Clock
import java.time.LocalDateTime

const val DEFAULT_MONTHS = 3L

@Service
class BehaviourService(
  private val prisonApiService: PrisonApiService,
  private val clock: Clock,
) {
  private val behaviourCaseNoteMap = mapOf("POS" to "IEP_ENC", "NEG" to "IEP_WARN")

  suspend fun getBehaviours(reviews: List<PrisonerIncentiveLevel>): BehaviourSummary {
    val lastRealReviews = getLastRealReviewForOffenders(reviews)
    val lastReviewsOrDefaultPeriods = lastRealReviews.mapValues { truncateReviewDate(it.value) }
    val caseNoteCountsByType = getCaseNoteUsageByLastReviewDate(
      prisonApiService.retrieveCaseNoteCountsByFromDate(
        behaviourCaseNoteMap.keys.toList(),
        lastReviewsOrDefaultPeriods,
      ).toList(),
    )
    return BehaviourSummary(caseNoteCountsByType, lastRealReviews)
  }

  private fun getCaseNoteUsageByLastReviewDate(caseNotesByType: List<PrisonerCaseNoteByTypeSubType>) =
    caseNotesByType
      .groupBy(PrisonerCaseNoteByTypeSubType::toKey)
      .mapValues { cn ->
        CaseNoteSummary(
          key = cn.key,
          totalCaseNotes = calcTypeCount(cn.value),
          numSubTypeCount = calcTypeCount(cn.value.filter { cnc -> cnc.caseNoteSubType == behaviourCaseNoteMap[cn.key.caseNoteType] }),
        )
      }

  private fun calcTypeCount(caseNoteUsage: List<PrisonerCaseNoteByTypeSubType>): Int =
    caseNoteUsage.sumOf { it.numCaseNotes }

  private fun getLastRealReviewForOffenders(reviews: List<PrisonerIncentiveLevel>) =
    reviews
      .groupBy { it.bookingId }
      .mapValues { review ->
        val latestRealReview = review.value.firstOrNull(PrisonerIncentiveLevel::isRealReview)
        latestRealReview?.reviewTime
      }

  private fun truncateReviewDate(lastReviewTime: LocalDateTime?): LocalDateTime {
    val defaultReviewPeriod = LocalDateTime.now(clock).minusMonths(DEFAULT_MONTHS)
    return if (lastReviewTime == null || lastReviewTime.isBefore(defaultReviewPeriod)) {
      defaultReviewPeriod
    } else {
      lastReviewTime
    }
  }
}

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
