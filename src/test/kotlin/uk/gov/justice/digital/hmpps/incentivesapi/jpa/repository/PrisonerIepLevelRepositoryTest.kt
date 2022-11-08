package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
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

  private fun entity(bookingId: Long, current: Boolean): PrisonerIepLevel =
    PrisonerIepLevel(
      iepCode = "BAS",
      prisonId = "LEI",
      locationId = "LEI-1-1-001",
      bookingId = bookingId,
      current = current,
      reviewedBy = "TEST_STAFF1",
      reviewTime = LocalDateTime.now(),
      prisonerNumber = "A1234AB",
    )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    repository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    repository.deleteAll()
  }

  @Test
  fun `getAllBookingIds() returns all the bookingIds`(): Unit = runBlocking {
    // Given some records
    repository.save(entity(2, true))
    repository.save(entity(4, true))
    repository.save(entity(8, true))

    // When I get the list of bookingIds
    val bookingIds = repository.getAllBookingIds().toList().sorted()

    // Then
    assertThat(bookingIds).isEqualTo(listOf<Long>(2, 4, 8))
  }

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

  @Nested
  inner class CurrentTrueConstraint {
    private val bookingId = 1234567L

    @Test
    fun `cannot persist another record for the same bookingId where current=true already exists`(): Unit = runBlocking {
      // Given
      repository.save(entity(bookingId, true))

      // When
      Assert.assertThrows(DataIntegrityViolationException::class.java) {
        runBlocking { repository.save(entity(bookingId, true)) }
      }

      // Then
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId).toList()).hasSize(1)
    }

    @Test
    fun `can persist a current=false record for the same bookingId`(): Unit = runBlocking {
      // Given
      repository.save(entity(bookingId, true))

      // When
      repository.save(entity(bookingId, false))

      // Then
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId).toList()).hasSize(2)
    }

    @Test
    fun `can persist a current=true record for different bookingIds`(): Unit = runBlocking {
      // Given
      repository.save(entity(bookingId, true))

      // When
      val secondBookingId = 42L
      repository.save(entity(secondBookingId, true))

      // Then
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId).toList()).hasSize(1)
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(secondBookingId).toList()).hasSize(1)
    }

    @Test
    fun `can persist a current=false record for different bookingIds`(): Unit = runBlocking {
      // Given
      repository.save(entity(bookingId, true))
      val secondBookingId = 42L
      repository.save(entity(secondBookingId, true))

      // When
      repository.save(entity(bookingId, false))
      repository.save(entity(secondBookingId, false))

      // Then
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId).toList()).hasSize(2)
      assertThat(repository.findAllByBookingIdOrderByReviewTimeDesc(secondBookingId).toList()).hasSize(2)
    }
  }
}
