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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class KpiRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: KpiRepository

  @Autowired
  lateinit var reviewsRepository: IncentiveReviewRepository

  @Autowired
  lateinit var nextReviewDateRepository: NextReviewDateRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    repository.deleteAll()
    reviewsRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    repository.deleteAll()
    reviewsRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @Test
  fun saveAndFindTest(): Unit = runBlocking {
    val day = LocalDate.parse("2022-12-01")

    repository.save(
      Kpi(day, 5000, 30000, 20000),
    )

    val result: Kpi? = repository.findById(day)

    assertThat(result).isNotNull

    with(result) {
      assertThat(this!!.day).isEqualTo(day)
      assertThat(this.overdueReviews).isEqualTo(5000)
      assertThat(this.previousMonthReviewsConducted).isEqualTo(30000)
      assertThat(this.previousMonthPrisonersReviewed).isEqualTo(20000)
      assertThat(this.whenCreated).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
      assertThat(this.whenUpdated).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.MINUTES))
      assertThat(this.id).isEqualTo(day)
      assertThat(this.new).isFalse()
    }
  }

  @Test
  fun dayPrimaryKeyConstraintTest(): Unit = runBlocking {
    val day = LocalDate.parse("2022-12-01")

    repository.save(
      Kpi(day, 5000, 30000, 20000),
    )

    assertThatThrownBy {
      runBlocking {
        // Save another KPI record with same date fails because it's primary key
        repository.save(
          Kpi(day, 42, 42, 42),
        )
      }
    }.isInstanceOf(DataIntegrityViolationException::class.java)
  }

  @Test
  fun updateExistingRecordTest(): Unit = runBlocking {
    val day = LocalDate.parse("2022-12-01")

    repository.save(
      Kpi(day, 5000, 30000, 20000),
    )

    // NOTE: Use value returned by `save()` yields unexpected results in conjunction with `copy()`
    val existingRecord = repository.findById(day)
    repository.save(
      existingRecord!!.copy(overdueReviews = 42),
    )

    val updatedRecord = repository.findById(day)
    assertThat(updatedRecord!!.overdueReviews).isEqualTo(42)
    assertThat(updatedRecord.previousMonthReviewsConducted).isEqualTo(30000)
    assertThat(updatedRecord.previousMonthPrisonersReviewed).isEqualTo(20000)
  }

  @Test
  fun `get number of reviews conducted and prisoners reviewed`(): Unit = runBlocking {
    reviewsRepository.saveAll(
      listOf(
        // Review in matching date range
        IncentiveReview(
          levelCode = "BAS",
          prisonId = "LEI",
          locationId = "LEI-1-1-001",
          bookingId = 111111,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 1, 12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Review in matching date range, same prisoner
        IncentiveReview(
          levelCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-001",
          bookingId = 111111,
          current = false,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 8, 12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Review in matching date range, another prisoner
        IncentiveReview(
          levelCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-002",
          bookingId = 222222,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.of(2023, 7, 20, 12, 0),
          prisonerNumber = "B2222BB",
        ),
        // Review OUTSIDE matching date range - NOT counted
        IncentiveReview(
          levelCode = "STD",
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

  @Test
  fun `get prisoner numbers overdue a review`(): Unit = runBlocking {
    nextReviewDateRepository.saveAll(
      listOf(
        // Prisoner A1111AA
        NextReviewDate(
          bookingId = 111111,
          nextReviewDate = LocalDate.now().minusDays(100),
        ),
        // Prisoner A1111AA, different booking ID
        NextReviewDate(
          bookingId = 222222,
          nextReviewDate = LocalDate.now().minusDays(200),
        ),
        // Prisoner A1111AA, different booking ID, NOT OVERDUE
        NextReviewDate(
          bookingId = 444444,
          nextReviewDate = LocalDate.now().plusYears(1),
        ),
        // Prisoner C3333CC
        NextReviewDate(
          bookingId = 333333,
          nextReviewDate = LocalDate.now().minusDays(200),
        ),
        // Prisoner Z9999ZZ (not overdue a review)
        NextReviewDate(
          bookingId = 999999,
          nextReviewDate = LocalDate.now().plusYears(1),
        ),
      ),
    ).toList()
    reviewsRepository.saveAll(
      listOf(
        // A prisoner
        IncentiveReview(
          levelCode = "BAS",
          prisonId = "LEI",
          locationId = "LEI-1-1-001",
          bookingId = 111111,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDate.now().minusDays(100).minusDays(7).atTime(12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Same prisoner number, different booking ID
        IncentiveReview(
          levelCode = "STD",
          prisonId = "MDI",
          locationId = "LEI-1-1-001",
          bookingId = 222222,
          current = false,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDate.now().minusDays(200).minusYears(1).atTime(12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Same prisoner number, different booking ID, NOT OVERDUE
        IncentiveReview(
          levelCode = "STD",
          prisonId = "MDI",
          locationId = "LEI-1-1-001",
          bookingId = 444444,
          current = false,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDate.now().atTime(12, 0),
          prisonerNumber = "A1111AA",
        ),
        // Another prisoner
        IncentiveReview(
          levelCode = "STD",
          prisonId = "LEI",
          locationId = "LEI-1-1-002",
          bookingId = 333333,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDate.now().minusDays(200).minusYears(1).atTime(12, 0),
          prisonerNumber = "C3333CC",
        ),
        // Another prisoner (not overdue a review)
        IncentiveReview(
          levelCode = "STD",
          prisonId = "MDI",
          locationId = "MDI-1-1-999",
          bookingId = 999999,
          current = true,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDate.now().atTime(12, 0),
          prisonerNumber = "Z9999ZZ",
        ),
      ),
    ).toList()

    assertThat(repository.getPrisonerNumbersOverdueReview().toList())
      .isEqualTo(
        listOf("C3333CC"),
      )
  }
}
