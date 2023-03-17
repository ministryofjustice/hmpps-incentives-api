package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.NonTransientDataAccessException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class PrisonIncentiveLevelRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: PrisonIncentiveLevelRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    repository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    repository.deleteAll()
  }

  @Test
  fun `saves blank per-prison incentive level information`(): Unit = runBlocking {
    val entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 0,
      remandSpendLimitInPence = 0,
      convictedTransferLimitInPence = 0,
      convictedSpendLimitInPence = 0,

      visitOrders = 0,
      privilegedVisitOrders = 0,

      new = true,
    )
    repository.save(entity)
    assertThat(repository.count()).isEqualTo(1)
  }

  private fun assertThatEntityCannotBeSaved(entity: PrisonIncentiveLevel) {
    assertThatThrownBy {
      runBlocking { repository.save(entity) }
    }.isInstanceOf(NonTransientDataAccessException::class.java)
  }

  @Test
  fun `fails to save duplicate per-prison incentive level information`(): Unit = runBlocking {
    var entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 2500,
      remandSpendLimitInPence = 25000,
      convictedTransferLimitInPence = 500,
      convictedSpendLimitInPence = 5000,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    repository.save(entity)
    assertThat(repository.count()).isEqualTo(1)
    entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 5500,
      remandSpendLimitInPence = 55000,
      convictedTransferLimitInPence = 1800,
      convictedSpendLimitInPence = 18000,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    assertThatEntityCannotBeSaved(entity)
    assertThat(repository.count()).isEqualTo(1)
  }

  @Test
  fun `fails to save per-prison incentive level information for unknown levels`(): Unit = runBlocking {
    val entity = PrisonIncentiveLevel(
      levelCode = "std", // ← does not exist
      prisonId = "MDI",

      remandTransferLimitInPence = 5500,
      remandSpendLimitInPence = 55000,
      convictedTransferLimitInPence = 1800,
      convictedSpendLimitInPence = 18000,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    assertThatEntityCannotBeSaved(entity)
    assertThat(repository.count()).isEqualTo(0)
  }

  @Test
  fun `fails to save per-prison incentive level information with invalid values`(): Unit = runBlocking {
    val entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 5500,
      remandSpendLimitInPence = 55000,
      convictedTransferLimitInPence = 1800,
      convictedSpendLimitInPence = -1000, // ← cannot be negative

      visitOrders = 2,
      privilegedVisitOrders = 0,

      new = true,
    )
    assertThatEntityCannotBeSaved(entity)
    assertThat(repository.count()).isEqualTo(0)
  }
}
