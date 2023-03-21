package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel

@Repository
interface IncentiveLevelRepository : CoroutineCrudRepository<IncentiveLevel, String> {
  /**
   * Finds all incentive levels, including inactive ones, in globally-defined order
   */
  fun findAllByOrderBySequence(): Flow<IncentiveLevel>

  /**
   * Finds all active incentive levels in globally-defined order
   */
  fun findAllByActiveIsTrueOrderBySequence(): Flow<IncentiveLevel>

  /**
   * Finds the highest sequence number; used to place new incentive levels at the end
   */
  @Query(
    //language=postgresql
    """
    SELECT MAX("sequence")
    FROM incentive_level
    """,
  )
  suspend fun findMaxSequence(): Int?
}
