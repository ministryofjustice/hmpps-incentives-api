package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepPrison

@Repository
interface IepPrisonRepository : CoroutineCrudRepository<IepPrison, Long> {
  suspend fun findAllByPrisonIdAndIepCode(prisonId: String, iepCode: String): IepPrison?
  suspend fun findAllByPrisonIdAndActiveIsTrue(prisonId: String): Flow<IepPrison>
}
