package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

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
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class KpiRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: KpiRepository

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
    assertThat(updatedRecord!!.previousMonthReviewsConducted).isEqualTo(30000)
    assertThat(updatedRecord!!.previousMonthPrisonersReviewed).isEqualTo(20000)
  }
}
