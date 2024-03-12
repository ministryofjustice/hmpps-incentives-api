package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.hmpps.kotlin.sar.HmppsPrisonSubjectAccessRequestReactiveService
import uk.gov.justice.hmpps.kotlin.sar.HmppsSubjectAccessRequestContent
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SubjectAccessRequestService(
  private val prisonerIncentiveReviewService: PrisonerIncentiveReviewService,
) : HmppsPrisonSubjectAccessRequestReactiveService {

  override suspend fun getPrisonContentFor(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): HmppsSubjectAccessRequestContent? {
    val history = prisonerIncentiveReviewService.getPrisonerIncentiveHistory(prn)
    val reviews = history.incentiveReviewDetails
      .mapIndexed { index, review ->
        ReviewForSar(
          id = review.id,
          bookingId = review.bookingId,
          prisonerNumber = review.prisonerNumber,
          nextReviewDate = history.nextReviewDate,
          levelCode = review.iepCode,
          prisonId = review.agencyId,
          locationId = review.locationId,
          reviewTime = review.iepTime,
          reviewedBy = review.userId,
          commentText = review.comments,
          current = index == 0,
          reviewType = review.reviewType,
        )
      }
      .filter {
        val reviewDate = it.reviewTime.toLocalDate()
        (
          fromDate == null || reviewDate.isEqual(fromDate) || reviewDate.isAfter(fromDate)
          ) && (toDate == null || reviewDate.isEqual(toDate) || reviewDate.isBefore(toDate))
      }

    return HmppsSubjectAccessRequestContent(
      content = reviews,
    )
  }
}

data class ReviewForSar(
  val id: Long,
  val bookingId: Long,
  val prisonerNumber: String,
  val nextReviewDate: LocalDate? = null,
  val levelCode: String,
  val prisonId: String,
  val locationId: String? = null,
  val reviewTime: LocalDateTime,
  val reviewedBy: String? = null,
  val commentText: String? = null,
  val current: Boolean = true,
  val reviewType: ReviewType = ReviewType.REVIEW,
)
