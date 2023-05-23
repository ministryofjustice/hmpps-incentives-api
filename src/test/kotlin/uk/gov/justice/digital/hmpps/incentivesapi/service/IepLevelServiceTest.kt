package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel

class IepLevelServiceTest {
  private val incentiveLevelService: IncentiveLevelService = mock()
  private val iepLevelService = IepLevelService(incentiveLevelService)

  @Nested
  inner class `finding nearest levels` {
    private val prisonId = "MDI"

    @BeforeEach
    fun setup(): Unit = runBlocking {
      whenever(incentiveLevelService.getAllIncentiveLevels()).thenReturn(
        listOf(
          IncentiveLevel(code = "BAS", name = "Basic"),
          IncentiveLevel(code = "ENT", name = "Entry", active = false),
          IncentiveLevel(code = "STD", name = "Standard"),
          IncentiveLevel(code = "ENH", name = "Enhanced"),
          IncentiveLevel(code = "EN2", name = "Enhanced 2"),
          IncentiveLevel(code = "EN3", name = "Enhanced 3"),
        ),
      )
    }

    @Test
    fun `when target level is available`(): Unit = runBlocking {
      // the case for most transfers (most prisons use the same set of levels)
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      for (level in listOf("BAS", "STD", "ENH")) {
        assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, level)).isEqualTo(level)
      }
    }

    @Test
    fun `when target level is not present, nor is there a higher one`(): Unit = runBlocking {
      // can happen when transferring from a prison that has high levels to a typical prison
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target and many other high levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of transferring from a prison that has high levels to another prison
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS", defaultOnAdmission = true, active = true),
        prisonIncentiveLevel(prisonId, "ENT", active = true),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
        prisonIncentiveLevel(prisonId, "ENH", active = false),
        prisonIncentiveLevel(prisonId, "EN2", active = false),
        prisonIncentiveLevel(prisonId, "EN3", active = false),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN3")).isEqualTo("BAS")
    }

    @Test
    fun `when target level is not present, but there is a higher one higher`(): Unit = runBlocking {
      // not likely to happen, but covers case where a prison may have mistakenly removed a level
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
        prisonIncentiveLevel(prisonId, "EN3"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("EN3")
    }

    @Test
    fun `when target and many other low levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of prisons with predominantly high levels
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS", active = false),
        prisonIncentiveLevel(prisonId, "ENT"),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
        prisonIncentiveLevel(prisonId, "ENH", active = false),
        prisonIncentiveLevel(prisonId, "EN2"),
        prisonIncentiveLevel(prisonId, "EN3", defaultOnAdmission = true),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "BAS")).isEqualTo("EN2")
    }

    @Test
    fun `when target level is present but inactive`(): Unit = runBlocking {
      // can happen when a prison disables a high level
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
        prisonIncentiveLevel(prisonId, "EN2", active = false),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target level is globally inactive`(): Unit = runBlocking {
      // unlikely to happen, but feasible if a prisoner has not had a review in a long time
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ENT")).isEqualTo("STD")
    }

    @Test
    fun `falls back to the prison's default when target is not found`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD"), // default & active
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("STD")
    }

    @Test
    fun `falls back to standard when target is not found and there is no default`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set but standard is available
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = true),
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("STD")
    }

    @Test
    fun `falls back to the prison's first level when target is not found and there is no default or standard`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set AND standard is unavailable
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS"),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
        prisonIncentiveLevel(prisonId, "ENH"),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("BAS")
    }

    @Test
    fun `fails when a prison does not have any available levels`(): Unit = runBlocking {
      // extreme edge case when the prison does not have any levels
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS", active = false),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
        prisonIncentiveLevel(prisonId, "ENH", active = false),
      )
      assertThatThrownBy {
        runBlocking { iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "STD") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }

    @Test
    fun `fails when there are no available levels in a prison because a default cannot be chosen`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have any levels
      val prisonLevels = listOf(
        prisonIncentiveLevel(prisonId, "BAS", active = false),
        prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
        prisonIncentiveLevel(prisonId, "ENH", active = false),
      )
      assertThatThrownBy {
        runBlocking { iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }
  }
}
