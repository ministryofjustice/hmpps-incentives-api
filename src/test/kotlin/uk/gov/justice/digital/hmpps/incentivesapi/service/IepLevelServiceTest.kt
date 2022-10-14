package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel as GlobalIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository

class IepLevelServiceTest {

  private val iepLevelRepository: IepLevelRepository = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val iepLevelService = IepLevelService(iepLevelRepository, prisonApiService)

  @Test
  fun `prison has all 5 iep levels`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(iepLevelRepository.findAll()).thenReturn(fiveIepLevels)
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
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
        // TODO: Prison API endpoint doesn't have `default` field. No IEP level is default at the moment
        // assertThat(iepLevelsForPrison.first().default).isTrue
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
        whenever(iepLevelRepository.findAll()).thenReturn(fiveIepLevels)
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
            IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
          )
        )

        // When
        val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonId)

        // Then - do not include ENH2 or ENH3
        assertThat(iepLevelsForPrison).hasSize(3)
        // TODO: Prison API endpoint doesn't have `default` field. No IEP level is default at the moment
        // assertThat(iepLevelsForPrison.first().default).isTrue
        assertThat(iepLevelsForPrison.last().iepLevel).isEqualTo("ENH")
      }
    }
  }

  @Test
  fun `do not return inactive IepLevel`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(iepLevelRepository.findAll()).thenReturn(
          flowOf(
            GlobalIepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
            GlobalIepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2, active = false),
          )
        )
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
          )
        )

        // When
        val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonId)

        // Then - only BAS is returned as STD is inactive
        assertThat(iepLevelsForPrison).hasSize(1)
        assertThat(iepLevelsForPrison.last().iepLevel).isEqualTo("BAS")
      }
    }
  }

  @Test
  fun `doesn't return unknown IepLevels (not present in IepLevel table)`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(iepLevelRepository.findAll()).thenReturn(
          flowOf(
            GlobalIepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
            GlobalIepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2, active = false),
          )
        )
        whenever(prisonApiService.getIepLevelsForPrison(prisonId)).thenReturn(
          flowOf(
            IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
            IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
            // Unknown IEP level
            IepLevel(iepLevel = "UNK", iepDescription = "Unknown", sequence = 3),
          )
        )

        // When
        val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonId)

        // Then - only BAS is returned as STD is inactive and "UNK" is not in the IepLevels table
        assertThat(iepLevelsForPrison.map { it.iepLevel }).isEqualTo(listOf("BAS"))
      }
    }
  }

  private val fiveIepLevels = flowOf(
    GlobalIepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
    GlobalIepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2),
    GlobalIepLevel(iepCode = "ENH", iepDescription = "Enhanced", sequence = 3),
    GlobalIepLevel(iepCode = "ENH2", iepDescription = "Enhanced 2", sequence = 4),
    GlobalIepLevel(iepCode = "ENH3", iepDescription = "Enhanced 3", sequence = 5),
  )
}
