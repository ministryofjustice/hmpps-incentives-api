package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.NonTransientDataAccessException
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@DisplayName("Prison incentive level repository")
@DataR2dbcTest
@ActiveProfiles("test")
@WithMockAuthUser
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

      remandTransferLimitInPence = 27_50,
      remandSpendLimitInPence = 275_00,
      convictedTransferLimitInPence = 5_50,
      convictedSpendLimitInPence = 55_00,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    repository.save(entity)
    assertThat(repository.count()).isEqualTo(1)
    entity = PrisonIncentiveLevel(
      levelCode = "STD",
      prisonId = "MDI",

      remandTransferLimitInPence = 60_50,
      remandSpendLimitInPence = 605_00,
      convictedTransferLimitInPence = 19_80,
      convictedSpendLimitInPence = 198_00,

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
      // does not exist
      levelCode = "std",
      prisonId = "MDI",

      remandTransferLimitInPence = 60_50,
      remandSpendLimitInPence = 605_00,
      convictedTransferLimitInPence = 19_80,
      convictedSpendLimitInPence = 198_00,

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

      remandTransferLimitInPence = 60_50,
      remandSpendLimitInPence = 605_00,
      convictedTransferLimitInPence = 19_80,
      // cannot be negative
      convictedSpendLimitInPence = -198_00,

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

        remandTransferLimitInPence = 60_50,
        remandSpendLimitInPence = 605_00,
        convictedTransferLimitInPence = 19_80,
        convictedSpendLimitInPence = 198_00,

        visitOrders = 2,
        privilegedVisitOrders = 1,

        new = true,
      )
      repository.save(entity)
    }

    // assert that repository returns prison incentive levels in globally-defined order
    var returnedOrder = repository.findAllByPrisonId("MDI").toList()
    assertThat(returnedOrder.map { it.levelCode }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "ENT"))
    assertThat(
      returnedOrder.map {
        it.levelName
      },
    ).isEqualTo(listOf("Basic", "Standard", "Enhanced", "Enhanced 2", "Entry"))
    returnedOrder = repository.findAllByPrisonIdAndActiveIsTrue("MDI").toList()
    assertThat(returnedOrder.map { it.levelName }).isEqualTo(listOf("Basic", "Standard", "Enhanced", "Enhanced 2"))
  }

  @Test
  fun `loads level description using join to global incentive levels`(): Unit = runBlocking {
    val entity = PrisonIncentiveLevel(
      levelCode = "ENH",
      prisonId = "MDI",

      remandTransferLimitInPence = 66_00,
      remandSpendLimitInPence = 660_00,
      convictedTransferLimitInPence = 33_00,
      convictedSpendLimitInPence = 330_00,

      visitOrders = 2,
      privilegedVisitOrders = 1,

      new = true,
    )
    repository.save(entity)

    val savedEntity = repository.findFirstByPrisonIdAndLevelCode("MDI", "ENH")
    assertThat(savedEntity?.levelName).isEqualTo("Enhanced")
  }

  private fun makeNewEntity(levelCode: String, prisonId: String) = PrisonIncentiveLevel(
    levelCode = levelCode,
    prisonId = prisonId,
    active = levelCode != "ENT",
    defaultOnAdmission = levelCode == "STD",

    remandTransferLimitInPence = 60_50,
    remandSpendLimitInPence = 605_00,
    convictedTransferLimitInPence = 19_80,
    convictedSpendLimitInPence = 198_00,

    visitOrders = 2,
    privilegedVisitOrders = 1,

    new = true,
  )

  private suspend fun generateDefaultData() {
    listOf("BAS", "STD", "ENH", "EN2", "ENT").forEach { levelCode ->
      listOf("BAI", "MDI", "WRI").forEach { prisonId ->
        repository.save(makeNewEntity(levelCode, prisonId))
      }
    }
    assertThat(repository.count()).isEqualTo(15)
  }

  @Test
  fun `does not join on incentive levels when calling standard auto-generated repository methods`(): Unit =
    runBlocking {
      generateDefaultData()

      repository.findAll().collect {
        assertThat(it.levelName).isNull()
      }
      val someId = repository.findAll().first().id
      val someEntity = repository.findById(someId)!!
      assertThat(someEntity.levelName).isNull()
      repository.findAllById(listOf(someId)).collect {
        assertThat(it.levelName).isNull()
      }
    }

  @Test
  fun `loads default incentive level information in a prison`(): Unit = runBlocking {
    generateDefaultData()

    val entity = repository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue("MDI")
    assertThat(entity?.levelCode).isEqualTo("STD")
    assertThat(entity?.levelName).isEqualTo("Standard")

    val missing = repository.findFirstByPrisonIdAndActiveIsTrueAndDefaultIsTrue("LEI")
    assertThat(missing).isNull()
  }

  @Test
  fun `sets other levels to be non-default`(): Unit = runBlocking {
    generateDefaultData()

    var previouslyDefaultLevelCode = repository.setOtherLevelsNotDefaultForAdmission("MDI", "ENH").toSet()
    assertThat(previouslyDefaultLevelCode).isEqualTo(setOf("STD"))

    previouslyDefaultLevelCode = repository.setOtherLevelsNotDefaultForAdmission("MDI", "ENH").toSet()
    assertThat(previouslyDefaultLevelCode).isEqualTo(emptySet<String>())
  }

  @Test
  fun `find prisons that have active levels`(): Unit = runBlocking {
    generateDefaultData()
    // Generate inactive prisons that will not be returned:
    listOf("BAS", "STD", "ENH", "EN2", "ENT").forEach { levelCode ->
      listOf("EXI", "LEI").forEach { prisonId ->
        repository.save(makeNewEntity(levelCode, prisonId).copy(active = false))
      }
    }

    val prisonIds = repository.findPrisonIdsWithActiveLevels().toSet()
    assertThat(prisonIds).isEqualTo(setOf("BAI", "MDI", "WRI"))
  }

  @Test
  fun `find prisons that have specific level active`(): Unit = runBlocking {
    generateDefaultData()
    // Generate active prisons with inactive EN2 that will not be returned:
    listOf("BAS", "STD", "ENH", "EN2").forEach { levelCode ->
      listOf("EXI", "LEI").forEach { prisonId ->
        repository.save(makeNewEntity(levelCode, prisonId).copy(active = levelCode != "EN2"))
      }
    }

    var prisonIds = repository.findPrisonIdsWithActiveLevels().toSet()
    assertThat(prisonIds).isEqualTo(setOf("BAI", "MDI", "WRI", "EXI", "LEI"))

    prisonIds = repository.findPrisonIdsWithActiveLevel("EN2").toSet()
    assertThat(prisonIds).isEqualTo(setOf("BAI", "MDI", "WRI"))
  }
}
