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
  private fun behaviourCaseNoteMap() = mapOf("POS" to "IEP_ENC", "NEG" to "IEP_WARN")

  suspend fun getBehaviours(reviews: List<PrisonerIepLevel>) =
    getCaseNoteUsageByLastReviewDate(
      prisonApiService.retrieveCaseNoteCountsByFromDate(
        behaviourCaseNoteMap().keys.toList(),
        getLastRealReviewForOffenders(reviews)
      ).toList(),
      behaviourCaseNoteMap(),
    )
  private fun getCaseNoteUsageByLastReviewDate(caseNotesByType: List<PrisonerCaseNoteByTypeSubType>, subTypeMap: Map<String, String>) =
    caseNotesByType
      .groupBy(PrisonerCaseNoteByTypeSubType::toKey)
      .map { cn ->
        CaseNoteSummary(
          key = cn.key,
          totalCaseNotes = calcTypeCount(cn.value),
          numSubTypeCount = calcTypeCount(cn.value.filter { cnc -> cnc.caseNoteSubType == subTypeMap[cn.key.caseNoteType] })

        )
      }.associateBy { it.key }

  private fun calcTypeCount(caseNoteUsage: List<PrisonerCaseNoteByTypeSubType>): Int =
    caseNoteUsage.map { it.numCaseNotes }.fold(0) { acc, next -> acc + next }

  private fun getLastRealReviewForOffenders(reviews: List<PrisonerIepLevel>): Map<Long, LocalDateTime> =
    reviews
      .groupBy { it.bookingId }
      .map { review ->
        val latestReview = review.value.firstOrNull(PrisonerIepLevel::isRealReview)
        review.key to truncateReviewDate(latestReview?.reviewTime)
      }.associate {
        it.first to it.second
      }

  private fun defaultReviewPeriod() = LocalDateTime.now(clock).minusMonths(DEFAULT_MONTHS)

  private fun truncateReviewDate(lastReviewTime: LocalDateTime?): LocalDateTime {
    if (lastReviewTime == null) return defaultReviewPeriod()

    val today = LocalDateTime.now(clock)
    return if (lastReviewTime.isBefore(today.minusMonths(DEFAULT_MONTHS))) { defaultReviewPeriod() } else {
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
