package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel

@Repository
interface PrisonerIepLevelRepository : CoroutineCrudRepository<PrisonerIepLevel, Long> {
  fun findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber: String): Flow<PrisonerIepLevel>
  fun findAllByBookingIdOrderByReviewTimeDesc(bookingId: Long): Flow<PrisonerIepLevel>
  fun findAllByBookingIdInAndCurrentIsTrue(bookingIds: List<Long>): Flow<PrisonerIepLevel>
  suspend fun findOneByBookingIdAndCurrentIsTrue(bookingId: Long): PrisonerIepLevel?
  suspend fun findFirstByBookingIdOrderByReviewTimeDesc(bookingId: Long): PrisonerIepLevel?
  @Query("UPDATE prisoner_iep_level set prisoner_number = :remainingPrisonerNumber, booking_id = :bookingId WHERE prisoner_number = :removedPrisonerNumber")
  @Modifying
  suspend fun updatePrisonerNumber(remainingPrisonerNumber: String, bookingId: Long, removedPrisonerNumber: String): Int
}
