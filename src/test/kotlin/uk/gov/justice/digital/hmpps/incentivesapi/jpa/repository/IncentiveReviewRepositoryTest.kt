package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime

@DisplayName("Incentive review repository")
@DataR2dbcTest
@ActiveProfiles("test")
@WithMockAuthUser
class IncentiveReviewRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: IncentiveReviewRepository

  private fun entity(bookingId: Long, current: Boolean): IncentiveReview = IncentiveReview(
    levelCode = "BAS",
    prisonId = "LEI",
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
  fun savePrisonerIepLevel(): Unit = runBlocking {
    val bookingId = 1234567L

    repository.save(
      IncentiveReview(
        levelCode = "BAS",
        prisonId = "LEI",
        bookingId = bookingId,
        current = false,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(2),
        prisonerNumber = "A1234AB",
      ),
    )

    repository.save(
      IncentiveReview(
        levelCode = "STD",
        prisonId = "MDI",
        bookingId = bookingId,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now(),
        prisonerNumber = "A1234AB",
      ),
    )

    coroutineScope {
      launch {
        val prisonerLevelCurrent = repository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(
          listOf(bookingId),
        ).first()
        with(prisonerLevelCurrent) {
          assertThat(levelCode).isEqualTo("STD")
          assertThat(prisonId).isEqualTo("MDI")
          assertThat(current).isEqualTo(true)
          assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
        }
      }

      launch {
        val prisonerLevelFirst = repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        with(prisonerLevelFirst!!) {
          assertThat(levelCode).isEqualTo("STD")
          assertThat(prisonId).isEqualTo("MDI")
          assertThat(current).isEqualTo(true)
          assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
        }
      }

      launch {
        val prisonerAllLevels = repository.findAllByBookingIdOrderByReviewTimeDesc(bookingId).toList()
        assertThat(prisonerAllLevels).hasSize(2)
      }
    }
  }

  @DisplayName("current=true constraint")
  @Nested
  inner class CurrentTrueConstraint {
    private val bookingId = 1234567L

    @Test
    fun `cannot persist another record for the same bookingId where current=true already exists`(): Unit = runBlocking {
      // Given
      repository.save(entity(bookingId, true))

      // When
      assertThatThrownBy {
        runBlocking { repository.save(entity(bookingId, true)) }
      }.isInstanceOf(DataIntegrityViolationException::class.java)

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

  @Test
  fun `checks if there are prisoners on a level`(): Unit = runBlocking {
    repository.save(
      IncentiveReview(
        levelCode = "BAS",
        prisonId = "LEI",
        bookingId = 123456,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(2),
        prisonerNumber = "A1234AB",
      ),
    )
    repository.save(
      IncentiveReview(
        levelCode = "STD",
        prisonId = "LEI",
        bookingId = 123456,
        current = false,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(20),
        prisonerNumber = "A1234AB",
      ),
    )

    // unknown booking
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123400), "BAS")).isFalse
    // non-current record
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123456), "STD")).isFalse
    // exists currently
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123456), "BAS")).isTrue
    // multiple bookings, none currently on level
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123400, 123456), "STD")).isFalse
    // multiple bookings, one exists currently
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123400, 123456), "BAS")).isTrue

    repository.save(
      IncentiveReview(
        levelCode = "BAS",
        prisonId = "LEI",
        bookingId = 123400,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(2),
        prisonerNumber = "A1234AC",
      ),
    )

    // now known and exists currently
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123400), "BAS")).isTrue
    // multiple bookings, both exist currently
    assertThat(repository.somePrisonerCurrentlyOnLevel(listOf(123400, 123456), "BAS")).isTrue
  }
}
