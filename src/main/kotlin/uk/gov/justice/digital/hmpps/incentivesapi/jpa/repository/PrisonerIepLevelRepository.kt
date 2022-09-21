package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel

@Repository
interface PrisonerIepLevelRepository : CoroutineCrudRepository<PrisonerIepLevel, Long> {
  fun findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber: String): Flow<PrisonerIepLevel>
  fun findAllByBookingIdOrderByReviewTimeDesc(bookingId: Long): Flow<PrisonerIepLevel>
  fun findAllByBookingIdInAndCurrentIsTrue(bookingIds: List<Long>): Flow<PrisonerIepLevel>
  suspend fun findOneByBookingIdAndCurrentIsTrue(bookingId: Long): PrisonerIepLevel?
  suspend fun findFirstByBookingIdOrderByReviewTimeDesc(bookingId: Long): PrisonerIepLevel?
}
