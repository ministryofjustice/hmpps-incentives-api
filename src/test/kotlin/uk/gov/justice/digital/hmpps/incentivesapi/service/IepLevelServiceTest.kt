package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepPrison
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepPrisonRepository

class IepLevelServiceTest {

  private val iepLevelRepository: IepLevelRepository = mock()
  private val iepPrisonRepository: IepPrisonRepository = mock()
  private val iepLevelService = IepLevelService(iepLevelRepository, iepPrisonRepository)

  @Test
  fun `prison has all 5 iep levels`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(iepLevelRepository.findAll()).thenReturn(fiveIepLevels)
        whenever(iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)).thenReturn(
          flowOf(
            IepPrison(iepCode = "BAS", prisonId = prisonId, defaultIep = true),
            IepPrison(iepCode = "STD", prisonId = prisonId, defaultIep = false),
            IepPrison(iepCode = "ENH", prisonId = prisonId, defaultIep = false),
            IepPrison(iepCode = "ENH2", prisonId = prisonId, defaultIep = false),
            IepPrison(iepCode = "ENH3", prisonId = prisonId, defaultIep = false),
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
        whenever(iepLevelRepository.findAll()).thenReturn(fiveIepLevels)
        whenever(iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)).thenReturn(
          flowOf(
            IepPrison(iepCode = "BAS", prisonId = prisonId, defaultIep = true),
            IepPrison(iepCode = "STD", prisonId = prisonId, defaultIep = false),
            IepPrison(iepCode = "ENH", prisonId = prisonId, defaultIep = false),
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

  @Test
  fun `do not return inactive IepLevel`() {
    runBlocking {
      coroutineScope {
        // Given
        val prisonId = "XXX"
        whenever(iepLevelRepository.findAll()).thenReturn(
          flowOf(
            IepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
            IepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2, active = false),
          )
        )
        whenever(iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)).thenReturn(
          flowOf(
            IepPrison(iepCode = "BAS", prisonId = prisonId, defaultIep = true),
            IepPrison(iepCode = "STD", prisonId = prisonId, defaultIep = false),
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
            IepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
            IepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2, active = false),
          )
        )
        whenever(iepPrisonRepository.findAllByPrisonIdAndActiveIsTrue(prisonId)).thenReturn(
          flowOf(
            IepPrison(iepCode = "BAS", prisonId = prisonId, defaultIep = true),
            IepPrison(iepCode = "STD", prisonId = prisonId, defaultIep = false),
            // Unknown IEP level
            IepPrison(iepCode = "UNK", prisonId = prisonId, defaultIep = false),
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
    IepLevel(iepCode = "BAS", iepDescription = "Basic", sequence = 1),
    IepLevel(iepCode = "STD", iepDescription = "Standard", sequence = 2),
    IepLevel(iepCode = "ENH", iepDescription = "Enhanced", sequence = 3),
    IepLevel(iepCode = "ENH2", iepDescription = "Enhanced 2", sequence = 4),
    IepLevel(iepCode = "ENH3", iepDescription = "Enhanced 3", sequence = 5),
  )
}
