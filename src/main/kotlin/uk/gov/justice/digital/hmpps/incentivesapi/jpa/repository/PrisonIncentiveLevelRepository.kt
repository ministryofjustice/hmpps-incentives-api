package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel

@Repository
interface PrisonIncentiveLevelRepository : CoroutineCrudRepository<PrisonIncentiveLevel, Int> {
  /**
   * All levels’ configuration for this prison, including inactive ones; in globally-defined order
   * NB: Should not be exposed to clients directly
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.description AS level_description
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId
    ORDER BY incentive_level.sequence
    """
  )
  fun findAllByPrisonId(prisonId: String): Flow<PrisonIncentiveLevel>

  /**
   * Active levels’ configuration for this prison; in globally-defined order
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.description AS level_description
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.active IS TRUE
    ORDER BY incentive_level.sequence
    """
  )
  fun findAllByPrisonIdAndActiveIsTrue(prisonId: String): Flow<PrisonIncentiveLevel>

  /**
   * Finds a levels’ configuration in a prison whether active or inactive; can be missing
   * NB: Should not be exposed to clients directly
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.description AS level_description
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.level_code = :levelCode
    LIMIT 1
    """
  )
  suspend fun findFirstByPrisonIdAndLevelCode(prisonId: String, levelCode: String): PrisonIncentiveLevel?

  /**
   * Finds an active levels’ configuration in a prison; can be missing
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.description AS level_description
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.level_code = :levelCode AND prison_incentive_level.active IS TRUE
    LIMIT 1
    """
  )
  suspend fun findFirstByPrisonIdAndLevelCodeAndActiveIsTrue(prisonId: String, levelCode: String): PrisonIncentiveLevel?
}
