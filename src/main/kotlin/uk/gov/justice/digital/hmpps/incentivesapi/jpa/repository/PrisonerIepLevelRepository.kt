package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel

@Repository
interface PrisonerIepLevelRepository : CoroutineCrudRepository<PrisonerIepLevel, Long> {
  fun findAllByBookingIdOrderBySequenceDesc(bookingId: Long): Flow<PrisonerIepLevel>
  suspend fun findOneByBookingIdAndCurrentIsTrue(bookingId: Long): PrisonerIepLevel?
  suspend fun findFirstByBookingIdOrderBySequenceDesc(bookingId: Long): PrisonerIepLevel?
}
