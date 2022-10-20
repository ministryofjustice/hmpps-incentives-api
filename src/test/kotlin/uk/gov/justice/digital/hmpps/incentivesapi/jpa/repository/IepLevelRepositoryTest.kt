package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class IepLevelRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: IepLevelRepository

  @Test
  fun saveIep(): Unit = runBlocking {

    repository.save(IepLevel(iepCode = "XXX", iepDescription = "Test", active = true))

    val persistedIep = repository.findById("XXX") ?: throw RuntimeException("XXX not found")
    with(persistedIep) {
      assertThat(iepCode).isEqualTo("XXX")
      assertThat(iepDescription).isEqualTo("Test")
      assertThat(sequence).isEqualTo(99)
      assertThat(active).isEqualTo(true)
    }
  }
}
