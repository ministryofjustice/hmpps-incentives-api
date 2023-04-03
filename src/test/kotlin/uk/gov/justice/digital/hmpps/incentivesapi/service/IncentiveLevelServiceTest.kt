package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class IncentiveLevelServiceTest {

  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val incentiveLevelRepository: IncentiveLevelRepository = mock()
  private val prisonIncentiveLevelService: PrisonIncentiveLevelService = mock()
  private val featureFlagsService: FeatureFlagsService = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val incentiveReviewsService = IncentiveLevelService(
    clock,
    incentiveLevelRepository,
    prisonIncentiveLevelService,
    featureFlagsService,
    prisonApiService,
  )

  @Test
  fun `query database if feature flag is true`(): Unit = runBlocking {
    // Given
    whenever(featureFlagsService.isIncentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase()).thenReturn(true)
    whenever(incentiveLevelRepository.findAllByOrderBySequence()).thenReturn(
      flowOf(
        IncentiveLevel(
          code = "",
          description = "Standard",
          sequence = 10,
          new = true,
        ),
      ),
    )

    // When
    incentiveReviewsService.getAllIncentiveLevelsMapByCode()

    // Then
    verify(incentiveLevelRepository, times(1)).findAllByOrderBySequence()
    verifyNoInteractions(prisonApiService)
  }

  @Test
  fun `call PrisonAPI if feature flag is false`(): Unit = runBlocking {
    // Given
    whenever(featureFlagsService.isIncentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase()).thenReturn(false)
    whenever(prisonApiService.getIepLevels()).thenReturn(
      listOf(
        IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
        IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
        IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
      ),
    )

    // When
    val activeIncentiveLevelsByCode = incentiveReviewsService.getAllIncentiveLevelsMapByCode()

    // Then
    assertThat(activeIncentiveLevelsByCode).isEqualTo(
      mapOf(
        "BAS" to uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel(code = "BAS", description = "Basic"),
        "STD" to uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel(code = "STD", description = "Standard"),
        "ENH" to uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel(code = "ENH", description = "Enhanced"),
      ),
    )
    verify(prisonApiService, times(1)).getIepLevels()
    verifyNoInteractions(incentiveLevelRepository)
  }
}
