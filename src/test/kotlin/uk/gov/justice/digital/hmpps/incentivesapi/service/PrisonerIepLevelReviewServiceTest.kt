package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.config.ReviewAddedSyncMechanism
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPatchRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepReviewInNomis
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrisonerIepLevelReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val iepLevelService = IepLevelService(prisonApiService)
  private val authenticationFacade: AuthenticationFacade = mock()
  private var clock: Clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  private val snsService: SnsService = mock()
  private val auditService: AuditService = mock()
  private val featureFlagsService: FeatureFlagsService = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService = mock()
  private val offenderSearchService: OffenderSearchService = mock()

  private val prisonerIepLevelReviewService = PrisonerIepLevelReviewService(
    prisonApiService,
    prisonerIepLevelRepository,
    iepLevelService,
    snsService,
    auditService,
    authenticationFacade,
    clock,
    featureFlagsService,
    nextReviewDateGetterService,
    nextReviewDateUpdaterService,
    offenderSearchService,
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc
    // while other tests may override the call to the repo
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
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
      whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
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
        verify(snsService, times(1)).publishDomainEvent(
          eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
          description = "An IEP review has been added",
          occurredAt = reviewTime,
          additionalInformation = AdditionalInformation(
            id = 42,
            nomsNumber = prisonerNumber,
          ),
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
        verify(snsService, times(0)).publishDomainEvent(any(), any(), any(), any())
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
        val bookingId = 1234567L
        val offenderNo = "A1234AB"

        // Given
        whenever(
          prisonApiService.getIEPSummaryForPrisoner(
            bookingId,
            withDetails = true
          )
        ).thenReturn(iepSummaryWithDetail(bookingId))
        // Mock requests to get ACCT status
        whenever(
          prisonApiService.getPrisonerExtraInfo(bookingId, useClientCredentials = true)
        ).thenReturn(
          prisonerExtraInfo(offenderNo, bookingId)
        )

        // When
        prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId)

        // Then
        verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(bookingId, true)
        verifyNoInteractions(prisonerIepLevelRepository)
      }
    }

    @Test
    fun `will call prison api if useNomisData is true and will not return iep details if withDetails is false`(): Unit =
      runBlocking {
        coroutineScope {
          val bookingId = 1234567L
          val prisonerNumber = "A1234AA"

          // Given
          whenever(
            prisonApiService.getIEPSummaryForPrisoner(bookingId, withDetails = true)
          ).thenReturn(
            iepSummaryWithDetail(bookingId)
          )
          // Mock requests to get ACCT status
          whenever(
            prisonApiService.getPrisonerExtraInfo(bookingId, useClientCredentials = true)
          ).thenReturn(
            prisonerExtraInfo(prisonerNumber, bookingId)
          )

          // When
          prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId, withDetails = false)

          // Then
          verify(prisonApiService, times(1)).getIEPSummaryForPrisoner(bookingId, true)
          verifyNoInteractions(prisonerIepLevelRepository)
        }
      }

    @Test
    fun `will query incentives db if useNomisData is false`(): Unit = runBlocking {
      coroutineScope {
        val bookingId = currentLevel.bookingId
        val expectedNextReviewDate = currentLevel.reviewTime.plusYears(1).toLocalDate()

        whenever(featureFlagsService.isIncentivesDataSourceOfTruth()).thenReturn(true)
        whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
        whenever(nextReviewDateGetterService.get(bookingId)).thenReturn(expectedNextReviewDate)

        // Given
        whenever(prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)).thenReturn(
          currentAndPreviousLevels
        )

        // When
        val result = prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId)

        // Then
        verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderByReviewTimeDesc(bookingId)
        assertThat(result.iepDetails.size).isEqualTo(2)
        assertThat(result.nextReviewDate).isEqualTo(expectedNextReviewDate)
      }
    }

    @Test
    fun `will query incentives db if useNomisData is false and will not return iep details if withDetails is false`(): Unit =
      runBlocking {
        coroutineScope {
          val bookingId = currentLevel.bookingId
          val expectedNextReviewDate = currentAndPreviousLevels.first().reviewTime.plusYears(1).toLocalDate()

          whenever(featureFlagsService.isIncentivesDataSourceOfTruth()).thenReturn(true)
          whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
          whenever(nextReviewDateGetterService.get(bookingId)).thenReturn(expectedNextReviewDate)

          // Given
          whenever(prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)).thenReturn(
            currentAndPreviousLevels
          )

          // When
          val result =
            prisonerIepLevelReviewService.getPrisonerIepLevelHistory(bookingId, withDetails = false)

          // Then
          verify(prisonerIepLevelRepository, times(1)).findAllByBookingIdOrderByReviewTimeDesc(bookingId)
          assertThat(result.iepDetails.size).isZero
          assertThat(result.nextReviewDate).isEqualTo(expectedNextReviewDate)
        }
      }
  }

  @Nested
  inner class ProcessReceivedPrisoner {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // This ensures save works and an id is set on the PrisonerIepLevel
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }
      whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
    }

    @ParameterizedTest
    @ValueSource(strings = ["NEW_ADMISSION", "READMISSION"])
    fun `process admissions`(reason: String): Unit = runBlocking {
      val expectedReviewType = when (reason) {
        "NEW_ADMISSION" -> ReviewType.INITIAL
        "READMISSION" -> ReviewType.READMISSION
        else -> throw IllegalArgumentException("Unexpected reason for this test: $reason")
      }

      // Given - default for that prison is Enhanced
      val prisonOffenderEvent = prisonOffenderEvent(reason)
      val prisonerAtLocation = prisonerAtLocation()
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      // Enhanced is the default for this prison so use that
      whenever(prisonApiService.getIepLevelsForPrison("MDI", true)).thenReturn(
        flowOf(
          IepLevel(
            iepLevel = "STD",
            iepDescription = "Standard",
            sequence = 1,
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
        commentText = "Default level assigned on arrival",
        bookingId = prisonerAtLocation().bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = expectedReviewType,
        prisonerNumber = prisonerAtLocation().offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(nextReviewDateUpdaterService, times(1)).update(prisonerAtLocation.bookingId)

      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = expectedPrisonerIepLevel.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 0,
          nomsNumber = prisonerAtLocation().offenderNo,
        ),
      )
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
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED", prisonerNumber)
      val prisonerAtLocation = prisonerAtLocation(bookingId, prisonerNumber, agencyId = "MDI")
      whenever(prisonApiService.getPrisonerInfo(prisonerNumber, true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(prisonApiService.getIepLevelsForPrison(prisonerAtLocation.agencyId, true)).thenReturn(basicStandardEnhanced)
      whenever(prisonApiService.getIepLevels()).thenReturn(globalIncentiveLevels.asFlow())
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "Standard", "STD", LocalDateTime.now()),
        iepDetail("BXI", "Basic", "BAS", LocalDateTime.now().minusDays(1)),
      )
      val iepSummary = iepSummary("Basic", bookingId, iepDetails)
      whenever(
        prisonApiService.getIEPSummaryForPrisoner(
          bookingId,
          withDetails = true,
          useClientCredentials = true
        )
      ).thenReturn(iepSummary)
      // Mock request to get ACCT status
      whenever(
        prisonApiService.getPrisonerExtraInfo(bookingId, true)
      ).thenReturn(
        prisonerExtraInfo(prisonerNumber, bookingId)
      )

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then - the prisoner was on BAS at BXI so they should remain on this level
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "BAS",
        commentText = "Level transferred from previous establishment",
        bookingId = bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerNumber,
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = expectedPrisonerIepLevel.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 0,
          nomsNumber = prisonerNumber,
        ),
      )
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Basic", "BAS"),
          expectedPrisonerIepLevel.reviewedBy,
        )
    }

    @Test
    fun `process TRANSFERRED when prison does not support incentive level at previous prison`(): Unit = runBlocking {
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      val prisonerAtLocation = prisonerAtLocation(bookingId, prisonerNumber, agencyId = "MDI")
      whenever(prisonApiService.getPrisonerInfo(prisonerNumber, true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(prisonApiService.getIepLevelsForPrison(prisonerAtLocation.agencyId, true)).thenReturn(basicStandardEnhanced)
      whenever(prisonApiService.getIepLevels()).thenReturn(globalIncentiveLevels.asFlow())
      // Mock request to get ACCT status
      whenever(
        prisonApiService.getPrisonerExtraInfo(bookingId, true)
      ).thenReturn(
        prisonerExtraInfo(prisonerNumber, bookingId)
      )
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "Standard", "STD", LocalDateTime.now()),
        iepDetail("BXI", "Enhanced 2", "ENH2", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "Basic", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary("Standard", bookingId, iepDetails = iepDetails)
      whenever(
        prisonApiService.getIEPSummaryForPrisoner(
          bookingId,
          withDetails = true,
          useClientCredentials = true
        )
      ).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then - MDI prison does not support ENH2 (which they were on in BXI) so fallback to STD
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "STD",
        commentText = "Level transferred from previous establishment",
        bookingId = bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerNumber,
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = expectedPrisonerIepLevel.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 0,
          nomsNumber = prisonerNumber,
        )
      )
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Standard", "STD"),
          expectedPrisonerIepLevel.reviewedBy,
        )
    }

    @Test
    fun `process TRANSFERRED when there are no iep details`(): Unit = runBlocking {
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonerAtLocation = prisonerAtLocation(bookingId, prisonerNumber)
      val prisonOffenderEvent = prisonOffenderEvent("TRANSFERRED")
      whenever(prisonApiService.getPrisonerInfo(prisonerNumber, true)).thenReturn(prisonerAtLocation)
      whenever(prisonApiService.getLocationById(prisonerAtLocation.assignedLivingUnitId, true)).thenReturn(location)
      whenever(prisonApiService.getIepLevelsForPrison(prisonerAtLocation.agencyId, true)).thenReturn(basicStandardEnhanced)
      whenever(prisonApiService.getIepLevels()).thenReturn(globalIncentiveLevels.asFlow())
      whenever(
        prisonApiService.getIEPSummaryForPrisoner(
          bookingId,
          withDetails = true,
          useClientCredentials = true
        )
      ).thenReturn(iepSummaryWithDetail(bookingId))
      // Mock requests to get ACCT status
      whenever(
        prisonApiService.getPrisonerExtraInfo(bookingId, true)
      ).thenReturn(
        prisonerExtraInfo(prisonerNumber, bookingId),
      )

      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then - MDI prison does not support ENH2 (which they were on in BXI) so fallback to ENH
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "STD",
        commentText = "Level transferred from previous establishment",
        bookingId = bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerNumber,
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = expectedPrisonerIepLevel.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 0,
          nomsNumber = prisonerNumber,
        ),
      )
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Standard", "STD"),
          expectedPrisonerIepLevel.reviewedBy,
        )
    }

    @Test
    fun `process 'prisoner alerts updated' event when ACCT alert added`(): Unit = runBlocking {
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonerAlertsUpdatedEvent = prisonerAlertsUpdatedEvent(
        prisonerNumber,
        bookingId,
        alertsAdded = listOf(PrisonerAlert.ACCT_ALERT_CODE),
        alertsRemoved = emptyList(),
      )

      // When
      prisonerIepLevelReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `process 'prisoner alerts updated' event when ACCT alert removed`(): Unit = runBlocking {
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonerAlertsUpdatedEvent = prisonerAlertsUpdatedEvent(
        prisonerNumber,
        bookingId,
        alertsAdded = emptyList(),
        alertsRemoved = listOf(PrisonerAlert.ACCT_ALERT_CODE),
      )

      // When
      prisonerIepLevelReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `process 'prisoner alerts updated' event when alerts didn't change`(): Unit = runBlocking {
      val bookingId = 1234567L
      val prisonerNumber = "A1244AB"

      // Given
      val prisonerUpdatedEvent = prisonerAlertsUpdatedEvent(
        prisonerNumber,
        bookingId,
        alertsAdded = listOf("ABC"),
        alertsRemoved = listOf("XYZ"),
      )

      // When
      prisonerIepLevelReviewService.processPrisonerAlertsUpdatedEvent(prisonerUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(0))
        .update(bookingId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["RETURN_FROM_COURT", "TEMPORARY_ABSENCE_RETURN"])
    fun `do not process irrelevant reasons`(reason: String): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.processOffenderEvent(prisonOffenderEvent(reason))

      // Then
      verifyNoInteractions(prisonerIepLevelRepository)
    }

    @Test
    fun `process MERGE event`(): Unit = runBlocking {
      // Given - default for that prison is Enhanced
      val prisonMergeEvent = prisonMergeEvent()
      val prisonerAtLocation = prisonerAtLocation(
        bookingId = 1234567,
        offenderNo = "A1244AB"
      )
      whenever(prisonApiService.getPrisonerInfo("A1244AB", true))
        .thenReturn(prisonerAtLocation)

      val newReview = PrisonerIepLevel(
        id = 1L,
        prisonerNumber = "A8765SS",
        bookingId = 1234567L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(2)
      )
      val oldReview1 = PrisonerIepLevel(
        id = 3L,
        prisonerNumber = "A1244AB",
        bookingId = 555555L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(100)
      )

      val oldReview2 = PrisonerIepLevel(
        id = 2L,
        prisonerNumber = "A1244AB",
        bookingId = 555555L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        iepCode = "BAS",
        current = false,
        reviewTime = LocalDateTime.now().minusDays(200),
      )

      whenever(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
        .thenReturn(
          flowOf(
            newReview
          )
        )

      whenever(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
        .thenReturn(
          flowOf(
            oldReview2,
            oldReview1
          )
        )
      prisonerIepLevelReviewService.mergedPrisonerDetails(prisonMergeEvent)

      verify(prisonerIepLevelRepository).save(eq(newReview.copy(prisonerNumber = "A1244AB")))
      verify(prisonerIepLevelRepository).save(eq(oldReview2.copy(bookingId = 1234567L, id = 0L, current = false)))
      verify(prisonerIepLevelRepository).save(eq(oldReview1.copy(bookingId = 1234567L, id = 0L, current = false)))

      // check next review date is updated for new bookingId
      verify(nextReviewDateUpdaterService, times(1))
        .update(prisonerAtLocation.bookingId)

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
        eventType = "prisoner-offender-search.prisoner.received",
        additionalInformation = AdditionalInformation(
          id = 123,
          reason = "NEW_ADMISSION"
        ),
        occurredAt = Instant.now(),
        description = "A prisoner has been received into a prison with reason: admission on new charges"
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

      // Triggers the update of next review date when a new review is created
      verify(nextReviewDateUpdaterService, times(0)).update(bookingId)
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

      // Mock find query
      whenever(prisonerIepLevelRepository.findById(id)).thenReturn(iepReview)

      // Mock PrisonerIepLevel being updated
      whenever(prisonerIepLevelRepository.delete(iepReview)).thenReturn(Unit)

      whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
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
    fun `updates the next review date of the affected prisoner`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.handleSyncDeleteIepReviewRequest(bookingId, iepReview.id)

      // Then check it's saved
      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync DELETE request is handled
      prisonerIepLevelReviewService.handleSyncDeleteIepReviewRequest(bookingId, iepReview.id)

      // SNS event is sent
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_DELETED,
        description = "An IEP review has been deleted",
        occurredAt = iepReview.reviewTime,
        additionalInformation = AdditionalInformation(
          id = id,
          nomsNumber = prisonerAtLocation().offenderNo,
        ),
      )

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
    fun `If deleted IEP review had current = true, set the latest IEP review current flag to true`(): Unit =
      runBlocking {
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

    @Test
    fun `If deleted IEP review had current = false, doesn't update latest IEP review`(): Unit =
      runBlocking {
        // Prisoner had few IEP reviews
        val currentIepReview = iepReview.copy(current = false)
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

        // and the older IEP is not updated
        verify(prisonerIepLevelRepository, times(0))
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

      // Mock find query
      whenever(prisonerIepLevelRepository.findById(id)).thenReturn(iepReview)

      // Mock PrisonerIepLevel being updated
      whenever(prisonerIepLevelRepository.save(expectedIepReview))
        .thenReturn(expectedIepReview)

      whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
    }

    @Test
    fun `updates the IEP review`(): Unit = runBlocking {
      // When
      val result =
        prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, iepReview.id, syncPatchRequest)

      // Then check it's saved
      verify(prisonerIepLevelRepository, times(1))
        .save(expectedIepReview)

      // Then check it's returned
      assertThat(result).isEqualTo(iepDetail)
    }

    @Test
    fun `updates the next review date for the prisoner`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, iepReview.id, syncPatchRequest)

      // Then check the next review date was updated for the affected prisoner
      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync POST request is handled
      prisonerIepLevelReviewService.handleSyncPatchIepReviewRequest(bookingId, iepReview.id, syncPatchRequest)

      // SNS event is sent
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
        description = "An IEP review has been updated",
        occurredAt = iepReview.reviewTime,
        additionalInformation = AdditionalInformation(
          id = id,
          nomsNumber = prisonerAtLocation().offenderNo,
        ),
      )

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

      // Mock save() of PrisonerIepLevel record
      whenever(prisonerIepLevelRepository.save(iepReview))
        .thenReturn(iepReview.copy(id = iepReviewId))

      whenever(prisonApiService.getIncentiveLevels()).thenReturn(incentiveLevels)
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
    fun `updates next review date`(): Unit = runBlocking {
      // When
      prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest)

      // Then check it's returned
      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `sends IepReview event and audit message`(): Unit = runBlocking {
      // When sync POST request is handled
      prisonerIepLevelReviewService.handleSyncPostIepReviewRequest(bookingId, syncPostRequest)

      // SNS event is sent
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = syncPostRequest.iepTime,
        additionalInformation = AdditionalInformation(
          id = iepReviewId,
          nomsNumber = prisonerAtLocation().offenderNo,
          reason = "USER_CREATED_NOMIS",
        )
      )

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

  private fun iepSummary(iepLevel: String = "Enhanced", bookingId: Long = 1L, iepDetails: List<IepDetail> = emptyList()) = IepSummary(
    bookingId = bookingId,
    iepDate = iepTime.toLocalDate(),
    iepLevel = iepLevel,
    iepTime = iepTime,
    iepDetails = iepDetails,
  )

  private fun iepSummaryWithDetail(bookingId: Long = 1L) = iepSummary(
    iepLevel = "Enhanced",
    bookingId,
    iepDetails = listOf(
      IepDetail(
        bookingId = bookingId,
        agencyId = "MDI",
        iepLevel = "Enhanced",
        iepCode = "ENH",
        iepDate = iepTime.toLocalDate(),
        iepTime = iepTime,
        userId = "TEST_USER",
        auditModuleName = "PRISON_API",
      ),
      IepDetail(
        bookingId = bookingId,
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

  private fun prisonOffenderEvent(reason: String, prisonerNumber: String = "A1244AB") = HMPPSDomainEvent(
    eventType = "prisoner-offender-search.prisoner.received",
    additionalInformation = AdditionalInformation(
      id = 123,
      nomsNumber = prisonerNumber,
      reason = reason
    ),
    occurredAt = Instant.now(),
    description = "A prisoner has been received into a prison with reason: " + when (reason) {
      "NEW_ADMISSION" -> "admission on new charges"
      "READMISSION" -> "re-admission on an existing booking"
      "TRANSFERRED" -> "transfer from another prison"
      "RETURN_FROM_COURT" -> "returned back to prison from court"
      "TEMPORARY_ABSENCE_RETURN" -> "returned after a temporary absence"
      else -> throw NotImplementedError("No description set up for $reason event")
    }
  )

  private fun prisonerAlertsUpdatedEvent(
    prisonerNumber: String = "A1244AB",
    bookingId: Long,
    alertsAdded: List<String> = listOf(PrisonerAlert.ACCT_ALERT_CODE),
    alertsRemoved: List<String> = emptyList()
  ) = HMPPSDomainEvent(
    eventType = "prisoner-offender-search.prisoner.alerts-updated",
    additionalInformation = AdditionalInformation(
      nomsNumber = prisonerNumber,
      bookingId = bookingId,
      alertsAdded = alertsAdded,
      alertsRemoved = alertsRemoved,
    ),
    occurredAt = Instant.now(),
    description = "A prisoner record has been updated"
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

  private val location = Location(
    agencyId = "MDI", locationId = 77777L, description = "Houseblock 1"
  )

  private val basicStandardEnhanced = flowOf(
    IepLevel(
      iepLevel = "BAS",
      iepDescription = "Basic",
      sequence = 1,
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

  private fun iepDetailFromIepLevel(
    prisonerIepLevel: PrisonerIepLevel,
    iepDescription: String,
    iepCode: String,
    id: Long = 0
  ) =
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

  private fun offenderSearchPrisoner(
    prisonerNumber: String = "A1244AB",
    bookingId: Long = 1234567L,
  ) = OffenderSearchPrisoner(
    prisonerNumber = prisonerNumber,
    bookingId = bookingId,
    firstName = "JAMES",
    middleNames = "",
    lastName = "HALLS",
    dateOfBirth = LocalDate.parse("1971-07-01"),
    receptionDate = LocalDate.parse("2020-07-01"),
    prisonId = "MDI",
    alerts = emptyList(),
  )
}

val globalIncentiveLevels = listOf(
  IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
  IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
  IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
  IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 4),
)
val incentiveLevels = globalIncentiveLevels.associateBy { iep -> iep.iepLevel }
