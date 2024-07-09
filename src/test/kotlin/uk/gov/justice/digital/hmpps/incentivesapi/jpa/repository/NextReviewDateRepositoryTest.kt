package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockAuthUser
class NextReviewDateRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: NextReviewDateRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    repository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    repository.deleteAll()
  }

  @Test
  fun saveAndFindTest(): Unit = runBlocking {
    val bookingId = 1234567L
    val nextReviewDate = LocalDate.parse("2022-12-25")

    repository.save(
      NextReviewDate(bookingId, nextReviewDate),
    )

    val result: NextReviewDate? = repository.findById(bookingId)

    assertThat(result).isNotNull

    with(result) {
      assertThat(this!!.nextReviewDate).isEqualTo(nextReviewDate)
      assertThat(this.whenCreated).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
      assertThat(this.whenUpdated).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
      assertThat(this.id).isEqualTo(bookingId)
      assertThat(this.new).isFalse()
    }
  }

  @Test
  fun bookingIdPrimaryKeyConstraintTest(): Unit = runBlocking {
    val bookingId = 1234567L

    repository.save(
      NextReviewDate(bookingId, LocalDate.parse("2022-12-25")),
    )

    assertThatThrownBy {
      runBlocking {
        // Save another record with same bookingId fails because it's primary key
        repository.save(
          NextReviewDate(bookingId, LocalDate.parse("2030-01-31")),
        )
      }
    }.isInstanceOf(DataIntegrityViolationException::class.java)
  }

  @Test
  fun updateExistingRecordTest(): Unit = runBlocking {
    val bookingId = 1234567L
    val updatedDate = LocalDate.parse("2030-01-31")

    repository.save(
      NextReviewDate(bookingId, LocalDate.parse("2022-12-25")),
    )

    // NOTE: Use value returned by `save()` yields unexpected results in conjunction with `copy()`
    val existingRecord = repository.findById(bookingId)
    repository.save(
      existingRecord!!.copy(nextReviewDate = updatedDate),
    )

    val updatedRecord = repository.findById(bookingId)
    assertThat(updatedRecord!!.nextReviewDate).isEqualTo(updatedDate)
  }

  @Test
  fun findAllByIdTest(): Unit = runBlocking {
    val reviewDates = mapOf(
      123L to LocalDate.parse("2023-01-31"),
      456L to LocalDate.parse("2023-02-28"),
    )

    for ((bookingId, reviewDate) in reviewDates.entries) {
      repository.save(NextReviewDate(bookingId, reviewDate))
    }

    val bookingIds = reviewDates.keys.toList()
    val result = repository.findAllById(bookingIds).toList()

    assertThat(result.size).isEqualTo(reviewDates.size)

    for ((index, bookingId) in bookingIds.withIndex()) {
      with(result[index]) {
        assertThat(this.bookingId).isEqualTo(bookingId)
        assertThat(this.nextReviewDate).isEqualTo(reviewDates[bookingId])
      }
    }
  }
}
