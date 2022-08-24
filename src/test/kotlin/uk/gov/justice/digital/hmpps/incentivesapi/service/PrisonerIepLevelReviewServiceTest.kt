package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PrisonerIepLevelReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val iepLevelRepository: IepLevelRepository = mock()
  private val iepLevelService: IepLevelService = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private var clock: Clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  private val prisonerIepLevelReviewService = PrisonerIepLevelReviewService(
    prisonApiService,
    prisonerIepLevelRepository,
    iepLevelRepository,
    iepLevelService,
    authenticationFacade,
    clock,
  )

  @Nested
  inner class getPrisonerIepLevelHistory {
    @Test
    fun `will call prison api if useNomisData is true`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonApiService.getIEPSummaryForPrisoner(1234567, withDetails = true)).thenReturn(iepSummaryWithDetail)

        // When
        prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = true)

        // Then
        verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(1234567, true)
        verifyNoInteractions(prisonerIepLevelRepository)
      }
    }

    @Test
    fun `will call prison api if useNomisData is true and will not return iep details if withDetails is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonApiService.getIEPSummaryForPrisoner(1234567, withDetails = false)).thenReturn(iepSummary())

        // When
        prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = true, withDetails = false)

        // Then
        verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(1234567, false)
        verifyNoInteractions(prisonerIepLevelRepository)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(1234567)).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderBySequenceDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isEqualTo(2)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false and will not return iep details if withDetails is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(1234567)).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false, withDetails = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderBySequenceDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isZero()
      }
    }
  }

  @Nested
  inner class processReceivedPrisoner {
    @Test
    fun `process ADMISSION`(): Unit = runBlocking {
      // Given - default for that prison is Enhanced
      val prisonOffenderEvent = prisonOffenderEvent("ADMISSION")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation())
      whenever(prisonApiService.getLocationById(prisonerAtLocation().assignedLivingUnitId, true)).thenReturn(location)
      // Enhanced is the default for this prison so use that
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation().agencyId)).thenReturn(
        listOf(
          IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 1, default = false),
          IepLevel(iepLevel = "ENC", iepDescription = "Enhanced", sequence = 1, default = true),
        )
      )

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = "ENC",
          commentText = prisonOffenderEvent.description,
          bookingId = prisonerAtLocation().bookingId,
          prisonId = location.agencyId,
          locationId = location.description,
          sequence = 1,
          current = true,
          reviewedBy = "incentives-api",
          reviewTime = LocalDateTime.now(clock),
          reviewType = ReviewType.INITIAL,
          prisonerNumber = prisonerAtLocation().offenderNo
        )
      )
    }

    @Test
    fun `process TRANSFERRED when offender stays on same incentive level as previous prison`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      val prisonerAtLocation = prisonerAtLocation("MDI")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId)).thenReturn(basicStandardEnhanced)
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "STD", LocalDateTime.now()),
        iepDetail("BXI", "STD", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary(iepDetails = iepDetails)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then - this prison has STD configured so the offender stays on the same level
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = "STD",
          commentText = prisonOffenderEvent.description,
          bookingId = prisonerAtLocation.bookingId,
          prisonId = location.agencyId,
          locationId = location.description,
          sequence = 1,
          current = true,
          reviewedBy = "incentives-api",
          reviewTime = LocalDateTime.now(clock),
          reviewType = ReviewType.TRANSFER,
          prisonerNumber = prisonerAtLocation.offenderNo
        )
      )
    }

    @Test
    fun `process TRANSFERRED when prison does not support incentive level at previous prison`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      val prisonerAtLocation = prisonerAtLocation("MDI")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId)).thenReturn(basicStandardEnhanced)
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "STD", LocalDateTime.now()),
        iepDetail("BXI", "ENH2", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary(iepDetails = iepDetails)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then - this prison does not support ENH2 so fallback to ENH
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = "ENH",
          commentText = prisonOffenderEvent.description,
          bookingId = prisonerAtLocation.bookingId,
          prisonId = location.agencyId,
          locationId = location.description,
          sequence = 1,
          current = true,
          reviewedBy = "incentives-api",
          reviewTime = LocalDateTime.now(clock),
          reviewType = ReviewType.TRANSFER,
          prisonerNumber = prisonerAtLocation.offenderNo
        )
      )
    }

    @Test
    fun `process TRANSFERRED when there are no iep details`(): Unit = runBlocking {
      // Given
      val prisonerAtLocation = prisonerAtLocation()
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId)).thenReturn(basicStandardEnhanced)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary(iepDetails = emptyList()))

      // When
      Assert.assertThrows(NoDataFoundException::class.java) {
        runBlocking { prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent) }
      }
    }

    @Test
    fun `do not process reason RETURN_FROM_COURT`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent("RETURN_FROM_COURT"))

      // Then
      verifyNoInteractions(prisonerIepLevelRepository)
    }

    @Test
    fun `do not create IEP level if prisonerNumber is null`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = HMPPSDomainEvent(
        eventType = "prison-offender-events.prisoner.received",
        additionalInformation = AdditionalInformation(
          id = 123,
          reason = "ADMISSION"
        ),
        occurredAt = Instant.now(),
        description = "A prisoner has been received into prison"
      )

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then
      verifyNoInteractions(prisonerIepLevelRepository)
    }
  }

  private val iepTime: LocalDateTime = LocalDateTime.now().minusDays(10)
  private fun iepSummary(iepLevel: String = "Enhanced", iepDetails: List<IepDetail> = emptyList()) = IepSummary(
    bookingId = 1L,
    daysSinceReview = 60,
    iepDate = iepTime.toLocalDate(),
    iepLevel = iepLevel,
    iepTime = iepTime,
    iepDetails = iepDetails,
  )
  private val iepSummaryWithDetail = iepSummary(
    iepDetails = listOf(
      IepDetail(
        bookingId = 1L,
        sequence = 2,
        agencyId = "MDI",
        iepLevel = "Enhanced",
        iepDate = iepTime.toLocalDate(),
        iepTime = iepTime,
        userId = "TEST_USER",
        auditModuleName = "PRISON_API"
      ),
      IepDetail(
        bookingId = 1L,
        sequence = 1,
        agencyId = "LEI",
        iepLevel = "Standard",
        iepDate = iepTime.minusDays(100).toLocalDate(),
        iepTime = iepTime.minusDays(100),
        userId = "TEST_USER",
        auditModuleName = "PRISON_API"
      ),
    )
  )
  private fun iepDetail(agencyId: String, iepLevel: String, iepTime: LocalDateTime) = IepDetail(
    bookingId = 1L,
    sequence = 2,
    agencyId = agencyId,
    iepLevel = iepLevel,
    iepDate = iepTime.toLocalDate(),
    iepTime = iepTime,
    userId = "TEST_USER",
    auditModuleName = "PRISON_API"
  )

  private val previousLevel = PrisonerIepLevel(
    iepCode = "BAS",
    prisonId = "LEI",
    locationId = "LEI-1-1-001",
    bookingId = 1234567,
    sequence = 1,
    current = false,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now().minusDays(2),
    prisonerNumber = "A1234AB"
  )

  private val currentLevel = PrisonerIepLevel(
    iepCode = "STD",
    prisonId = "MDI",
    locationId = "MDI-1-1-004",
    bookingId = 1234567,
    sequence = 2,
    current = true,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now(),
    prisonerNumber = "A1234AB"
  )

  private val currentAndPreviousLevels = flowOf(previousLevel, currentLevel)

  private fun prisonOffenderEvent(reason: String) = HMPPSDomainEvent(
    eventType = "prison-offender-events.prisoner.received",
    additionalInformation = AdditionalInformation(
      id = 123,
      nomsNumber = "A1244AB",
      reason = reason
    ),
    occurredAt = Instant.now(),
    description = "A prisoner has been received into prison"
  )

  private fun prisonerAtLocation(agencyId: String = "MDI") = PrisonerAtLocation(
    1234567, 1, "John", "Smith", "A1234AA", agencyId, 1
  )

  private val location = Location(
    agencyId = "MDI", locationId = 77777L, description = "Houseblock 1"
  )

  private val basicStandardEnhanced = listOf(
    IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, default = false),
    IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
    IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, default = false),
  )
}
