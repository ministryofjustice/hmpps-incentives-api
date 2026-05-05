package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository

@Service
class IncentiveStoreService(
  private val transactionalOperator: TransactionalOperator,
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun saveIncentiveReview(incentiveLevel: IncentiveReview): IncentiveReview {
    val (review, changes) = transactionalOperator.executeAndAwait {
      if (incentiveLevel.current) {
        incentiveReviewRepository.updateIncentivesToNotCurrentForBooking(incentiveLevel.bookingId)
      }
      val savedReview = incentiveReviewRepository.save(incentiveLevel)
      val pendingChanges = nextReviewDateUpdaterService.updateWriteOnly(incentiveLevel.bookingId)
      Pair(savedReview, pendingChanges)
    }

    nextReviewDateUpdaterService.publishDomainEvents(changes)
    return review
  }

  suspend fun updateMergedReviews(reviewsToUpdate: List<IncentiveReview>, remainingBookingId: Long) {
    val changes = transactionalOperator.executeAndAwait {
      val savedReviews = incentiveReviewRepository.saveAll(reviewsToUpdate)
      log.debug("${savedReviews.count()} records saved")
      nextReviewDateUpdaterService.updateWriteOnly(remainingBookingId)
    }

    nextReviewDateUpdaterService.publishDomainEvents(changes)
  }
}
