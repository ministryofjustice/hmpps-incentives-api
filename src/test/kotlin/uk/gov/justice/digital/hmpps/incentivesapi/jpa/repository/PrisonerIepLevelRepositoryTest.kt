package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDateTime

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class PrisonerIepLevelRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: PrisonerIepLevelRepository

  @Test
  fun savePrisonerIepLevel(): Unit = runBlocking {

    repository.save(
      PrisonerIepLevel(
        iepCode = "BAS",
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        bookingId = 1234567,
        sequence = 1,
        current = false,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(2),
        prisonerNumber = "A1234AB"
      )
    )

    repository.save(
      PrisonerIepLevel(
        iepCode = "STD",
        prisonId = "MDI",
        locationId = "MDI-1-1-004",
        bookingId = 1234567,
        sequence = 2,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now(),
        prisonerNumber = "A1234AB"
      )
    )

    coroutineScope {
      val prisonerLevel1 = async { repository.findOneByBookingIdAndCurrentIsTrue(1234567) }

      with(prisonerLevel1.await()!!) {
        assertThat(iepCode).isEqualTo("STD")
        assertThat(prisonId).isEqualTo("MDI")
        assertThat(current).isEqualTo(true)
        assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
        assertThat(sequence).isEqualTo(2)
      }

      val prisonerLevel2 = async { repository.findFirstByBookingIdOrderBySequenceDesc(1234567) }

      with(prisonerLevel2.await()!!) {
        assertThat(iepCode).isEqualTo("STD")
        assertThat(prisonId).isEqualTo("MDI")
        assertThat(current).isEqualTo(true)
        assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
        assertThat(sequence).isEqualTo(2)
      }

      val prisonerLevels = async { repository.findAllByBookingIdOrderBySequenceDesc(1234567) }

      val levels = prisonerLevels.await().toList()
      assertThat(levels).hasSize(2)
    }
  }
}
