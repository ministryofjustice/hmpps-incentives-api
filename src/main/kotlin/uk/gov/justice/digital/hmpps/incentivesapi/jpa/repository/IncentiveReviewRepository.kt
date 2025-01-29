package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview

@Repository
interface IncentiveReviewRepository : CoroutineCrudRepository<IncentiveReview, Long> {
  fun findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber: String): Flow<IncentiveReview>
  fun findAllByBookingIdOrderByReviewTimeDesc(bookingId: Long): Flow<IncentiveReview>
  fun findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds: List<Long>): Flow<IncentiveReview>
  fun findAllByBookingIdInOrderByReviewTimeDesc(bookingIds: List<Long>): Flow<IncentiveReview>
  suspend fun findFirstByBookingIdOrderByReviewTimeDesc(bookingId: Long): IncentiveReview?

  @Modifying
  @Query(
    "UPDATE prisoner_iep_level SET current = false WHERE booking_id = :bookingId AND current = true and id != :incentiveId",
  )
  suspend fun updateIncentivesToNotCurrentForBookingAndIncentive(bookingId: Long, incentiveId: Long): Int

  @Modifying
  @Query("UPDATE prisoner_iep_level SET current = false WHERE booking_id = :bookingId AND current = true")
  suspend fun updateIncentivesToNotCurrentForBooking(bookingId: Long): Int

  @Query(
    // language=postgresql
    """
    SELECT EXISTS(
      SELECT 1
      FROM prisoner_iep_level
      WHERE current IS TRUE AND iep_code = :levelCode AND booking_id IN (:bookingIds)
    )
    """,
  )
  suspend fun somePrisonerCurrentlyOnLevel(bookingIds: Iterable<Long>, levelCode: String): Boolean
}
