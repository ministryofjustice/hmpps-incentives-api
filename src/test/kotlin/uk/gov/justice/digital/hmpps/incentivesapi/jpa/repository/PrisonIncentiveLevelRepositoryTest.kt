package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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

      remandTransferLimitInPence = 2750,
      remandSpendLimitInPence = 27500,
      convictedTransferLimitInPence = 550,
      convictedSpendLimitInPence = 5500,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    repository.save(entity)
    assertThat(repository.count()).isEqualTo(1)
    entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 6050,
      remandSpendLimitInPence = 60500,
      convictedTransferLimitInPence = 1980,
      convictedSpendLimitInPence = 19800,

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

      remandTransferLimitInPence = 6050,
      remandSpendLimitInPence = 60500,
      convictedTransferLimitInPence = 1980,
      convictedSpendLimitInPence = 19800,

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

      remandTransferLimitInPence = 6050,
      remandSpendLimitInPence = 60500,
      convictedTransferLimitInPence = 1980,
      convictedSpendLimitInPence = -19800, // ← cannot be negative

      visitOrders = 2,
      privilegedVisitOrders = 0,

      new = true,
    )
    assertThatEntityCannotBeSaved(entity)
    assertThat(repository.count()).isEqualTo(0)
  }

  @Test
  fun `finds prison incentive levels in globally defined order`(): Unit = runBlocking {
    // when levels are saved out-of-order
    listOf("ENH", "BAS", "EN2", "ENT", "STD").forEach { levelCode ->
      val entity = PrisonIncentiveLevel(
        levelCode = levelCode,
        prisonId = "MDI",
        active = levelCode != "ENT",

        remandTransferLimitInPence = 6050,
        remandSpendLimitInPence = 60500,
        convictedTransferLimitInPence = 1980,
        convictedSpendLimitInPence = 19800,

        visitOrders = 2,
        privilegedVisitOrders = 1,

        new = true,
      )
      repository.save(entity)
    }

    // assert that repository returns prison incentive levels in globally-defined order
    var returnedOrder = repository.findAllByPrisonId("MDI").toList()
    assertThat(returnedOrder.map { it.levelCode }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "ENT"))
    assertThat(returnedOrder.map { it.levelDescription }).isEqualTo(listOf("Basic", "Standard", "Enhanced", "Enhanced 2", "Entry"))
    returnedOrder = repository.findAllByPrisonIdAndActiveIsTrue("MDI").toList()
    assertThat(returnedOrder.map { it.levelDescription }).isEqualTo(listOf("Basic", "Standard", "Enhanced", "Enhanced 2"))
  }

  @Test
  fun `loads level description using join to global incentive levels`(): Unit = runBlocking {
    val entity = PrisonIncentiveLevel(
      levelCode = "ENH",
      prisonId = "MDI",

      remandTransferLimitInPence = 6600,
      remandSpendLimitInPence = 66000,
      convictedTransferLimitInPence = 3300,
      convictedSpendLimitInPence = 33000,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    repository.save(entity)

    var savedEntity = repository.findFirstByPrisonIdAndLevelCode("MDI", "ENH")
    assertThat(savedEntity?.levelDescription).isEqualTo("Enhanced")
    savedEntity = repository.findFirstByPrisonIdAndLevelCodeAndActiveIsTrue("MDI", "ENH")
    assertThat(savedEntity?.levelDescription).isEqualTo("Enhanced")
  }

  private suspend fun generateDefaultData() {
    listOf("BAS", "STD", "ENH", "EN2", "ENT").forEach { levelCode ->
      listOf("BAI", "MDI", "WRI").forEach { prisonId ->
        val entity = PrisonIncentiveLevel(
          levelCode = levelCode,
          prisonId = prisonId,
          active = levelCode != "ENT",
          defaultOnAdmission = levelCode == "STD",

          remandTransferLimitInPence = 6050,
          remandSpendLimitInPence = 60500,
          convictedTransferLimitInPence = 1980,
          convictedSpendLimitInPence = 19800,

          visitOrders = 2,
          privilegedVisitOrders = 1,

          new = true,
        )
        repository.save(entity)
      }
    }
    assertThat(repository.count()).isEqualTo(15)
  }

  @Test
  fun `does not join on incentive levels when calling standard auto-generated repository methods`(): Unit = runBlocking {
    generateDefaultData()

    repository.findAll().collect {
      assertThat(it.levelDescription).isNull()
    }
    val someId = repository.findAll().first().id
    val someEntity = repository.findById(someId)!!
    assertThat(someEntity.levelDescription).isNull()
    repository.findAllById(listOf(someId)).collect {
      assertThat(it.levelDescription).isNull()
    }
  }

  @Test
  fun `loads default incentive level information in a prison`(): Unit = runBlocking {
    generateDefaultData()

    val entity = repository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue("MDI")
    assertThat(entity?.levelCode).isEqualTo("STD")
    assertThat(entity?.levelDescription).isEqualTo("Standard")

    val missing = repository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue("LEI")
    assertThat(missing).isNull()
  }
}
