package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel

@Repository
interface PrisonIncentiveLevelRepository : CoroutineCrudRepository<PrisonIncentiveLevel, Int> {
  /**
   * All levels’ configuration for this prison, including inactive ones; in globally-defined order
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.name AS level_name
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId
    ORDER BY incentive_level.sequence
    """,
  )
  fun findAllByPrisonId(prisonId: String): Flow<PrisonIncentiveLevel>

  /**
   * Active levels’ configuration for this prison; in globally-defined order
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.name AS level_name
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.active IS TRUE
    ORDER BY incentive_level.sequence
    """,
  )
  fun findAllByPrisonIdAndActiveIsTrue(prisonId: String): Flow<PrisonIncentiveLevel>

  /**
   * Finds a levels’ configuration in a prison whether active or inactive; can be missing
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.name AS level_name
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.level_code = :levelCode
    LIMIT 1
    """,
  )
  suspend fun findFirstByPrisonIdAndLevelCode(prisonId: String, levelCode: String): PrisonIncentiveLevel?

  /**
   * Finds the active and default level configuration for a prison
   * NB: Each prison should have exactly one but the rule is not enforced at database level
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_incentive_level.*, incentive_level.name AS level_name
    FROM prison_incentive_level
    JOIN incentive_level ON prison_incentive_level.level_code = incentive_level.code
    WHERE prison_id = :prisonId AND prison_incentive_level.active IS TRUE AND prison_incentive_level.default_on_admission IS TRUE
    LIMIT 1
    """,
  )
  suspend fun findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue(prisonId: String): PrisonIncentiveLevel?

  /**
   * Sets levels other than the one privided to be non-default for this prison
   * Used to ensure there is only one default level for admission in a prison
   */
  @Modifying
  @Query(
    // language=postgresql
    """
    UPDATE prison_incentive_level
    SET default_on_admission = FALSE
    WHERE prison_id = :prisonId AND NOT level_code = :levelCode
    """,
  )
  suspend fun setOtherLevelsNotDefaultForAdmission(prisonId: String, levelCode: String): Int?

  /**
   * All prisons that have active level configurations, effectively a way to find all active prisons
   */
  @Query(
    // language=postgresql
    """
    SELECT DISTINCT prison_id
    FROM prison_incentive_level
    WHERE active IS TRUE
    """,
  )
  fun findPrisonIdsWithActiveLevels(): Flow<String>

  /**
   * All prisons that have this level active
   */
  @Query(
    // language=postgresql
    """
    SELECT prison_id
    FROM prison_incentive_level
    WHERE active IS TRUE AND level_code = :levelCode
    ORDER BY prison_id
    """,
  )
  fun findPrisonIdsWithActiveLevel(levelCode: String): Flow<String>
}
