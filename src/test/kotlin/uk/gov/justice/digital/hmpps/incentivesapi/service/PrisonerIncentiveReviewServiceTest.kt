package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerInfo
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Prisoner incentive reviews service")
class PrisonerIncentiveReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val incentiveReviewRepository: IncentiveReviewRepository = mock()
  private val authenticationHolder: HmppsReactiveAuthenticationHolder = mock()
  private val clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val snsService: SnsService = mock()
  private val auditService: AuditService = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService = mock()
  private val incentiveStoreService: IncentiveStoreService = mock()
  private val incentiveLevelService: IncentiveLevelAuditedService = mock()
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService = mock()

  private val nearestPrisonIncentiveLevelService =
    NearestPrisonIncentiveLevelService(incentiveLevelService, prisonIncentiveLevelService)

  private val prisonerIncentiveReviewService = PrisonerIncentiveReviewService(
    prisonApiService,
    prisonerSearchService,
    incentiveReviewRepository,
    incentiveLevelService,
    prisonIncentiveLevelService,
    nearestPrisonIncentiveLevelService,
    snsService,
    auditService,
    authenticationHolder,
    clock,
    nextReviewDateGetterService,
    nextReviewDateUpdaterService,
    incentiveStoreService,
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc
    // while other tests may override the call to the repo
    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    whenever(incentiveLevelService.getAllIncentiveLevelsMapByCode()).thenReturn(incentiveLevels)
  }

  @DisplayName("add incentive review")
  @Nested
  inner class AddIncentiveReviewRequest {

    private val bookingId = 1234567L
    private val prisonerNumber = "A1234BC"
    private val reviewerUserName = "USER_1_GEN"
    private val reviewTime = LocalDateTime.now(clock)
    private val prisonerInfo = mockPrisoner(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
    )
    private val prisonerBasicInfo = PrisonerInfo(bookingId, prisonerNumber, "MDI", "John", "Smith")
    private val createIncentiveReviewRequest = CreateIncentiveReviewRequest(
      iepLevel = "ENH",
      comment = "A review took place",
      reviewType = ReviewType.REVIEW,
    )
    private val incentiveReview = IncentiveReview(
      levelCode = createIncentiveReviewRequest.iepLevel,
      commentText = createIncentiveReviewRequest.comment,
      reviewType = createIncentiveReviewRequest.reviewType!!,
      prisonId = prisonerInfo.prisonId,
      current = true,
      reviewedBy = reviewerUserName,
      reviewTime = reviewTime,
      prisonerNumber = prisonerInfo.prisonerNumber,
      bookingId = prisonerInfo.bookingId,
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(authenticationHolder.getPrincipal()).thenReturn(reviewerUserName)
      whenever(incentiveStoreService.saveIncentiveReview(any())).thenReturn(incentiveReview.copy(id = 42))
      whenever(incentiveLevelService.getAllIncentiveLevelsMapByCode()).thenReturn(incentiveLevels)
    }

    @Test
    fun `addIncentiveReview() by prisoner number`(): Unit = runBlocking {
      // Given
      whenever(prisonerSearchService.getPrisonerInfo(prisonerNumber)).thenReturn(prisonerInfo)

      // When
      prisonerIncentiveReviewService.addIncentiveReview(prisonerNumber, createIncentiveReviewRequest)

      testAddIepReviewCommonFunctionality()
    }

    private suspend fun testAddIepReviewCommonFunctionality() {
      // IEP review is saved
      verify(incentiveStoreService, times(1)).saveIncentiveReview(incentiveReview)

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

      // An audit event is published
      verify(auditService, times(1)).sendMessage(
        AuditType.IEP_REVIEW_ADDED,
        "42",
        iepDetailFromIepLevel(
          incentiveReview,
          iepCode = "ENH",
          iepDescription = "Enhanced",
          id = 42,
        ),
        reviewerUserName,
      )
    }
  }

  @DisplayName("get review history")
  @Nested
  inner class GetIncentiveReviewHistory {

    @Test
    fun `will not return iep details if withDetails is false`(): Unit = runBlocking {
      val bookingId = currentLevel.bookingId
      val expectedNextReviewDate = currentAndPreviousLevels.first().reviewTime.plusYears(1).toLocalDate()

      whenever(incentiveLevelService.getAllIncentiveLevelsMapByCode()).thenReturn(incentiveLevels)
      whenever(nextReviewDateGetterService.get(bookingId)).thenReturn(expectedNextReviewDate)

      // Given
      whenever(incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)).thenReturn(
        currentAndPreviousLevels,
      )

      // When
      val result =
        prisonerIncentiveReviewService.getPrisonerIncentiveHistory(bookingId, withDetails = false)

      // Then
      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository, times(1)).findAllByBookingIdOrderByReviewTimeDesc(bookingId)
      assertThat(result.incentiveReviewDetails.size).isZero
      assertThat(result.nextReviewDate).isEqualTo(expectedNextReviewDate)
    }
  }

  @DisplayName("process received prisoner")
  @Nested
  inner class ProcessReceivedPrisoner {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // This ensures save works and an id is set on the IncentiveReview
      whenever(incentiveStoreService.saveIncentiveReview(any())).thenAnswer { i -> i.arguments[0] }
      whenever(incentiveLevelService.getAllIncentiveLevelsMapByCode()).thenReturn(incentiveLevels)
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
      val prisonerNumber = prisonOffenderEvent.additionalInformation?.nomsNumber!!
      val bookingId = prisonOffenderEvent.additionalInformation?.id!!
      val prisonerAtLocation = mockPrisoner(
        prisonerNumber = prisonerNumber,
        bookingId = bookingId,
      )
      whenever(prisonerSearchService.getPrisonerInfo(prisonerNumber)).thenReturn(prisonerAtLocation)
      // Enhanced is the default for this prison, so use that
      whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels("MDI")).thenReturn(
        listOf(
          PrisonIncentiveLevel(
            levelCode = "STD",
            levelName = "Standard",
            prisonId = "MDI",
            active = true,
            defaultOnAdmission = false,
            remandTransferLimitInPence = 0,
            remandSpendLimitInPence = 0,
            convictedTransferLimitInPence = 0,
            convictedSpendLimitInPence = 0,
            visitOrders = 0,
            privilegedVisitOrders = 0,
          ),
          PrisonIncentiveLevel(
            levelCode = "ENH",
            levelName = "Enhanced",
            prisonId = "MDI",
            active = true,
            defaultOnAdmission = true,
            remandTransferLimitInPence = 0,
            remandSpendLimitInPence = 0,
            convictedTransferLimitInPence = 0,
            convictedSpendLimitInPence = 0,
            visitOrders = 0,
            privilegedVisitOrders = 0,
          ),
        ),
      )

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then
      val expectedIncentiveReview = IncentiveReview(
        levelCode = "ENH",
        commentText = "Default level assigned on arrival",
        bookingId = prisonerAtLocation.bookingId,
        prisonId = prisonerAtLocation.prisonId,
        current = true,
        reviewedBy = "INCENTIVES_API",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = expectedReviewType,
        prisonerNumber = prisonerAtLocation.prisonerNumber,
      )

      verify(incentiveStoreService, times(1)).saveIncentiveReview(expectedIncentiveReview)

      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_INSERTED,
        description = "An IEP review has been added",
        occurredAt = expectedIncentiveReview.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 0,
          nomsNumber = prisonerNumber,
        ),
      )
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedIncentiveReview, "Enhanced", "ENH"),
          expectedIncentiveReview.reviewedBy,
        )
    }

    @Test
    fun `process 'prisoner alerts updated' event when ACCT alert added`(): Unit = runBlocking {
      val bookingId = 1234567L

      // Given
      val prisonerAlertsUpdatedEvent = prisonerAlertsUpdatedEvent(
        alertsAdded = listOf(PrisonerAlert.ACCT_ALERT_CODE),
        alertsRemoved = emptyList(),
      )

      // When
      prisonerIncentiveReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `process 'prisoner alerts updated' event when ACCT alert removed`(): Unit = runBlocking {
      val bookingId = 1234567L

      // Given
      val prisonerAlertsUpdatedEvent = prisonerAlertsUpdatedEvent(
        alertsAdded = emptyList(),
        alertsRemoved = listOf(PrisonerAlert.ACCT_ALERT_CODE),
      )

      // When
      prisonerIncentiveReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .update(bookingId)
    }

    @Test
    fun `process 'prisoner alerts updated' event when alerts didn't change`(): Unit = runBlocking {
      val bookingId = 1234567L

      // Given
      val prisonerUpdatedEvent = prisonerAlertsUpdatedEvent(
        alertsAdded = listOf("ABC"),
        alertsRemoved = listOf("XYZ"),
      )

      // When
      prisonerIncentiveReviewService.processPrisonerAlertsUpdatedEvent(prisonerUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(0))
        .update(bookingId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["RETURN_FROM_COURT", "TEMPORARY_ABSENCE_RETURN"])
    fun `do not process irrelevant reasons`(reason: String): Unit = runBlocking {
      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent(reason))

      // Then
      verifyNoInteractions(incentiveReviewRepository)
    }

    @Test
    fun `process merge event`(): Unit = runBlocking {
      // Given - default for that prison is Enhanced
      val prisonerMergedEvent = prisonerMergedEvent()
      val prisonerAtLocation = mockPrisoner(
        bookingId = 1234567,
        prisonerNumber = "A1244AB",
      )
      whenever(prisonerSearchService.getPrisonerInfo("A1244AB"))
        .thenReturn(prisonerAtLocation)

      val newReview = IncentiveReview(
        id = 1L,
        prisonerNumber = "A8765SS",
        bookingId = 1234567L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(2),
      )
      val oldReview1 = IncentiveReview(
        id = 3L,
        prisonerNumber = "A1244AB",
        bookingId = 555555L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now().minusDays(100),
      )

      val oldReview2 = IncentiveReview(
        id = 2L,
        prisonerNumber = "A1244AB",
        bookingId = 555555L,
        prisonId = "LEI",
        locationId = "LEI-1-1-001",
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        current = false,
        reviewTime = LocalDateTime.now().minusDays(200),
      )

      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
        .thenReturn(
          flowOf(
            newReview,
          ),
        )
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
        .thenReturn(
          flowOf(
            oldReview2,
            oldReview1,
          ),
        )

      prisonerIncentiveReviewService.processOffenderEvent(prisonerMergedEvent)

      verify(incentiveStoreService).updateMergedReviews(
        listOf(
          newReview.copy(prisonerNumber = "A1244AB"),
          oldReview2.copy(bookingId = 1234567L, id = 0L, current = false),
          oldReview1.copy(bookingId = 1234567L, id = 0L, current = false),
        ),
        1234567L,
      )

      verify(auditService, times(1))
        .sendMessage(
          AuditType.PRISONER_NUMBER_MERGE,
          "A1244AB",
          "3 incentive records updated from merge A8765SS -> A1244AB. Updated to booking ID 1234567",
          "INCENTIVES_API",
        )
    }

    @Test
    fun `process booking moved event`(): Unit = runBlocking {
      val currentReview = IncentiveReview(
        id = 10L,
        prisonerNumber = "A8765SS",
        bookingId = 1234567L,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "ENH",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(1),
      )
      val olderReview = IncentiveReview(
        id = 9L,
        prisonerNumber = "A8765SS",
        bookingId = 1234567L,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = false,
        reviewTime = LocalDateTime.now(clock).minusDays(2),
      )
      whenever(incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(1234567))
        .thenReturn(flowOf(currentReview, olderReview))
      whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>()))
        .thenReturn(flowOf())

      val event = bookingMovedEvent()
      prisonerIncentiveReviewService.processBookingMovedEvent(event)

      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository).saveAll(
        listOf(
          currentReview.copy(prisonerNumber = "A1244AB"),
          olderReview.copy(prisonerNumber = "A1244AB"),
        ),
      )
    }

    @Test
    fun `do not create review if prisoner number is null`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = HMPPSDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        additionalInformation = AdditionalInformation(
          id = 123,
          reason = "NEW_ADMISSION",
        ),
        occurredAt = Instant.now(),
        description = "A prisoner has been received into a prison with reason: admission on new charges",
      )

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then
      verifyNoInteractions(incentiveReviewRepository)
    }
  }

  private val previousLevel = IncentiveReview(
    levelCode = "BAS",
    prisonId = "LEI",
    locationId = "LEI-1-1-001",
    bookingId = 1234567,
    current = false,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now().minusDays(2),
    prisonerNumber = "A1234AB",
  )

  private val currentLevel = IncentiveReview(
    id = 1,
    levelCode = "STD",
    prisonId = "MDI",
    locationId = "MDI-1-1-004",
    bookingId = 1234567,
    current = true,
    reviewedBy = "TEST_STAFF1",
    reviewTime = LocalDateTime.now(),
    prisonerNumber = "A1234AB",
  )

  private val currentAndPreviousLevels = flowOf(previousLevel, currentLevel)

  private fun prisonOffenderEvent(reason: String, prisonerNumber: String = "A1244AB") = HMPPSDomainEvent(
    eventType = "prisoner-offender-search.prisoner.received",
    additionalInformation = AdditionalInformation(
      id = 123,
      nomsNumber = prisonerNumber,
      reason = reason,
    ),
    occurredAt = Instant.now(),
    description = "A prisoner has been received into a prison with reason: " + when (reason) {
      "NEW_ADMISSION" -> "admission on new charges"
      "READMISSION" -> "re-admission on an existing booking"
      "TRANSFERRED" -> "transfer from another prison"
      "RETURN_FROM_COURT" -> "returned back to prison from court"
      "TEMPORARY_ABSENCE_RETURN" -> "returned after a temporary absence"
      else -> throw NotImplementedError("No description set up for $reason event")
    },
  )

  private fun prisonerAlertsUpdatedEvent(
    alertsAdded: List<String> = listOf(PrisonerAlert.ACCT_ALERT_CODE),
    alertsRemoved: List<String> = emptyList(),
  ) = HMPPSDomainEvent(
    eventType = "prisoner-offender-search.prisoner.alerts-updated",
    additionalInformation = AdditionalInformation(
      nomsNumber = "A1244AB",
      bookingId = 1234567,
      alertsAdded = alertsAdded,
      alertsRemoved = alertsRemoved,
    ),
    occurredAt = Instant.now(),
    description = "A prisoner record has been updated",
  )

  private fun prisonerMergedEvent() = HMPPSDomainEvent(
    eventType = "prison-offender-events.prisoner.merged",
    additionalInformation = AdditionalInformation(
      nomsNumber = "A1244AB",
      reason = "MERGE",
      removedNomsNumber = "A8765SS",
    ),
    occurredAt = Instant.now(),
    description = "A prisoner has been merged from A8765SS to A1244AB",
  )

  private fun bookingMovedEvent(bookingStartDateTime: LocalDateTime? = null) = HMPPSBookingMovedDomainEvent(
    eventType = "prison-offender-events.prisoner.booking.moved",
    additionalInformation = AdditionalInformationBookingMoved(
      bookingId = 1234567,
      movedFromNomsNumber = "A8765SS",
      movedToNomsNumber = "A1244AB",
      bookingStartDateTime = bookingStartDateTime,
    ),
    occurredAt = ZonedDateTime.now(clock),
    version = "1.0",
    description = "a NOMIS booking has moved between prisoners",
  )

  private fun iepDetailFromIepLevel(
    incentiveReview: IncentiveReview,
    iepDescription: String,
    iepCode: String,
    id: Long = 0,
  ) = IncentiveReviewDetail(
    id = id,
    iepLevel = iepDescription,
    iepCode = iepCode,
    comments = incentiveReview.commentText,
    bookingId = incentiveReview.bookingId,
    agencyId = incentiveReview.prisonId,
    locationId = incentiveReview.locationId,
    userId = incentiveReview.reviewedBy,
    iepDate = incentiveReview.reviewTime.toLocalDate(),
    iepTime = incentiveReview.reviewTime,
    reviewType = incentiveReview.reviewType,
    prisonerNumber = incentiveReview.prisonerNumber,
    auditModuleName = "INCENTIVES_API",
  )

  private val globalIncentiveLevels = listOf(
    IncentiveLevel(code = "BAS", name = "Basic"),
    IncentiveLevel(code = "STD", name = "Standard"),
    IncentiveLevel(code = "ENH", name = "Enhanced"),
    IncentiveLevel(code = "EN2", name = "Enhanced 2"),
  )

  private val incentiveLevels = globalIncentiveLevels.associateBy { iep -> iep.code }
}
