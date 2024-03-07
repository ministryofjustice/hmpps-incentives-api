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

class NearestPrisonIncentiveLevelServiceTest {
  private val incentiveLevelService: IncentiveLevelAuditedService = mock()
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService = mock()
  private val nearestPrisonIncentiveLevelService = NearestPrisonIncentiveLevelService(incentiveLevelService, prisonIncentiveLevelService)

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
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      for (level in listOf("BAS", "STD", "ENH")) {
        assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, level)).isEqualTo(level)
      }
    }

    @Test
    fun `when target level is not present, nor is there a higher one`(): Unit = runBlocking {
      // can happen when transferring from a prison that has high levels to a typical prison
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target and many other high levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of transferring from a prison that has high levels to another prison
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS", defaultOnAdmission = true, active = true),
          prisonIncentiveLevel(prisonId, "ENT", active = true),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
          prisonIncentiveLevel(prisonId, "ENH", active = false),
          prisonIncentiveLevel(prisonId, "EN2", active = false),
          prisonIncentiveLevel(prisonId, "EN3", active = false),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "EN3")).isEqualTo("BAS")
    }

    @Test
    fun `when target level is not present, but there is a higher one higher`(): Unit = runBlocking {
      // not likely to happen, but covers case where a prison may have mistakenly removed a level
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
          prisonIncentiveLevel(prisonId, "EN3"),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "EN2")).isEqualTo("EN3")
    }

    @Test
    fun `when target and many other low levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of prisons with predominantly high levels
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS", active = false),
          prisonIncentiveLevel(prisonId, "ENT"),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
          prisonIncentiveLevel(prisonId, "ENH", active = false),
          prisonIncentiveLevel(prisonId, "EN2"),
          prisonIncentiveLevel(prisonId, "EN3", defaultOnAdmission = true),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "BAS")).isEqualTo("EN2")
    }

    @Test
    fun `when target level is present but inactive`(): Unit = runBlocking {
      // can happen when a prison disables a high level
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
          prisonIncentiveLevel(prisonId, "EN2", active = false),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target level is globally inactive`(): Unit = runBlocking {
      // unlikely to happen, but feasible if a prisoner has not had a review in a long time
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "ENT")).isEqualTo("STD")
    }

    @Test
    fun `falls back to the prison's default when target is not found`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          // default & active
          prisonIncentiveLevel(prisonId, "STD"),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "ABC")).isEqualTo("STD")
    }

    @Test
    fun `fails when target is not found and there is no default`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set but standard is available
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = true),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      assertThatThrownBy {
        runBlocking { assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "ABC")) }
      }.isInstanceOf(DataIntegrityException::class.java)
    }

    @Test
    fun `fails when target is not found and there is no default or standard`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set AND standard is unavailable
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS"),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
          prisonIncentiveLevel(prisonId, "ENH"),
        ),
      )

      assertThatThrownBy {
        runBlocking { assertThat(nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "ABC")) }
      }.isInstanceOf(DataIntegrityException::class.java)
    }

    @Test
    fun `fails when a prison does not have any available levels`(): Unit = runBlocking {
      // extreme edge case when the prison does not have any levels
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS", active = false),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
          prisonIncentiveLevel(prisonId, "ENH", active = false),
        ),
      )

      assertThatThrownBy {
        runBlocking { nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "STD") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }

    @Test
    fun `fails when there are no available levels in a prison because a default cannot be chosen`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have any levels
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonId)).thenReturn(
        listOf(
          prisonIncentiveLevel(prisonId, "BAS", active = false),
          prisonIncentiveLevel(prisonId, "STD", defaultOnAdmission = false, active = false),
          prisonIncentiveLevel(prisonId, "ENH", active = false),
        ),
      )

      assertThatThrownBy {
        runBlocking { nearestPrisonIncentiveLevelService.findNearestHighestLevel(prisonId, "ABC") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }
  }
}
