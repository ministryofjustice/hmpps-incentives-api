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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate
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
        prisonerNumber = "A1234AB",
      ),
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
        prisonerNumber = "A1234AB",
      ),
    )

    coroutineScope {
      launch {
        val prisonerLevelCurrent = repository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId)).first()
        with(prisonerLevelCurrent) {
          assertThat(iepCode).isEqualTo("STD")
          assertThat(prisonId).isEqualTo("MDI")
          assertThat(current).isEqualTo(true)
          assertThat(reviewedBy).isEqualTo("TEST_STAFF1")
        }
      }

      launch {
        val prisonerLevelFirst = repository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)
        with(prisonerLevelFirst!!) {
          assertThat(iepCode).isEqualTo("STD")
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
      PrisonerIepLevel(
        iepCode = "BAS",
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        bookingId = 123456,
        current = true,
        reviewedBy = "TEST_STAFF1",
        reviewTime = LocalDateTime.now().minusDays(2),
        prisonerNumber = "A1234AB",
      ),
    )
    repository.save(
      PrisonerIepLevel(
        iepCode = "STD",
        prisonId = "LEI",
        locationId = "LEI-1-1-002",
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
      PrisonerIepLevel(
        iepCode = "BAS",
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
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

  @Test
  fun `get number of reviews conducted and prisoners reviewed`(): Unit = runBlocking {
    repository.saveAll(
      listOf(
        // Review in matching date range
        PrisonerIepLevel(
          iepCode = "BAS",
          prisonId = "LEI",
          locationId = "LEI-1-1-001",
          bookingId = 111111,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 1, 12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Review in matching date range, same prisoner
        PrisonerIepLevel(
          iepCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-001",
          bookingId = 111111,
          current = false,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 8, 12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Review in matching date range, another prisoner
        PrisonerIepLevel(
          iepCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-002",
          bookingId = 222222,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 20, 12, 0),
          prisonerNumber = "B2222BB",
        ),
        // Review OUTSIDE matching date range - NOT counted
        PrisonerIepLevel(
          iepCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-999",
          bookingId = 999999,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 6, 30, 12, 0),
          prisonerNumber = "Z9999ZZ",
        ),
      ),
    ).toList()

    assertThat(repository.getNumberOfReviewsConductedAndPrisonersReviewed(LocalDate.of(2023, 8, 2)))
      .isEqualTo(ReviewsConductedPrisonersReviewed(3, 2))
  }
}
