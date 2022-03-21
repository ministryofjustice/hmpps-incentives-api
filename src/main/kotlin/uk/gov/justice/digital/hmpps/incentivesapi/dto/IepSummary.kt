package uk.gov.justice.digital.hmpps.incentivesapi.dto

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class IepSummary(
  val bookingId: Long,
  val daysSinceReview: Int,
  val iepDate: LocalDate,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val iepDetails: List<IepDetail>
) {

  fun daysSinceReview(): Int {
    return daysSinceReview(iepDetails)
  }

  fun daysOnLevel(): Int {
    val currentIepDate = LocalDate.now().atStartOfDay()
    var daysOnLevel = 0

    run iepCheck@{
      iepDetails.forEach {
        if (it.iepLevel != iepLevel) {
          return@iepCheck
        }
        daysOnLevel = Duration.between(it.iepDate.atStartOfDay(), currentIepDate).toDays().toInt()
      }
    }

    return daysOnLevel
  }
}

fun daysSinceReview(iepHistory: List<IepDetail>): Int {
  val currentIepDate = LocalDate.now().atStartOfDay()
  return Duration.between(iepHistory.first().iepDate.atStartOfDay(), currentIepDate).toDays().toInt()
}

data class IepDetail(
  val bookingId: Long,
  val sequence: Long,
  val iepDate: LocalDate,
  val iepTime: LocalDateTime,
  val agencyId: String,
  val iepLevel: String,
  val comments: String? = null,
  val userId: String?,
  val auditModuleName: String? = null
)

data class IepReview(
  val iepLevel: String,
  val comment: String,
)
