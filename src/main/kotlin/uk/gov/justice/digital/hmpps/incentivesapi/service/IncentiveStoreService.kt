package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveRecordUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository

@Service
@Transactional
class IncentiveStoreService(
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun saveIncentiveReview(incentiveLevel: IncentiveReview): IncentiveReview {
    if (incentiveLevel.current) {
      incentiveReviewRepository.updateIncentivesToNotCurrentForBooking(incentiveLevel.bookingId)
    }

    val review = incentiveReviewRepository.save(incentiveLevel)
    nextReviewDateUpdaterService.update(incentiveLevel.bookingId)
    return review
  }

  suspend fun updateMergedReviews(reviewsToUpdate: List<IncentiveReview>, remainingBookingId: Long) {
    val savedReviews = incentiveReviewRepository.saveAll(reviewsToUpdate)
    log.debug("${savedReviews.count()} records saved")
    nextReviewDateUpdaterService.update(remainingBookingId)
  }

  suspend fun deleteIncentiveRecord(incentiveRecord: IncentiveReview) {
    incentiveReviewRepository.delete(incentiveRecord)
    nextReviewDateUpdaterService.update(incentiveRecord.bookingId)

    // If the deleted record had `current=true`, the latest IEP review becomes current
    if (incentiveRecord.current) {
      // The deleted record was current, set new current to the latest IEP review
      incentiveReviewRepository.findFirstByBookingIdOrderByReviewTimeDesc(incentiveRecord.bookingId)?.run {
        incentiveReviewRepository.save(this.copy(current = true))
      }
    }
  }

  suspend fun updateIncentiveRecord(update: IncentiveRecordUpdate, incentiveRecord: IncentiveReview): IncentiveReview {
    if (update.current == true) {
      incentiveReviewRepository.updateIncentivesToNotCurrentForBookingAndIncentive(
        incentiveRecord.bookingId,
        incentiveRecord.id,
      )
    }

    val updatedIncentiveRecord = incentiveReviewRepository.save(
      incentiveRecord.copy(
        reviewTime = update.reviewTime ?: incentiveRecord.reviewTime,
        commentText = update.comment ?: incentiveRecord.commentText,
        current = update.current ?: incentiveRecord.current,
      ),
    )
    nextReviewDateUpdaterService.update(updatedIncentiveRecord.bookingId)
    return updatedIncentiveRecord
  }
}
