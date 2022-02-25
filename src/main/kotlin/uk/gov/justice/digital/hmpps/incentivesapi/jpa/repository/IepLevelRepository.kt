package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel

@Repository
interface IepLevelRepository : CoroutineCrudRepository<IepLevel, String> {
  suspend fun findOneByIepDescription(iepCode: String): IepLevel?
  suspend fun findAllByActiveIsTrueOrderBySequence(): Flow<IepLevel>
}
