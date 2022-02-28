package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepPrison

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class IepPrisonRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: IepPrisonRepository

  @Test
  fun saveIepLevel(): Unit = runBlocking {

    repository.save(IepPrison(iepCode = "STD", prisonId = "XXX", defaultIep = true))

    coroutineScope {
      val persistedIepLevel = async { repository.findAllByPrisonIdAndIepCode("XXX", "STD") }

      with(persistedIepLevel.await()!!) {
        assertThat(iepCode).isEqualTo("STD")
        assertThat(prisonId).isEqualTo("XXX")
        assertThat(active).isEqualTo(true)
        assertThat(expiryDate).isNull()
        assertThat(defaultIep).isTrue()
      }
    }
  }

  @Test
  fun failToSaveNonMappedIEP() {
    assertThrows(DataIntegrityViolationException::class.java) {
      runBlocking { repository.save(IepPrison(iepCode = "DDD", prisonId = "MDI")) }
    }
  }

  @Test
  fun retrieveLevels(): Unit = runBlocking {
    coroutineScope {
      val persistedIepLevels = async { repository.findAllByPrisonIdAndActiveIsTrue("MDI") }

      val levels = persistedIepLevels.await().toList()
      assertThat(levels).hasSize(3)
    }
  }
}
