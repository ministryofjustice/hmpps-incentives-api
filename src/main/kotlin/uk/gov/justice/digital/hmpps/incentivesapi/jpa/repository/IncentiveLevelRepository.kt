package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel

@Repository
interface IncentiveLevelRepository : CoroutineCrudRepository<IncentiveLevel, String> {
  fun findAllByOrderBySequence(): Flow<IncentiveLevel>
  fun findAllByActiveIsTrueOrderBySequence(): Flow<IncentiveLevel>
}
