package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel

@Repository
interface IncentiveLevelRepository : CoroutineCrudRepository<IncentiveLevel, String> {
  fun findAllByOrderBySequence(): Flow<IncentiveLevel>
  fun findAllByActiveIsTrueOrderBySequence(): Flow<IncentiveLevel>

  @Query(
    //language=postgresql
    """
    SELECT MAX("sequence")
    FROM incentive_level
    """
  )
  suspend fun findMaxSequence(): Int?
}
