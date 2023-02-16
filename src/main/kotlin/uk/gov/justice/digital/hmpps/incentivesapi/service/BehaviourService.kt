package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.Clock
import java.time.LocalDateTime
import java.time.Period

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
        val latestReview = review.value.firstOrNull(PrisonerIepLevel::isRealReview) ?: review.value.first()
        review.key to latestReview
      }.associate {
        it.first to truncateReviewDate(it.second.reviewTime)
      }

  private fun truncateReviewDate(lastReviewTime: LocalDateTime): LocalDateTime {
    val today = LocalDateTime.now(clock)
    return if (Period.between(lastReviewTime.toLocalDate(), today.toLocalDate()).months < 3) { lastReviewTime } else {
      today.minusMonths(3)
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
