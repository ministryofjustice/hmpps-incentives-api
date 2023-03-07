package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.Clock
import java.time.LocalDateTime

const val DEFAULT_MONTHS = 3L

@Service
class BehaviourService(
  private val prisonApiService: PrisonApiService,
  private val clock: Clock,
) {
  private val behaviourCaseNoteMap = mapOf("POS" to "IEP_ENC", "NEG" to "IEP_WARN")

  suspend fun getBehaviours(reviews: List<PrisonerIepLevel>): Map<BookingTypeKey, CaseNoteSummary> {
    val lastRealReviews = getLastRealReviewForOffenders(reviews)
    val lastReviewsOrDefaultPeriods = lastRealReviews.mapValues { truncateReviewDate(it.value) }
    return getCaseNoteUsageByLastReviewDate(
      prisonApiService.retrieveCaseNoteCountsByFromDate(
        behaviourCaseNoteMap.keys.toList(),
        lastReviewsOrDefaultPeriods,
      ).toList(),
    )
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

  private fun getLastRealReviewForOffenders(reviews: List<PrisonerIepLevel>) =
    reviews
      .groupBy { it.bookingId }
      .mapValues { review ->
        val latestRealReview = review.value.firstOrNull(PrisonerIepLevel::isRealReview)
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

data class CaseNoteSummary(
  val key: BookingTypeKey,
  val totalCaseNotes: Int,
  val numSubTypeCount: Int
)

fun PrisonerCaseNoteByTypeSubType.toKey() = BookingTypeKey(bookingId, caseNoteType)

data class BookingTypeKey(
  val bookingId: Long,
  val caseNoteType: String,
)
