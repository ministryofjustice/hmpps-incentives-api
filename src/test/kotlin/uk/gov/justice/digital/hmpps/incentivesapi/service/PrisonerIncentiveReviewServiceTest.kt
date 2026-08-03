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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BookingSwitchRepairOutcome
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Prisoner incentive reviews service")
class PrisonerIncentiveReviewServiceTest {

  private val prisonerSearchService: PrisonerSearchService = mock()
  private val incentiveReviewRepository: IncentiveReviewRepository = mock()
  private val authenticationHolder: HmppsReactiveAuthenticationHolder = mock()
  private val clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val snsService: SnsService = mock()
  private val auditService: AuditService = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService = mock()
  private val nextReviewDateRepository: NextReviewDateRepository = mock()
  private val incentiveStoreService: IncentiveStoreService = mock()
  private val transactionalOperator = TransactionalOperator.create(
    object : ReactiveTransactionManager {
      override fun getReactiveTransaction(definition: TransactionDefinition?): Mono<ReactiveTransaction> =
        Mono.just(mock())
      override fun commit(transaction: ReactiveTransaction): Mono<Void> = Mono.empty()
      override fun rollback(transaction: ReactiveTransaction): Mono<Void> = Mono.empty()
    },
  )
  private val incentiveLevelService: IncentiveLevelAuditedService = mock()
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService = mock()

  private val nearestPrisonIncentiveLevelService =
    NearestPrisonIncentiveLevelService(incentiveLevelService, prisonIncentiveLevelService)

  private val prisonerIncentiveReviewService = PrisonerIncentiveReviewService(
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
    nextReviewDateRepository,
    incentiveStoreService,
    transactionalOperator,
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc
    // while other tests may override the call to the repo
    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    // Default for the before/after current-review lookups in merge / booking-moved handling.
    // Tests that exercise a change in current incentive override this for specific prisoner numbers.
    whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    // Default next review date for the snapshot lookups; tests that exercise a next-review-date
    // change override this with consecutive return values.
    whenever(nextReviewDateRepository.findById(any()))
      .thenReturn(NextReviewDate(bookingId = 0L, nextReviewDate = LocalDate.parse("2023-01-01")))

    whenever(incentiveLevelService.getAllIncentiveLevelsMapByCode()).thenReturn(incentiveLevels)
  }

  /**
   * A mistaken new booking is corrected in NOMIS by releasing the prisoner and re-admitting them
   * onto their earlier booking, so the reinstated booking always has the *lower* id. Shared by the
   * event-driven correction and the repair endpoint, which are the same operation.
   */
  private val switchPrisonerNumber = "A1244AB"
  private val reinstatedBookingId = 1000000L
  private val mistakenBookingId = 2000000L

  private val reviewOnReinstatedBooking = IncentiveReview(
    id = 1L,
    prisonerNumber = switchPrisonerNumber,
    bookingId = reinstatedBookingId,
    prisonId = "LEI",
    reviewedBy = "TEST_STAFF1",
    levelCode = "ENH",
    current = true,
    reviewTime = LocalDateTime.now(clock).minusDays(100),
  )

  private val reviewOnMistakenBooking = IncentiveReview(
    id = 2L,
    prisonerNumber = switchPrisonerNumber,
    bookingId = mistakenBookingId,
    prisonId = "MDI",
    reviewedBy = "INCENTIVES_API",
    levelCode = "STD",
    current = true,
    reviewType = ReviewType.INITIAL,
    reviewTime = LocalDateTime.now(clock).minusDays(1),
  )

  private suspend fun stubBookingSwitch(vararg reviews: IncentiveReview) {
    whenever(prisonerSearchService.getPrisonerInfo(switchPrisonerNumber))
      .thenReturn(mockPrisoner(bookingId = reinstatedBookingId, prisonerNumber = switchPrisonerNumber))
    // before snapshot, the handler's own read, then the after snapshot
    whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(switchPrisonerNumber))
      .thenReturn(flowOf(*reviews), flowOf(*reviews), flowOf(*reviews))
    whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>())).thenReturn(flowOf())
    whenever(incentiveReviewRepository.save(any())).thenAnswer { i -> i.arguments[0] }
    whenever(nextReviewDateUpdaterService.updateWriteOnly(reinstatedBookingId))
      .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))
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
      val bookingId = prisonOffenderEvent.additionalInformation.id!!
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
      whenever(nextReviewDateUpdaterService.updateWriteOnly(bookingId))
        .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))

      // When
      prisonerIncentiveReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .updateWriteOnly(bookingId)
    }

    @Test
    fun `process 'prisoner alerts updated' event when ACCT alert removed`(): Unit = runBlocking {
      val bookingId = 1234567L

      // Given
      val prisonerAlertsUpdatedEvent = prisonerAlertsUpdatedEvent(
        alertsAdded = emptyList(),
        alertsRemoved = listOf(PrisonerAlert.ACCT_ALERT_CODE),
      )
      whenever(nextReviewDateUpdaterService.updateWriteOnly(bookingId))
        .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))

      // When
      prisonerIncentiveReviewService.processPrisonerAlertsUpdatedEvent(prisonerAlertsUpdatedEvent)

      verify(nextReviewDateUpdaterService, times(1))
        .updateWriteOnly(bookingId)
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
        .updateWriteOnly(bookingId)
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

      // Current incentive unchanged (no current-review stubs) so no iep-review.updated event
      verify(snsService, never()).publishDomainEvent(
        eventType = eq(IncentivesDomainEventType.IEP_REVIEW_UPDATED),
        description = any(),
        occurredAt = any(),
        additionalInformation = any(),
      )
    }

    @Test
    fun `merge publishes iep-review-updated when survivor's current incentive changes`(): Unit = runBlocking {
      val survivorCurrentBefore = IncentiveReview(
        id = 3L,
        prisonerNumber = "A1244AB",
        bookingId = 1234567L,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(100),
      )
      // The removed prisoner's current (Enhanced) review, which the survivor takes on after the merge
      val survivorCurrentAfter = IncentiveReview(
        id = 100L,
        prisonerNumber = "A1244AB",
        bookingId = 999999L,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "ENH",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(1),
      )

      whenever(prisonerSearchService.getPrisonerInfo("A1244AB"))
        .thenReturn(mockPrisoner(bookingId = 1234567, prisonerNumber = "A1244AB"))
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
        .thenReturn(flowOf(survivorCurrentAfter.copy(prisonerNumber = "A8765SS")))
      // before lookup, then the merge logic's read of the survivor's reviews, then the after lookup
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
        .thenReturn(
          flowOf(survivorCurrentBefore),
          flowOf(survivorCurrentBefore),
          flowOf(survivorCurrentAfter),
        )

      prisonerIncentiveReviewService.processOffenderEvent(prisonerMergedEvent())

      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
        description = "An IEP review has been updated",
        occurredAt = survivorCurrentAfter.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 100L,
          nomsNumber = "A1244AB",
        ),
      )
    }

    @Test
    fun `merge publishes iep-review-updated when only the survivor's next review date changes`(): Unit = runBlocking {
      // Same current review row before and after the merge (same level and review time), but the
      // merge brings in extra history that shifts the computed next review date.
      val survivorCurrent = IncentiveReview(
        id = 3L,
        prisonerNumber = "A1244AB",
        bookingId = 1234567L,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "STD",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(100),
      )

      whenever(prisonerSearchService.getPrisonerInfo("A1244AB"))
        .thenReturn(mockPrisoner(bookingId = 1234567, prisonerNumber = "A1244AB"))
      // Removed prisoner has reviews so the merge actually updates records
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
        .thenReturn(flowOf(survivorCurrent.copy(id = 50L, prisonerNumber = "A8765SS", bookingId = 999999L)))
      // before lookup, the merge logic's read, then the after lookup — all the same current review
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
        .thenReturn(
          flowOf(survivorCurrent),
          flowOf(survivorCurrent),
          flowOf(survivorCurrent),
        )
      // Next review date for the survivor's booking changes as a result of the merge
      whenever(nextReviewDateRepository.findById(1234567L))
        .thenReturn(
          NextReviewDate(bookingId = 1234567L, nextReviewDate = LocalDate.parse("2023-01-01")),
          NextReviewDate(bookingId = 1234567L, nextReviewDate = LocalDate.parse("2024-06-01")),
        )

      prisonerIncentiveReviewService.processOffenderEvent(prisonerMergedEvent())

      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
        description = "An IEP review has been updated",
        occurredAt = survivorCurrent.reviewTime,
        additionalInformation = AdditionalInformation(
          id = 3L,
          nomsNumber = "A1244AB",
        ),
      )
    }

    @Test
    fun `merge does not publish iep-review-updated when level, review time and next review date are unchanged`(): Unit =
      runBlocking {
        val survivorCurrent = IncentiveReview(
          id = 3L,
          prisonerNumber = "A1244AB",
          bookingId = 1234567L,
          prisonId = "LEI",
          reviewedBy = "TEST_STAFF1",
          levelCode = "STD",
          current = true,
          reviewTime = LocalDateTime.now(clock).minusDays(100),
        )

        whenever(prisonerSearchService.getPrisonerInfo("A1244AB"))
          .thenReturn(mockPrisoner(bookingId = 1234567, prisonerNumber = "A1244AB"))
        whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A8765SS"))
          .thenReturn(flowOf(survivorCurrent.copy(id = 50L, prisonerNumber = "A8765SS", bookingId = 999999L)))
        whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
          .thenReturn(
            flowOf(survivorCurrent),
            flowOf(survivorCurrent),
            flowOf(survivorCurrent),
          )
        // Next review date unchanged (default stub returns the same date for every call)

        prisonerIncentiveReviewService.processOffenderEvent(prisonerMergedEvent())

        verify(snsService, never()).publishDomainEvent(
          eventType = eq(IncentivesDomainEventType.IEP_REVIEW_UPDATED),
          description = any(),
          occurredAt = any(),
          additionalInformation = any(),
        )
      }

    @Test
    fun `booking moved publishes iep-review-updated when moved-to prisoner's current incentive changes`(): Unit =
      runBlocking {
        val movedReview = IncentiveReview(
          id = 10L,
          prisonerNumber = "A8765SS",
          bookingId = 1234567L,
          prisonId = "LEI",
          reviewedBy = "TEST_STAFF1",
          levelCode = "ENH",
          current = true,
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        )
        val movedToBefore = IncentiveReview(
          id = 5L,
          prisonerNumber = "A1244AB",
          bookingId = 222222L,
          prisonId = "LEI",
          reviewedBy = "TEST_STAFF1",
          levelCode = "STD",
          current = true,
          reviewTime = LocalDateTime.now(clock).minusDays(5),
        )
        val movedToAfter = movedReview.copy(prisonerNumber = "A1244AB")

        whenever(incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(1234567))
          .thenReturn(flowOf(movedReview))
        whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>()))
          .thenReturn(flowOf())
        // moved-from prisoner: unchanged current (default empty stub leaves it null before & after)
        // moved-to prisoner: before lookup then after lookup
        whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc("A1244AB"))
          .thenReturn(flowOf(movedToBefore), flowOf(movedToAfter))

        prisonerIncentiveReviewService.processBookingMovedEvent(bookingMovedEvent())

        verify(snsService, times(1)).publishDomainEvent(
          eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
          description = "An IEP review has been updated",
          occurredAt = movedToAfter.reviewTime,
          additionalInformation = AdditionalInformation(
            id = 10L,
            nomsNumber = "A1244AB",
          ),
        )
      }

    @Test
    fun `booking switch stands down the review written against the mistaken later booking`(): Unit = runBlocking {
      // Given - the prisoner is back on their earlier booking, but the review this service wrote
      // against the mistaken booking is still current and, being newest, wins prisoner-scoped reads
      stubBookingSwitch(reviewOnMistakenBooking, reviewOnReinstatedBooking)

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

      // Then
      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository).saveAll(
        listOf(reviewOnMistakenBooking.copy(current = false, new = false)),
      )
      // the reinstated booking's latest review was already current, so it is left untouched
      verify(incentiveReviewRepository, never()).save(any())
      verify(nextReviewDateUpdaterService, times(1)).updateWriteOnly(reinstatedBookingId)
      verify(auditService, times(1)).sendMessage(
        eq(AuditType.PRISONER_BOOKING_SWITCHED),
        eq(switchPrisonerNumber),
        any(),
        eq("INCENTIVES_API"),
      )
    }

    @Test
    fun `booking switch reinstates the latest review on the correct booking when it is not current`(): Unit =
      runBlocking {
        // Given
        val notCurrentOnReinstatedBooking = reviewOnReinstatedBooking.copy(current = false)
        stubBookingSwitch(reviewOnMistakenBooking, notCurrentOnReinstatedBooking)

        // When
        prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

        // Then
        verify(incentiveReviewRepository, times(1))
          .save(notCurrentOnReinstatedBooking.copy(current = true, new = false))
      }

    @Test
    fun `booking switch does not touch current reviews on earlier bookings`(): Unit = runBlocking {
      // Given - history from a previous sentence, left current because nothing ever clears it.
      // It sits on a lower booking than the reinstated one, so it is legitimate and must survive.
      val previousSentenceReview = IncentiveReview(
        id = 3L,
        prisonerNumber = switchPrisonerNumber,
        bookingId = 500000L,
        prisonId = "LEI",
        reviewedBy = "INCENTIVES_API",
        levelCode = "BAS",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(500),
      )
      stubBookingSwitch(reviewOnReinstatedBooking, previousSentenceReview)

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

      // Then - nothing to correct
      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository, never()).saveAll(any<List<IncentiveReview>>())
      verify(incentiveReviewRepository, never()).save(any())
      verify(nextReviewDateUpdaterService, never()).updateWriteOnly(any())
      verifyNoInteractions(auditService)
    }

    @Test
    fun `booking switch stands down every later booking when staff got the admission wrong twice`(): Unit =
      runBlocking {
        // Given - staff admitted the prisoner onto a new booking, did it again two days later, then
        // switched back past both. Seen in production for A3812EX. Booking ids are sequential, so
        // anything above the reinstated booking was created after it and was abandoned; standing
        // down only the newest would leave the second admission's default level winning the read.
        val secondMistakenBooking = IncentiveReview(
          id = 4L,
          prisonerNumber = switchPrisonerNumber,
          bookingId = 1500000L,
          prisonId = "LEI",
          reviewedBy = "INCENTIVES_API",
          levelCode = "STD",
          current = true,
          reviewType = ReviewType.INITIAL,
          reviewTime = LocalDateTime.now(clock).minusDays(3),
        )
        stubBookingSwitch(reviewOnMistakenBooking, secondMistakenBooking, reviewOnReinstatedBooking)

        // When
        prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

        // Then - both abandoned bookings are stood down, leaving the reinstated booking's level
        @Suppress("UnusedFlow")
        verify(incentiveReviewRepository).saveAll(
          listOf(
            reviewOnMistakenBooking.copy(current = false, new = false),
            secondMistakenBooking.copy(current = false, new = false),
          ),
        )
      }

    @Test
    fun `booking switch leaves a human-authored review on a later booking alone`(): Unit = runBlocking {
      // Given - staff reviewed the prisoner the day after the mistaken admission, against that
      // booking, so their decision superseded the default level within it. Seen in production for
      // A6487AY. Only reviews this service wrote on admission may be stood down, so this no-ops and
      // the prisoner keeps a level recorded against a booking they are no longer on — deliberately,
      // because discarding a real review is not a decision this service can make.
      val humanReviewOnMistakenBooking = reviewOnMistakenBooking.copy(
        id = 5L,
        reviewedBy = "TEST_STAFF1",
        levelCode = "BAS",
        reviewType = ReviewType.REVIEW,
        reviewTime = LocalDateTime.now(clock),
      )
      stubBookingSwitch(
        humanReviewOnMistakenBooking,
        // the service's own admission review, already stood down by the human one on the same booking
        reviewOnMistakenBooking.copy(current = false),
        reviewOnReinstatedBooking,
      )

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

      // Then
      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository, never()).saveAll(any<List<IncentiveReview>>())
      verify(incentiveReviewRepository, never()).save(any())
      verifyNoInteractions(auditService)
    }

    @Test
    fun `booking switch publishes iep-review-updated when the current review changes`(): Unit = runBlocking {
      // Given
      whenever(prisonerSearchService.getPrisonerInfo(switchPrisonerNumber))
        .thenReturn(mockPrisoner(bookingId = reinstatedBookingId, prisonerNumber = switchPrisonerNumber))
      whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>())).thenReturn(flowOf())
      whenever(nextReviewDateUpdaterService.updateWriteOnly(reinstatedBookingId))
        .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))
      // before the switch the mistaken Standard review is current; afterwards only the
      // reinstated booking's Enhanced review remains current
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(switchPrisonerNumber))
        .thenReturn(
          flowOf(reviewOnMistakenBooking, reviewOnReinstatedBooking),
          flowOf(reviewOnMistakenBooking, reviewOnReinstatedBooking),
          flowOf(reviewOnReinstatedBooking),
        )

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent("READMISSION_SWITCH_BOOKING"))

      // Then
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
        description = "An IEP review has been updated",
        occurredAt = reviewOnReinstatedBooking.reviewTime,
        additionalInformation = AdditionalInformation(
          id = reviewOnReinstatedBooking.id,
          nomsNumber = switchPrisonerNumber,
        ),
      )
    }

    @Test
    fun `do not process booking switch if prisoner number is null`(): Unit = runBlocking {
      // Given
      val prisonOffenderEvent = HMPPSDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        additionalInformation = AdditionalInformation(
          id = 123,
          reason = "READMISSION_SWITCH_BOOKING",
        ),
        occurredAt = Instant.now(),
        description = "A prisoner has been received into a prison with reason: " +
          "re-admission but switched to old booking",
      )

      // When
      prisonerIncentiveReviewService.processOffenderEvent(prisonOffenderEvent)

      // Then
      verifyNoInteractions(incentiveReviewRepository)
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

  @DisplayName("repair after booking switch")
  @Nested
  inner class RepairAfterBookingSwitch {

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(authenticationHolder.getPrincipal()).thenReturn("REPAIR_USER")
    }

    @Test
    fun `repairs a prisoner left on the level from a booking NOMIS has switched away from`(): Unit = runBlocking {
      // Given - the same state the event handler corrects, but for a prisoner whose event was
      // missed or who was affected before the event was handled at all
      val notCurrentOnReinstatedBooking = reviewOnReinstatedBooking.copy(current = false)
      whenever(prisonerSearchService.getPrisonerInfo(switchPrisonerNumber))
        .thenReturn(mockPrisoner(bookingId = reinstatedBookingId, prisonerNumber = switchPrisonerNumber))
      whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>())).thenReturn(flowOf())
      whenever(incentiveReviewRepository.save(any())).thenAnswer { i -> i.arguments[0] }
      whenever(nextReviewDateUpdaterService.updateWriteOnly(reinstatedBookingId))
        .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))
      // before snapshot, the repair's own read, then the after snapshot
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(switchPrisonerNumber))
        .thenReturn(
          flowOf(reviewOnMistakenBooking, notCurrentOnReinstatedBooking),
          flowOf(reviewOnMistakenBooking, notCurrentOnReinstatedBooking),
          flowOf(reviewOnReinstatedBooking),
        )

      // When
      val result = prisonerIncentiveReviewService.repairAfterBookingSwitch(switchPrisonerNumber)

      // Then
      assertThat(result.outcome).isEqualTo(BookingSwitchRepairOutcome.REPAIRED)
      assertThat(result.dryRun).isFalse()
      assertThat(result.bookingId).isEqualTo(reinstatedBookingId)
      assertThat(result.levelCodeBefore).isEqualTo("STD")
      assertThat(result.levelCodeAfter).isEqualTo("ENH")
      assertThat(result.reviewIdsStoodDown).isEqualTo(listOf(reviewOnMistakenBooking.id))
      assertThat(result.reviewIdReinstated).isEqualTo(reviewOnReinstatedBooking.id)

      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository).saveAll(listOf(reviewOnMistakenBooking.copy(current = false, new = false)))
      verify(incentiveReviewRepository).save(notCurrentOnReinstatedBooking.copy(current = true, new = false))
      verify(nextReviewDateUpdaterService, times(1)).updateWriteOnly(reinstatedBookingId)
    }

    @Test
    fun `repair publishes iep-review-updated so downstream services resync`(): Unit = runBlocking {
      // Given - a database-level fix could not do this, which is why the repair is an endpoint
      whenever(prisonerSearchService.getPrisonerInfo(switchPrisonerNumber))
        .thenReturn(mockPrisoner(bookingId = reinstatedBookingId, prisonerNumber = switchPrisonerNumber))
      whenever(incentiveReviewRepository.saveAll(any<List<IncentiveReview>>())).thenReturn(flowOf())
      whenever(nextReviewDateUpdaterService.updateWriteOnly(reinstatedBookingId))
        .thenReturn(NextReviewDateChanges(emptyMap(), emptyList(), emptyMap()))
      whenever(incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(switchPrisonerNumber))
        .thenReturn(
          flowOf(reviewOnMistakenBooking, reviewOnReinstatedBooking),
          flowOf(reviewOnMistakenBooking, reviewOnReinstatedBooking),
          flowOf(reviewOnReinstatedBooking),
        )

      // When
      prisonerIncentiveReviewService.repairAfterBookingSwitch(switchPrisonerNumber)

      // Then
      verify(snsService, times(1)).publishDomainEvent(
        eventType = IncentivesDomainEventType.IEP_REVIEW_UPDATED,
        description = "An IEP review has been updated",
        occurredAt = reviewOnReinstatedBooking.reviewTime,
        additionalInformation = AdditionalInformation(
          id = reviewOnReinstatedBooking.id,
          nomsNumber = switchPrisonerNumber,
        ),
      )
      // audited against the caller rather than the service, since a person asked for this
      verify(auditService, times(1)).sendMessage(
        eq(AuditType.PRISONER_BOOKING_SWITCHED),
        eq(switchPrisonerNumber),
        any(),
        eq("REPAIR_USER"),
      )
    }

    @Test
    fun `dry run reports the repair without writing anything or publishing events`(): Unit = runBlocking {
      // Given
      stubBookingSwitch(reviewOnMistakenBooking, reviewOnReinstatedBooking)

      // When
      val result = prisonerIncentiveReviewService.repairAfterBookingSwitch(switchPrisonerNumber, dryRun = true)

      // Then
      assertThat(result.outcome).isEqualTo(BookingSwitchRepairOutcome.REPAIRED)
      assertThat(result.dryRun).isTrue()
      assertThat(result.levelCodeBefore).isEqualTo("STD")
      assertThat(result.levelCodeAfter).isEqualTo("ENH")
      assertThat(result.reviewIdsStoodDown).isEqualTo(listOf(reviewOnMistakenBooking.id))

      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository, never()).saveAll(any<List<IncentiveReview>>())
      verify(incentiveReviewRepository, never()).save(any())
      verify(nextReviewDateUpdaterService, never()).updateWriteOnly(any())
      verifyNoInteractions(snsService)
      verifyNoInteractions(auditService)
    }

    @Test
    fun `repairing a prisoner who is already correct is a safe no-op`(): Unit = runBlocking {
      // Given - so the repair can be re-run over the whole list without checking first
      stubBookingSwitch(reviewOnReinstatedBooking)

      // When
      val result = prisonerIncentiveReviewService.repairAfterBookingSwitch(switchPrisonerNumber)

      // Then
      assertThat(result.outcome).isEqualTo(BookingSwitchRepairOutcome.NOTHING_TO_DO)
      assertThat(result.levelCodeBefore).isEqualTo("ENH")
      assertThat(result.levelCodeAfter).isEqualTo("ENH")
      assertThat(result.reviewIdsStoodDown).isEmpty()
      assertThat(result.reviewIdReinstated).isNull()

      @Suppress("UnusedFlow")
      verify(incentiveReviewRepository, never()).saveAll(any<List<IncentiveReview>>())
      verify(incentiveReviewRepository, never()).save(any())
      verifyNoInteractions(snsService)
      verifyNoInteractions(auditService)
    }
  }

  private val previousLevel = IncentiveReview(
    levelCode = "BAS",
    prisonId = "LEI",
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
      "READMISSION_SWITCH_BOOKING" -> "re-admission but switched to old booking"
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
