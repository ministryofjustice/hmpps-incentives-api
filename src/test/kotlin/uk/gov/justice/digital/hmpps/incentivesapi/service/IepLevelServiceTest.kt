package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = false),
            IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, default = false),
            IepLevel(iepLevel = "ENH2", iepDescription = "Enhanced 2", sequence = 4, default = false),
            IepLevel(iepLevel = "ENH3", iepDescription = "Enhanced 3", sequence = 5, default = false),
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
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = false),
            IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, default = false),
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
}
