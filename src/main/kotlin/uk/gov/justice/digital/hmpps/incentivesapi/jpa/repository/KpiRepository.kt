package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import java.time.LocalDate

data class ReviewsConductedPrisonersReviewed(
  val reviewsConducted: Int,
  val prisonersReviewed: Int,
)

@Repository
interface KpiRepository : CoroutineCrudRepository<Kpi, LocalDate> {
  /**
   * Returns the number of reviews conducted and prisoners reviewed in the previous month
   */
  @Query(
    //language=postgresql
    """
    SELECT count(*) AS reviews_conducted, count(DISTINCT prisoner_number) AS prisoners_reviewed
    FROM prisoner_iep_level
    -- review time between 1st day of previous month and 1st day of current month (relative to `day` argument)
    -- e.g. day = 2023-10-19, review_time >= 2023-09-01 AND review_time < 2023-10-01
    WHERE review_time >= (DATE_TRUNC('month', :day)-'1 month'::INTERVAL)::DATE AND
          review_time < DATE_TRUNC('month', :day)::DATE AND
          review_type IN ('REVIEW', 'MIGRATED')
    ;
    """,
  )
  suspend fun getNumberOfReviewsConductedAndPrisonersReviewed(day: LocalDate): ReviewsConductedPrisonersReviewed
}
