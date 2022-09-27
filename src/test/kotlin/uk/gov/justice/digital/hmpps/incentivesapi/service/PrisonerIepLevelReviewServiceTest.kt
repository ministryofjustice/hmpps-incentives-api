package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrisonerIepLevelReviewServiceTest {

  private val prisonApiService: PrisonApiService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val iepLevelRepository: IepLevelRepository = mock()
  private val iepLevelService: IepLevelService = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private var clock: Clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault())
  private val snsService: SnsService = mock()
  private val auditService: AuditService = mock()

  private val prisonerIepLevelReviewService = PrisonerIepLevelReviewService(
    prisonApiService,
    prisonerIepLevelRepository,
    iepLevelRepository,
    iepLevelService,
    snsService,
    auditService,
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
  inner class processReceivedPrisoner {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // This ensures save works and an id is set on the PrisonerIepLevel
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }
      whenever(iepLevelRepository.findById("STD")).thenReturn(IepLevel(iepCode = "STD", iepDescription = "Standard"))
      whenever(iepLevelRepository.findById("ENH")).thenReturn(IepLevel(iepCode = "ENH", iepDescription = "Enhanced"))
    }

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
          IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 1, default = true),
        )
      )

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "ENH",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation().bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "incentives-api",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.INITIAL,
        prisonerNumber = prisonerAtLocation().offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, expectedPrisonerIepLevel.reviewTime)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Enhanced"),
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
      whenever(iepLevelService.getIepLevelsForPrison(prisonerAtLocation.agencyId)).thenReturn(basicStandardEnhanced)
      val iepDetails = listOf(
        iepDetail(prisonerAtLocation.agencyId, "BAS", LocalDateTime.now()),
        iepDetail("BXI", "STD", LocalDateTime.now().minusDays(1)),
        iepDetail("LEI", "BAS", LocalDateTime.now().minusDays(2)),
      )
      val iepSummary = iepSummary(iepDetails = iepDetails)
      whenever(prisonApiService.getIEPSummaryForPrisoner(prisonerAtLocation.bookingId, withDetails = true, useClientCredentials = true)).thenReturn(iepSummary)

      // When
      prisonerIepLevelReviewService.processReceivedPrisoner(prisonOffenderEvent)

      // Then - the prisoner was on STD at BXI so they on this level
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "STD",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation.bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "incentives-api",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerAtLocation.offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, expectedPrisonerIepLevel.reviewTime)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Standard"),
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

      // Then - MDI prison does not support ENH2 (which they were on in BXI) so fallback to ENH
      val expectedPrisonerIepLevel = PrisonerIepLevel(
        iepCode = "ENH",
        commentText = prisonOffenderEvent.description,
        bookingId = prisonerAtLocation.bookingId,
        prisonId = location.agencyId,
        locationId = location.description,
        current = true,
        reviewedBy = "incentives-api",
        reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        reviewType = ReviewType.TRANSFER,
        prisonerNumber = prisonerAtLocation.offenderNo
      )

      verify(prisonerIepLevelRepository, times(1)).save(expectedPrisonerIepLevel)
      verify(snsService, times(1)).sendIepReviewEvent(0, expectedPrisonerIepLevel.reviewTime)
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "0",
          iepDetailFromIepLevel(expectedPrisonerIepLevel, "Enhanced"),
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

  @Nested
  inner class AddIepMigration {

    private val migrationRequest = syncPostRequest(iepLevelCode = "STD", reviewType = ReviewType.MIGRATED)

    @Test
    fun `process migration`(): Unit = runBlocking {
      // Given
      val bookingId = 1234567L
      whenever(prisonApiService.getPrisonerInfo(bookingId)).thenReturn(prisonerAtLocation())
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }

      // When
      prisonerIepLevelReviewService.persistPrisonerIepLevel(bookingId, migrationRequest)

      // Then
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = migrationRequest.iepLevel,
          commentText = migrationRequest.comment,
          bookingId = bookingId,
          prisonId = migrationRequest.prisonId,
          locationId = migrationRequest.locationId,
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
      whenever(prisonApiService.getPrisonerInfo(bookingId)).thenReturn(prisonerAtLocation())
      whenever(prisonerIepLevelRepository.save(any())).thenAnswer { i -> i.arguments[0] }

      // When
      prisonerIepLevelReviewService.persistPrisonerIepLevel(bookingId, migrationRequestWithNullUserId)

      // Then
      verify(prisonerIepLevelRepository, times(1)).save(
        PrisonerIepLevel(
          iepCode = migrationRequestWithNullUserId.iepLevel,
          commentText = migrationRequestWithNullUserId.comment,
          bookingId = bookingId,
          prisonId = migrationRequestWithNullUserId.prisonId,
          locationId = migrationRequestWithNullUserId.locationId,
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
      locationId = syncPostRequest.locationId,
      current = syncPostRequest.current,
      reviewedBy = syncPostRequest.userId,
      reviewTime = syncPostRequest.iepTime,
      reviewType = syncPostRequest.reviewType,
      prisonerNumber = offenderNo,
    )

    private val iepDetail = IepDetail(
      id = iepReviewId,
      iepLevel = iepLevelDescription,
      comments = syncPostRequest.comment,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      iepDate = syncPostRequest.iepTime.toLocalDate(),
      iepTime = syncPostRequest.iepTime,
      agencyId = syncPostRequest.prisonId,
      locationId = syncPostRequest.locationId,
      userId = syncPostRequest.userId,
      reviewType = syncPostRequest.reviewType,
      auditModuleName = "Incentives-API",
    )

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      // Mock Prison API getPrisonerInfo() response
      whenever(prisonApiService.getPrisonerInfo(bookingId)).thenReturn(
        prisonerAtLocation(bookingId, offenderNo)
      )

      // Mock IEP level query
      whenever(iepLevelRepository.findById("ENH")).thenReturn(
        IepLevel(iepCode = iepLevelCode, iepDescription = iepLevelDescription, sequence = 3, active = true),
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
      verify(snsService, times(1)).sendIepReviewEvent(iepReviewId, syncPostRequest.iepTime)

      // audit message is sent
      verify(auditService, times(1))
        .sendMessage(
          AuditType.IEP_REVIEW_ADDED,
          "$iepReviewId",
          iepDetail,
          syncPostRequest.userId,
        )
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
        agencyId = "MDI",
        iepLevel = "Enhanced",
        iepDate = iepTime.toLocalDate(),
        iepTime = iepTime,
        userId = "TEST_USER",
        auditModuleName = "PRISON_API"
      ),
      IepDetail(
        bookingId = 1L,
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

  private fun prisonerAtLocation(bookingId: Long = 1234567, offenderNo: String = "A1234AA", agencyId: String = "MDI") = PrisonerAtLocation(
    bookingId, 1, "John", "Smith", offenderNo, agencyId, 1
  )

  private val location = Location(
    agencyId = "MDI", locationId = 77777L, description = "Houseblock 1"
  )

  private val basicStandardEnhanced = listOf(
    IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1, default = false),
    IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2, default = true),
    IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3, default = false),
  )

  private fun syncPostRequest(iepLevelCode: String = "STD", reviewType: ReviewType) = SyncPostRequest(
    iepTime = LocalDateTime.now(),
    prisonId = "MDI",
    locationId = "1-2-003",
    iepLevel = iepLevelCode,
    comment = "A comment",
    userId = "XYZ_GEN",
    reviewType = reviewType,
    current = true,
  )

  private fun iepDetailFromIepLevel(prisonerIepLevel: PrisonerIepLevel, iepDescription: String) =
    IepDetail(
      id = 0,
      iepLevel = iepDescription,
      comments = prisonerIepLevel.commentText,
      bookingId = prisonerIepLevel.bookingId,
      agencyId = prisonerIepLevel.prisonId,
      locationId = prisonerIepLevel.locationId,
      userId = prisonerIepLevel.reviewedBy,
      iepDate = prisonerIepLevel.reviewTime.toLocalDate(),
      iepTime = prisonerIepLevel.reviewTime,
      reviewType = prisonerIepLevel.reviewType,
      prisonerNumber = prisonerIepLevel.prisonerNumber,
      auditModuleName = "Incentives-API",
    )
}
