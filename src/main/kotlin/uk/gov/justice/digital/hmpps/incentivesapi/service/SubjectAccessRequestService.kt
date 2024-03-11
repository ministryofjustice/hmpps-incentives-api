package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.hmpps.kotlin.sar.HmppsPrisonSubjectAccessRequestReactiveService
import uk.gov.justice.hmpps.kotlin.sar.HmppsSubjectAccessRequestContent
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SubjectAccessRequestService(
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val nextReviewRepository: NextReviewDateRepository,
) : HmppsPrisonSubjectAccessRequestReactiveService {

  override suspend fun getPrisonContentFor(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): HmppsSubjectAccessRequestContent? {
    val reviews = incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prn)
      .filter {
        val createdDate = (it.whenCreated ?: it.reviewTime).toLocalDate()
        (
          fromDate == null || createdDate.isEqual(fromDate) || createdDate.isAfter(fromDate)
          ) && (toDate == null || createdDate.isEqual(toDate) || createdDate.isBefore(toDate))
      }
      .map { review ->
        ReviewForSar(
          id = review.id,
          bookingId = review.bookingId,
          prisonerNumber = review.prisonerNumber,
          nextReviewDate = nextReviewRepository.findById(review.bookingId)?.nextReviewDate,
          levelCode = review.levelCode,
          prisonId = review.prisonId,
          locationId = review.locationId,
          reviewTime = review.reviewTime,
          reviewedBy = review.reviewedBy,
          commentText = review.commentText,
          current = review.current,
          reviewType = review.reviewType,
          whenCreated = review.whenCreated,
        )
      }.toList()

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
  val whenCreated: LocalDateTime? = null,
)
