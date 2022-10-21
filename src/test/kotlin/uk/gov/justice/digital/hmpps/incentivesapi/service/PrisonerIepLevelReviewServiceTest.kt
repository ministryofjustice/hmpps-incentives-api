package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.config.ReviewAddedSyncMechanism
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPatchRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel as GlobalIepLevel

class PrisonerIepLevelReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val iepLevelRepository: IepLevelRepository = mock()
  private val iepLevelService: IepLevelService = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private var clock: Clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  private val snsService: SnsService = mock()
  private val auditService: AuditService = mock()
  private val featureFlagsService: FeatureFlagsService = mock()

  private val prisonerIepLevelReviewService = PrisonerIepLevelReviewService(
    prisonApiService,
    prisonerIepLevelRepository,
    iepLevelRepository,
    iepLevelService,
    snsService,
    auditService,
    authenticationFacade,
    clock,
    featureFlagsService,
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc
    // while other tests may override the call to the repo
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())
  }

  @Nested
  inner class AddIepReview {

    private val bookingId = 1234567L
    private val prisonerNumber = "A1234BC"
    private val reviewerUserName = "USER_1_GEN"
    private val reviewTime = LocalDateTime.now(clock)
    private val prisonerInfo = prisonerAtLocation(
      bookingId = bookingId,
      offenderNo = prisonerNumber,
    )
    private val iepReview = IepReview(
      iepLevel = "ENH",
      comment = "A review took place",
      reviewType = ReviewType.REVIEW,
    )
    private val prisonerIepLevel = PrisonerIepLevel(
      iepCode = iepReview.iepLevel,
      commentText = iepReview.comment,
      reviewType = iepReview.reviewType!!,
      prisonId = prisonerInfo.agencyId,
      locationId = location.description,
      current = true,
      reviewedBy = reviewerUserName,
      reviewTime = reviewTime,
      prisonerNumber = prisonerInfo.offenderNo,
      bookingId = prisonerInfo.bookingId,
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId)).thenReturn(location)
      whenever(authenticationFacade.getUsername()).thenReturn(reviewerUserName)
      whenever(prisonerIepLevelRepository.save(any())).thenReturn(prisonerIepLevel.copy(id = 42))
      whenever(iepLevelRepository.findById(iepReview.iepLevel)).thenReturn(
        GlobalIepLevel(iepCode = iepReview.iepLevel, iepDescription = "Enhanced")
      )
    }

    @ParameterizedTest
    @EnumSource(ReviewAddedSyncMechanism::class)
    fun `addIepReview() by prisonerNumber`(reviewAddedSyncMechanism: ReviewAddedSyncMechanism): Unit = runBlocking {
      coroutineScope {
        whenever(featureFlagsService.reviewAddedSyncMechanism()).thenReturn(reviewAddedSyncMechanism)

        // Given
        whenever(prisonApiService.getPrisonerInfo(prisonerNumber)).thenReturn(prisonerInfo)

        // When
        prisonerIepLevelReviewService.addIepReview(prisonerNumber, iepReview)

        testAddIepReviewCommonFunctionality(reviewAddedSyncMechanism)
      }
    }

    @ParameterizedTest
    @EnumSource(ReviewAddedSyncMechanism::class)
    fun `addIepReview() by bookingId`(reviewAddedSyncMechanism: ReviewAddedSyncMechanism): Unit = runBlocking {
      coroutineScope {
        whenever(featureFlagsService.reviewAddedSyncMechanism()).thenReturn(reviewAddedSyncMechanism)

        // Given
        whenever(prisonApiService.getPrisonerInfo(bookingId)).thenReturn(prisonerInfo)

        // When
        prisonerIepLevelReviewService.addIepReview(bookingId, iepReview)

        testAddIepReviewCommonFunctionality(reviewAddedSyncMechanism)
      }
    }

    private suspend fun testAddIepReviewCommonFunctionality(reviewAddedSyncMechanism: ReviewAddedSyncMechanism) {
      // IEP review is saved
      verify(prisonerIepLevelRepository, times(1)).save(prisonerIepLevel)

      if (reviewAddedSyncMechanism == ReviewAddedSyncMechanism.DOMAIN_EVENT) {
        // A domain even is published
        verify(snsService, times(1)).sendIepReviewEvent(
          42,
          prisonerNumber,
          reviewTime,
          IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        )

        // Prison API request not made
        verify(prisonApiService, times(0)).addIepReview(any(), any())
      } else {
        // NOMIS is updated my making a request to Prison API
        verify(prisonApiService, times(1)).addIepReview(
          bookingId,
          IepReviewInNomis(
            iepLevel = iepReview.iepLevel,
            comment = iepReview.comment,
            reviewTime = reviewTime,
            reviewerUserName = reviewerUserName,
          ),
        )

        // Domain event not published
        verify(snsService, times(0)).sendIepReviewEvent(any(), any(), any(), any())
      }

      // An audit event is published
      verify(auditService, times(1)).sendMessage(
        AuditType.IEP_REVIEW_ADDED,
        "42",
        iepDetailFromIepLevel(
          prisonerIepLevel,
          iepCode = "ENH",
          iepDescription = "Enhanced",
          id = 42,
        ),
        reviewerUserName,
      )
    }

    @Test
    fun `update multiple IepLevels with current flag`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonApiService.getPrisonerInfo(bookingId)).thenReturn(prisonerInfo)
        val anotherIepLevelCurrentIsTrue = currentLevel.copy(id = 2L)
        whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId)))
          .thenReturn(flowOf(currentLevel, anotherIepLevelCurrentIsTrue))

        // When
        prisonerIepLevelReviewService.addIepReview(bookingId, iepReview)

        // Then
        verify(prisonerIepLevelRepository, times(1))
          .save(currentLevel.copy(current = false))

        verify(prisonerIepLevelRepository, times(1))
          .save(anotherIepLevelCurrentIsTrue.copy(current = false))
      }
    }
  }

  @Nested
  inner class GetPrisonerIepLevelHistory {
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
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(1234567)).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderByReviewTimeDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isEqualTo(2)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false and will not return iep details if withDetails is false`(): Unit = runBlocking {
      coroutineScope {
        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(1234567)).thenReturn(currentAndPreviousLevels)

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(1234567, useNomisData = false, withDetails = false)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderByReviewTimeDesc(1234567)
        verifyNoInteractions(prisonApiService)
        assertThat(result.iepDetails.size).isZero()
      }
    }
  }

  @Nested
  inner class ProcessReceivedPrisoner {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // This ensures save works and an id is set on the PrisonerIepLevel
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }
      whenever(iepLevelRepository.findById("STD")).thenReturn(GlobalIepLevel(iepCode = "STD", iepDescription = "Standard"))
      whenever(iepLevelRepository.findById("ENH")).thenReturn(GlobalIepLevel(iepCode = "ENH", iepDescription = "Enhanced"))
    }

    @Test
    fun `process ADMISSION`(): Unit = runBlocking {
      // Given - default for that prison is Enhanced
      val prisonOffenderEvent = prisonOffenderEvent("ADMISSION")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation())
      whenever(prisonApiService.getLocationById(prisonerAtLocation().assignedLivingUnitId, true)).thenReturn(location)
      // Enhanced is the default for this prison so use that
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation().agencyId, useClientCredentials = true)).thenReturn(
        listOf(
          IepLevel(
            iepLevel = "STD",
            iepDescription = "Standard",
            sequence = 1,
            default = false,
          ),
          IepLevel(
            iepLevel = "ENH",
            iepDescription = "Enhanced",
            sequence = 1,
            default = true,
          ),
        )
      )

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "ENH",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation().bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.INITIAL,
        prisonerNumber = prisonerAtLocation().offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, prisonerAtLocation().offenderNo, expectedPrisonerIepLevel.reviewTime, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Enhanced", "ENH"),
          expectedPrisonerIepLevel.reviewedBy,
        )
    }

    @Test
    fun `process TRANSFERRED when offender stays on same incentive level as previous prison`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      val prisonerAtLocation = prisonerAtLocation(agencyId = "MDI")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId, useClientCredentials = true)).thenReturn(basicStandardEnhanced)
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "Basic", "BAS", LocalDateTime.now()),
        iepDetail("BXI", "Standard", "STD", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "Basic", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary(iepDetails = iepDetails)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then - the prisoner was on STD at BXI so they on this level
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "STD",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation.bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerAtLocation.offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, prisonerAtLocation().offenderNo, expectedPrisonerIepLevel.reviewTime, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Standard", "STD"),
          expectedPrisonerIepLevel.reviewedBy,
        )
    }

    @Test
    fun `process TRANSFERRED when prison does not support incentive level at previous prison`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      val prisonerAtLocation = prisonerAtLocation(agencyId = "MDI")
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId, useClientCredentials = true)).thenReturn(basicStandardEnhanced)
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "Standard", "STD", LocalDateTime.now()),
        iepDetail("BXI", "Enhanced 2", "ENH2", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "Basic", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary(iepDetails = iepDetails)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then - MDI prison does not support ENH2 (which they were on in BXI) so fallback to ENH
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "ENH",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation.bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerAtLocation.offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, prisonerAtLocation().offenderNo, expectedPrisonerIepLevel.reviewTime, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Enhanced", "ENH"),
          expectedPrisonerIepLevel.reviewedBy,
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
        runBlocking { prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent) }
      }
    }

    @Test
    fun `do not process reason RETURN_FROM_COURT`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent("RETURN_FROM_COURT"))

      // Then
      verifyNoInteractions(prisonerIepLevelRepository)
    }

    @Test
    fun `process MERGE event`(): Unit = runBlocking {
      // Given - default for that prison is Enhanced
      val prisonMergeEvent = prisonMergeEvent()
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation(bookingId = 1234567, offenderNo = "A1244AB"))
      whenever(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
        .thenReturn(
          flowOf(
            PrisonerIepLevel(
              prisonerNumber = "A8765SS",
              bookingId = 1234567L,
              prisonId = "LEI",
              locationId = "LEI-1-1-001",
              reviewedBy = "TEST_STAFF1",
              iepCode = "BAS",
              current = true,
              reviewTime = LocalDateTime.now().minusDays(2)
            )
          )
        )
      whenever(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
        .thenReturn(
          flowOf(
            PrisonerIepLevel(
              prisonerNumber = "A1244AB",
              bookingId = 555555L,
              prisonId = "LEI",
              locationId = "LEI-1-1-001",
              reviewedBy = "TEST_STAFF1",
              iepCode = "BAS",
              current = false,
              reviewTime = LocalDateTime.now().minusDays(200),
            ),
            PrisonerIepLevel(
              prisonerNumber = "A1244AB",
              bookingId = 555555L,
              prisonId = "LEI",
              locationId = "LEI-1-1-001",
              reviewedBy = "TEST_STAFF1",
              iepCode = "STD",
              current = true,
              reviewTime = LocalDateTime.now().minusDays(100)
            )
          )
        )
      prisonerIepLevelReviewService.mergedPrisonerDetails(prisonMergeEvent)

      verify(auditService, times(1))
        .sendMessage(
          AuditType.PRISONER_NUMBER_MERGE,
          "A1244AB",
          "3 incentive records updated from merge A8765SS -> A1244AB. Updated to booking ID 1234567",
          "INCENTIVES_API"
        )
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
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then
      verifyNoInteractions(prisonerIepLevelRepository)
    }
  }

  @Nested
  inner class AddIepMigration {

    private val migrationRequest = syncPostRequest(iepLevelCode = "STD", reviewType = ReviewType.MIGRATED)

    @Test
    fun `process migration`(): Unit = runBlocking {
      // Given
      val bookingId = 1234567L
      whenever(prisonApiService.getPrisonerInfo(bookingId, true)).thenReturn(prisonerAtLocation())
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }

      // When
      prisonerIepLevelReviewService.persistSyncPostRequest(bookingId, migrationRequest, false)

      // Then
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = migrationRequest.iepLevel,
          commentText = migrationRequest.comment,
          bookingId = bookingId,
          prisonId = migrationRequest.prisonId,
          current = migrationRequest.current,
          reviewedBy = migrationRequest.userId,
          reviewTime = migrationRequest.iepTime,
          reviewType = migrationRequest.reviewType,
          prisonerNumber = prisonerAtLocation().offenderNo
        )
      )
    }

    @Test
    fun `process migration when userId is not provided`(): Unit = runBlocking {
      // Given
      val migrationRequestWithNullUserId = migrationRequest.copy(userId = null)
      val bookingId = 1234567L
      whenever(prisonApiService.getPrisonerInfo(bookingId, true)).thenReturn(prisonerAtLocation())
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }

      // When
      prisonerIepLevelReviewService.persistSyncPostRequest(bookingId, migrationRequestWithNullUserId, false)

      // Then
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = migrationRequestWithNullUserId.iepLevel,
          commentText = migrationRequestWithNullUserId.comment,
          bookingId = bookingId,
          prisonId = migrationRequestWithNullUserId.prisonId,
          current = migrationRequestWithNullUserId.current,
          reviewedBy = null,
          reviewTime = migrationRequestWithNullUserId.iepTime,
          reviewType = migrationRequestWithNullUserId.reviewType,
          prisonerNumber = prisonerAtLocation().offenderNo
        )
      )
    }
  }

  @Nested
  inner class HandleSyncDeleteIepReviewRequest {

    private val id = 42L
    private val bookingId = 123456L
    private val offenderNo = "A1234AA"
    private val iepLevelCode = "ENH"
    private val iepLevelDescription = "Enhanced"
    private val reviewTime = LocalDateTime.now().minusDays(10)

    private val iepReview = PrisonerIepLevel(
      id = id,
      iepCode = iepLevelCode,
      commentText = "A review took place",
      bookingId = bookingId,
      prisonId = "MDI",
      locationId = "1-2-003",
      current = true,
      reviewedBy = "USER_1_GEN",
      reviewTime = reviewTime,
      reviewType = ReviewType.REVIEW,
      prisonerNumber = offenderNo,
    )

    private val iepDetail = IepDetail(
      id = id,
      iepLevel = iepLevelDescription,
      iepCode = iepLevelCode,
      comments = iepReview.commentText,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      iepDate = reviewTime.toLocalDate(),
      iepTime = reviewTime,
      agencyId = "MDI",
      locationId = "1-2-003",
      userId = "USER_1_GEN",
      reviewType = ReviewType.REVIEW,
      auditModuleName = "INCENTIVES_API",
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // Mock IEP level query
      whenever(iepLevelRepository.findById(iepLevelCode)).thenReturn(
        GlobalIepLevel(iepCode = iepLevelCode, iepDescription = iepLevelDescription, sequence = 3, active = true),
      )

      // Mock find query
      whenever(prisonerIepLevelRepository.findById(id)).thenReturn(iepReview)

      // Mock PrisonerIepLevel being updated
      whenever(prisonerIepLevelRepository.delete(iepReview)).thenReturn(Unit)
    }

    @Test
    fun `deletes the IEP review`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.handleSyncDeleteIepReviewRequest(bookingId, iepReview.id)

      // Then check it's saved
      verify(prisonerIepLevelRepository, times(1))
        .delete(iepReview)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync DELETE request is handled
      prisonerIepLevelReviewService.handleSyncDeleteIepReviewRequest(bookingId, iepReview.id)

      // SNS event is sent
      verify(snsService, times(1)).sendIepReviewEvent(id, prisonerAtLocation().offenderNo, iepReview.reviewTime, IncentivesDomainEventType.IEP_REVIEW_DELETED)

      // audit message is sent
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_DELETED,
          "$id",
          iepDetail,
          iepReview.reviewedBy,
        )
    }

    @Test
    fun `If deleted IEP review had current = true, set the latest IEP review current flag to true`(): Unit = runBlocking {
      // Prisoner had few IEP reviews
      val currentIepReview = iepReview.copy(current = true)
      val olderIepReview = iepReview.copy(id = currentIepReview.id - 1, current = false)

      // Mock find query
      whenever(prisonerIepLevelRepository.findById(id)).thenReturn(currentIepReview)

      // Mock find of latest IEP review
      whenever(prisonerIepLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId))
        .thenReturn(olderIepReview)

      // When
      prisonerIepLevelReviewService.handleSyncDeleteIepReviewRequest(bookingId, currentIepReview.id)

      // Then desired IEP review is deletes as usual
      verify(prisonerIepLevelRepository, times(1))
        .delete(currentIepReview)

      // and the now latest IEP review becomes the active one
      verify(prisonerIepLevelRepository, times(1))
        .save(olderIepReview.copy(current = true))
    }
  }

  @Nested
  inner class HandleSyncPatchIepReviewRequest {

    private val id = 42L
    private val bookingId = 123456L
    private val offenderNo = "A1234AA"
    private val iepLevelCode = "ENH"
    private val iepLevelDescription = "Enhanced"
    private val reviewTime = LocalDateTime.now().minusDays(10)
    private val syncPatchRequest: SyncPatchRequest = SyncPatchRequest(
      comment = "UPDATED",
      iepTime = null,
      current = null,
    )

    private val iepReview = PrisonerIepLevel(
      id = id,
      iepCode = "ENH",
      commentText = "Existing comment, before patch",
      bookingId = bookingId,
      prisonId = "MDI",
      locationId = "1-2-003",
      current = true,
      reviewedBy = "USER_1_GEN",
      reviewTime = reviewTime,
      reviewType = ReviewType.REVIEW,
      prisonerNumber = offenderNo,
    )
    private val expectedIepReview = iepReview.copy(commentText = syncPatchRequest.comment)

    private val iepDetail = IepDetail(
      id = id,
      iepLevel = iepLevelDescription,
      iepCode = iepLevelCode,
      comments = syncPatchRequest.comment,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      iepDate = reviewTime.toLocalDate(),
      iepTime = reviewTime,
      agencyId = "MDI",
      locationId = "1-2-003",
      userId = "USER_1_GEN",
      reviewType = ReviewType.REVIEW,
      auditModuleName = "INCENTIVES_API",
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // Mock IEP level query
      whenever(iepLevelRepository.findById("ENH")).thenReturn(
        GlobalIepLevel(iepCode = iepLevelCode, iepDescription = iepLevelDescription, sequence = 3, active = true),
      )

      // Mock find query
      whenever(prisonerIepLevelRepository.findById(id)).thenReturn(iepReview)

      // Mock PrisonerIepLevel being updated
      whenever(prisonerIepLevelRepository.save(expectedIepReview))
        .thenReturn(expectedIepReview)
    }

    @Test
    fun `updates the IEP review`(): Unit = runBlocking {
      // When
      val result = prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, iepReview.id, syncPatchRequest)

      // Then check it's saved
      verify(prisonerIepLevelRepository, times(1))
        .save(expectedIepReview)

      // Then check it's returned
      assertThat(result).isEqualTo(iepDetail)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync POST request is handled
      prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, iepReview.id, syncPatchRequest)

      // SNS event is sent
      verify(snsService, times(1)).sendIepReviewEvent(id, prisonerAtLocation().offenderNo, iepReview.reviewTime, IncentivesDomainEventType.IEP_REVIEW_UPDATED)

      // audit message is sent
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_UPDATED,
          "$id",
          iepDetail,
          iepReview.reviewedBy,
        )
    }

    @Test
    fun `If request has current true we update the previous IEP Level with current of true`(): Unit = runBlocking {
      // Given
      val iepReviewUpdatedWithSyncPatch = iepReview.copy(current = true)
      whenever(prisonerIepLevelRepository.save(iepReviewUpdatedWithSyncPatch))
        .thenReturn(iepReviewUpdatedWithSyncPatch)

      whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId)))
        .thenReturn(flowOf(currentLevel))

      // When
      prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(
        bookingId, iepReview.id,
        SyncPatchRequest(
          comment = null,
          iepTime = null,
          current = true,
        )
      )

      // Then currentLevel record is updated, along with iepReviewUpdatedWithSyncPatch
      verify(prisonerIepLevelRepository, times(1))
        .save(currentLevel.copy(current = false))

      verify(prisonerIepLevelRepository, times(1))
        .save(iepReviewUpdatedWithSyncPatch)
    }
  }

  @Nested
  inner class HandleSyncPostIepReviewRequest {

    private val bookingId = 123456L
    private val offenderNo = "A1234AA"
    private val iepLevelCode = "ENH"
    private val iepLevelDescription = "Enhanced"
    private val syncPostRequest = syncPostRequest(iepLevelCode, reviewType = ReviewType.REVIEW)

    private val iepReviewId = 42L
    private val iepReview = PrisonerIepLevel(
      iepCode = syncPostRequest.iepLevel,
      commentText = syncPostRequest.comment,
      bookingId = bookingId,
      prisonId = syncPostRequest.prisonId,
      locationId = location.description,
      current = syncPostRequest.current,
      reviewedBy = syncPostRequest.userId,
      reviewTime = syncPostRequest.iepTime,
      reviewType = syncPostRequest.reviewType,
      prisonerNumber = offenderNo,
    )

    private val iepDetail = IepDetail(
      id = iepReviewId,
      iepLevel = iepLevelDescription,
      iepCode = iepLevelCode,
      comments = syncPostRequest.comment,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      iepDate = syncPostRequest.iepTime.toLocalDate(),
      iepTime = syncPostRequest.iepTime,
      agencyId = syncPostRequest.prisonId,
      locationId = location.description,
      userId = syncPostRequest.userId,
      reviewType = syncPostRequest.reviewType,
      auditModuleName = "INCENTIVES_API",
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // Mock Prison API getPrisonerInfo() response
      val prisonerAtLocation = prisonerAtLocation(bookingId, offenderNo)
      whenever(prisonApiService.getPrisonerInfo(bookingId, true))
        .thenReturn(prisonerAtLocation)

      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true))
        .thenReturn(location)

      // Mock IEP level query
      whenever(iepLevelRepository.findById("ENH")).thenReturn(
        GlobalIepLevel(iepCode = iepLevelCode, iepDescription = iepLevelDescription, sequence = 3, active = true),
      )

      // Mock save() of PrisonerIepLevel record
      whenever(prisonerIepLevelRepository.save(iepReview))
        .thenReturn(iepReview.copy(id = iepReviewId))
    }

    @Test
    fun `persists a new IEP review`(): Unit = runBlocking {
      // When
      val result = prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest)

      // Then check it's saved
      verify(prisonerIepLevelRepository, times(1))
        .save(iepReview)

      // Then check it's returned
      assertThat(result).isEqualTo(iepDetail)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync POST request is handled
      prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest)

      // SNS event is sent
      verify(snsService, times(1)).sendIepReviewEvent(iepReviewId, prisonerAtLocation().offenderNo, syncPostRequest.iepTime, IncentivesDomainEventType.IEP_REVIEW_INSERTED)

      // audit message is sent
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "$iepReviewId",
          iepDetail,
          syncPostRequest.userId,
        )
    }

    @Test
    fun `If request has current true we update the previous IEP Level with current of true`(): Unit = runBlocking {
      // Given
      whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId)))
        .thenReturn(flowOf(currentLevel))

      // When
      prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest.copy(current = true))

      // Then currentLevel record is updated, along with iepReview
      verify(prisonerIepLevelRepository, times(1))
        .save(currentLevel.copy(current = false))

      verify(prisonerIepLevelRepository, times(1))
        .save(iepReview)
    }
  }

  private val iepTime: LocalDateTime = LocalDateTime.now().minusDays(10)
  private fun iepSummary(iepLevel: String = "Enhanced", iepDetails: List<IepDetail> = emptyList()) = IepSummary(
    bookingId = 1L,
    iepDate = iepTime.toLocalDate(),
    iepLevel = iepLevel,
    iepTime = iepTime,
    iepDetails = iepDetails,
  )
  private val iepSummaryWithDetail = iepSummary(
    iepDetails = listOf(
      IepDetail(
        bookingId = 1L,
        agencyId = "MDI",
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepDate = iepTime.toLocalDate(),
        iepTime = iepTime,
        userId = "TEST_USER",
        auditModuleName = "PRISON_API",
      ),
      IepDetail(
        bookingId = 1L,
        agencyId = "LEI",
        iepLevel = "Standard",
        iepCode = "STD",
        iepDate = iepTime.minusDays(100).toLocalDate(),
        iepTime = iepTime.minusDays(100),
        userId = "TEST_USER",
        auditModuleName = "PRISON_API",
      ),
    )
  )
  private fun iepDetail(agencyId: String, iepLevel: String, iepCode: String, iepTime: LocalDateTime) = IepDetail(
    bookingId = 1L,
    agencyId = agencyId,
    iepLevel = iepLevel,
    iepCode = iepCode,
    iepDate = iepTime.toLocalDate(),
    iepTime = iepTime,
    userId = "TEST_USER",
    auditModuleName = "PRISON_API",
  )

  private val previousLevel = PrisonerIepLevel(
    iepCode = "BAS",
    prisonId = "LEI",
    locationId = "LEI-1-1-001",
    bookingId = 1234567,
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

  private fun prisonMergeEvent() = HMPPSDomainEvent(
    eventType = "prison-offender-events.prisoner.merged",
    additionalInformation = AdditionalInformation(
      nomsNumber = "A1244AB",
      reason = "MERGED",
      removedNomsNumber = "A8765SS",
    ),
    occurredAt = Instant.now(),
    description = "A prisoner has been merged from A8765SS to A1244AB"
  )

  private fun prisonerAtLocation(bookingId: Long = 1234567, offenderNo: String = "A1234AA", agencyId: String = "MDI") = PrisonerAtLocation(
    bookingId, 1, "John", "Smith", offenderNo, agencyId, 1
  )

  private val location = Location(
    agencyId = "MDI", locationId = 77777L, description = "Houseblock 1"
  )

  private val basicStandardEnhanced = listOf(
    IepLevel(
      iepLevel = "BAS",
      iepDescription = "Basic",
      sequence = 1,
      default = false,
    ),
    IepLevel(
      iepLevel = "STD",
      iepDescription = "Standard",
      sequence = 2,
      default = true,
    ),
    IepLevel(
      iepLevel = "ENH",
      iepDescription = "Enhanced",
      sequence = 3,
      default = false,
    ),
  )

  private fun syncPostRequest(iepLevelCode: String = "STD", reviewType: ReviewType) = SyncPostRequest(
    iepTime = LocalDateTime.now(),
    prisonId = "MDI",
    iepLevel = iepLevelCode,
    comment = "A comment",
    userId = "XYZ_GEN",
    reviewType = reviewType,
    current = true,
  )

  private fun iepDetailFromIepLevel(prisonerIepLevel: PrisonerIepLevel, iepDescription: String, iepCode: String, id: Long = 0) =
    IepDetail(
      id = id,
      iepLevel = iepDescription,
      iepCode = iepCode,
      comments = prisonerIepLevel.commentText,
      bookingId = prisonerIepLevel.bookingId,
      agencyId = prisonerIepLevel.prisonId,
      locationId = prisonerIepLevel.locationId,
      userId = prisonerIepLevel.reviewedBy,
      iepDate = prisonerIepLevel.reviewTime.toLocalDate(),
      iepTime = prisonerIepLevel.reviewTime,
      reviewType = prisonerIepLevel.reviewType,
      prisonerNumber = prisonerIepLevel.prisonerNumber,
      auditModuleName = "INCENTIVES_API",
    )
}
