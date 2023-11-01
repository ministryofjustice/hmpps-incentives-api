package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveRecordUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIncentiveLevelRepository

@Service
@Transactional
class IncentiveStoreService(
  private val prisonerIncentiveLevelRepository: PrisonerIncentiveLevelRepository,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun saveIncentiveReview(
    incentiveLevel: IncentiveReview,
  ): IncentiveReview {
    if (incentiveLevel.current) {
      prisonerIncentiveLevelRepository.updateIncentivesToNotCurrentForBooking(incentiveLevel.bookingId)
    }

    val review = prisonerIncentiveLevelRepository.save(incentiveLevel)
    nextReviewDateUpdaterService.update(incentiveLevel.bookingId)
    return review
  }

  suspend fun updateMergedReviews(
    reviewsToUpdate: List<IncentiveReview>,
    remainingBookingId: Long,
  ) {
    val savedReviews = prisonerIncentiveLevelRepository.saveAll(reviewsToUpdate)
    log.debug("${savedReviews.count()} records saved")
    nextReviewDateUpdaterService.update(remainingBookingId)
  }

  suspend fun deleteIncentiveRecord(incentiveRecord: IncentiveReview) {
    prisonerIncentiveLevelRepository.delete(incentiveRecord)
    nextReviewDateUpdaterService.update(incentiveRecord.bookingId)

    // If the deleted record had `current=true`, the latest IEP review becomes current
    if (incentiveRecord.current) {
      // The deleted record was current, set new current to the latest IEP review
      prisonerIncentiveLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(incentiveRecord.bookingId)?.run {
        prisonerIncentiveLevelRepository.save(this.copy(current = true))
      }
    }
  }

  suspend fun updateIncentiveRecord(
    update: IncentiveRecordUpdate,
    incentiveRecord: IncentiveReview,
  ): IncentiveReview {
    if (update.current == true) {
      prisonerIncentiveLevelRepository.updateIncentivesToNotCurrentForBookingAndIncentive(incentiveRecord.bookingId, incentiveRecord.id)
    }

    val updatedIncentiveRecord = prisonerIncentiveLevelRepository.save(
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
