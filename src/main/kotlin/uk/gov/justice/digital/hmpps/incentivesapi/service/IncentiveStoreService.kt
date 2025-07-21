package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
}
