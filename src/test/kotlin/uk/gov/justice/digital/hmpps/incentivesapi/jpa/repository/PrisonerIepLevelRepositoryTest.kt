package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    val bookingId = 1234567L

    repository.save(
      PrisonerIepLevel(
        iepCode = "BAS",
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        bookingId = bookingId,
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
        bookingId = bookingId,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now(),
        prisonerNumber = "A1234AB"
      )
    )

    coroutineScope {
      val prisonerLevelCurrent = async { repository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId)).first() }
      val prisonerLevelFirst = async { repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId) }
      val prisonerAllLevels = async { repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId) }

      with(prisonerLevelCurrent.await()) {
        assertThat(iepCode).isEqualTo("STD")
        assertThat(prisonId).isEqualTo("MDI")
        assertThat(current).isEqualTo(true)
        assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
      }

      with(prisonerLevelFirst.await()!!) {
        assertThat(iepCode).isEqualTo("STD")
        assertThat(prisonId).isEqualTo("MDI")
        assertThat(current).isEqualTo(true)
        assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
      }

      val levels = prisonerAllLevels.await().toList()
      assertThat(levels).hasSize(2)
    }
  }
}
