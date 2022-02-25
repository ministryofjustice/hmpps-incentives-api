package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepPrison

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class IepPrisonRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: IepPrisonRepository

  @Test
  fun saveIep(): Unit = runBlocking {

    repository.save(IepPrison(iepCode = "STD", prisonId = "MDI"))

    coroutineContext {
      val persistedIep = async { repository.findAllByPrisonIdAndActive("MDI") }
      with(persistedIep) {
        assertThat(iepCode).isEqualTo("XXX")
        assertThat(iepDescription).isEqualTo("Test")
        assertThat(sequence).isEqualTo(99)
        assertThat(active).isEqualTo(true)
      }
    }
  }

}
