package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.DataIntegrityException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel

class IepLevelServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val iepLevelService = IepLevelService(prisonApiService)

  @Test
  fun `prison has all 5 iep levels`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, default = true),
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
            IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
            IepLevel(iepLevel = "ENH2", iepDescription = "Enhanced 2", sequence = 4),
            IepLevel(iepLevel = "ENH3", iepDescription = "Enhanced 3", sequence = 5),
          )
        )

        // When
        val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonId)

        // Then - all configured IepLevel record are returned
        assertThat(iepLevelsForPrison).hasSize(5)
        assertThat(iepLevelsForPrison.first().default).isTrue
        assertThat(iepLevelsForPrison.last().iepLevel).isEqualTo("ENH3")
      }
    }
  }

  @Test
  fun `prison has up to ENH only`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, default = true),
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
            IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
          )
        )

        // When
        val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonId)

        // Then - do not include ENH2 or ENH3
        assertThat(iepLevelsForPrison).hasSize(3)
        assertThat(iepLevelsForPrison.first().default).isTrue
        assertThat(iepLevelsForPrison.last().iepLevel).isEqualTo("ENH")
      }
    }
  }

  @Nested
  inner class `finding nearest levels` {
    private val prisonId = "MDI"

    @BeforeEach
    fun setup(): Unit = runBlocking {
      whenever(prisonApiService.getIepLevels()).thenReturn(
        flowOf(
          IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
          IepLevel(iepLevel = "ENT", iepDescription = "Entry", sequence = 2, active = false),
          IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 3, default = true),
          IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 4),
          IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 5),
          IepLevel(iepLevel = "EN3", iepDescription = "Enhanced 3", sequence = 6),
        )
      )
    }

    @Test
    fun `when target level is available`(): Unit = runBlocking {
      // the case for most transfers (most prisons use the same set of levels)
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      for (level in listOf("BAS", "STD", "ENH")) {
        assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, level)).isEqualTo(level)
      }
    }

    @Test
    fun `when target level is not present, nor is there a higher one`(): Unit = runBlocking {
      // can happen when transferring from a prison that has high levels to a typical prison
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target and many other high levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of transferring from a prison that has high levels to another prison
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, default = true),
        IepLevel(iepLevel = "ENT", iepDescription = "Entry", sequence = 2),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 3, active = false),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 4, active = false),
        IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 5, active = false),
        IepLevel(iepLevel = "EN3", iepDescription = "Enhanced 3", sequence = 6, active = false),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN3")).isEqualTo("BAS")
    }

    @Test
    fun `when target level is not present, but there is a higher one higher`(): Unit = runBlocking {
      // not likely to happen, but covers case where a prison may have mistakenly removed a level
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
        IepLevel(iepLevel = "EN3", iepDescription = "Enhanced 3", sequence = 6),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("EN3")
    }

    @Test
    fun `when target and many other low levels are unavailable`(): Unit = runBlocking {
      // exaggerated version of prisons with predominantly high levels
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, active = false),
        IepLevel(iepLevel = "ENT", iepDescription = "Entry", sequence = 2),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 3, active = false),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 4, active = false),
        IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 5),
        IepLevel(iepLevel = "EN3", iepDescription = "Enhanced 3", sequence = 6, default = true),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "BAS")).isEqualTo("EN2")
    }

    @Test
    fun `when target level is present but inactive`(): Unit = runBlocking {
      // can happen when a prison disables a high level
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
        IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 4, active = false),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "EN2")).isEqualTo("ENH")
    }

    @Test
    fun `when target level is globally inactive`(): Unit = runBlocking {
      // unlikely to happen, but feasible if a prisoner has not had a review in a long time
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ENT")).isEqualTo("STD")
    }

    @Test
    fun `falls back to the prison's default when target is not found`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("STD")
    }

    @Test
    fun `falls back to standard when target is not found and there is no default`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set but standard is available
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("STD")
    }

    @Test
    fun `falls back to the prison's first level when target is not found and there is no default or standard`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have a default set AND standard is unavailable
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, active = false),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      )
      assertThat(iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC")).isEqualTo("BAS")
    }

    @Test
    fun `fails when a prison does not have any available levels`(): Unit = runBlocking {
      // extreme edge case when the prison does not have any levels
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, active = false),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, active = false),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, active = false),
      )
      assertThatThrownBy {
        runBlocking { iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "STD") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }

    @Test
    fun `fails when there are no available levels in a prison because a default cannot be chosen`(): Unit = runBlocking {
      // extreme edge case when a level is globally removed AND the prison does not have any levels
      val prisonLevels = listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, active = false),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, active = false),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, active = false),
      )
      assertThatThrownBy {
        runBlocking { iepLevelService.findNearestHighestLevel(prisonId, prisonLevels, "ABC") }
      }.isInstanceOf(DataIntegrityException::class.java)
    }
  }
}
