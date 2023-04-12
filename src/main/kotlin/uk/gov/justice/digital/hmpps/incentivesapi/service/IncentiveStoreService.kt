package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.count
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveRecordUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository

@Service
@Transactional
class IncentiveStoreService(
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun saveIncentiveReview(
    incentiveLevel: PrisonerIepLevel,
  ): PrisonerIepLevel {
    if (incentiveLevel.current) {
      prisonerIepLevelRepository.updateIncentivesToNotCurrentForBooking(incentiveLevel.bookingId)
    }

    val review = prisonerIepLevelRepository.save(incentiveLevel)
    nextReviewDateUpdaterService.update(incentiveLevel.bookingId)
    return review
  }

  suspend fun updateMergedReviews(
    reviewsToUpdate: List<PrisonerIepLevel>,
    remainingBookingId: Long,
  ) {
    val savedReviews = prisonerIepLevelRepository.saveAll(reviewsToUpdate)
    log.debug("${savedReviews.count()} records saved")
    nextReviewDateUpdaterService.update(remainingBookingId)
  }

  suspend fun deleteIncentiveReview(prisonerIepLevel: PrisonerIepLevel) {
    prisonerIepLevelRepository.delete(prisonerIepLevel)
    nextReviewDateUpdaterService.update(prisonerIepLevel.bookingId)

    // If the deleted record had `current=true`, the latest IEP review becomes current
    if (prisonerIepLevel.current) {
      // The deleted record was current, set new current to the latest IEP review
      prisonerIepLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(prisonerIepLevel.bookingId)?.run {
        prisonerIepLevelRepository.save(this.copy(current = true))
      }
    }
  }

  suspend fun updateIncentiveRecord(
    update: IncentiveRecordUpdate,
    incentiveRecord: PrisonerIepLevel,
  ): PrisonerIepLevel {
    update.current?.let {
      prisonerIepLevelRepository.updateIncentivesToNotCurrentForBookingAndIncentive(incentiveRecord.bookingId, incentiveRecord.id)
    }

    val updatedIncentiveRecord = prisonerIepLevelRepository.save(
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
