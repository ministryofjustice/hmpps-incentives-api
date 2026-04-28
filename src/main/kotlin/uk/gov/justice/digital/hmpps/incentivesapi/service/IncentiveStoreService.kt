package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository

@Service
class IncentiveStoreService(
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val incentiveReviewWriter: IncentiveReviewWriter,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun saveIncentiveReview(incentiveLevel: IncentiveReview): IncentiveReview {
    val review = incentiveReviewWriter.saveCurrentReview(incentiveLevel)
    nextReviewDateUpdaterService.update(incentiveLevel.bookingId)
    return review
  }

  suspend fun updateMergedReviews(reviewsToUpdate: List<IncentiveReview>, remainingBookingId: Long) {
    val savedReviews = incentiveReviewRepository.saveAll(reviewsToUpdate)
    log.debug("${savedReviews.count()} records saved")
    nextReviewDateUpdaterService.update(remainingBookingId)
  }
}

/**
 * Handles the transactional DB writes for saving an incentive review.
 * Kept separate from IncentiveStoreService so that the transaction commits before
 * NextReviewDateUpdaterService publishes domain events.
 */
@Component
class IncentiveReviewWriter(
  private val incentiveReviewRepository: IncentiveReviewRepository,
) {
  @Transactional
  suspend fun saveCurrentReview(incentiveLevel: IncentiveReview): IncentiveReview {
    if (incentiveLevel.current) {
      incentiveReviewRepository.updateIncentivesToNotCurrentForBooking(incentiveLevel.bookingId)
    }
    return incentiveReviewRepository.save(incentiveLevel)
  }
}
