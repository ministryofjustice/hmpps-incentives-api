package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.incentivesapi.config.ListOfDataNotFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview as IncentiveReviewDTO

@DisplayName("Incentive reviews service")
class IncentiveReviewsServiceTest {
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService = mock()
  private val offenderSearchService: OffenderSearchService = mock()
  private val locationsService: LocationsService = mock()
  private val behaviourService: BehaviourService = mock()
  private val incentiveReviewRepository: IncentiveReviewRepository = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val incentiveReviewsService =
    IncentiveReviewsService(
      offenderSearchService,
      locationsService,
      prisonIncentiveLevelService,
      incentiveReviewRepository,
      nextReviewDateGetterService,
      behaviourService,
      clock,
    )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock retrieveCaseNoteCounts
    whenever(behaviourService.getBehaviours(anyList())).thenReturn(BehaviourSummary(emptyMap(), emptyMap()))

    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "BAS",
            current = true,
            reviewType = ReviewType.REVIEW,
            reviewTime = LocalDateTime.now(clock).minusMonths(1),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "STD",
            current = false,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock).minusMonths(2),
          ),
        ),
      )
    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(110001, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110002, reviewTime = LocalDateTime.now(clock)),
        ),
      )

    whenever(nextReviewDateGetterService.getMany(any())).thenReturn(
      mapOf(
        110001L to LocalDate.parse("2022-12-12"),
        110002L to LocalDate.parse("2022-12-12"),
      ),
    )

    whenever(prisonIncentiveLevelService.getActivePrisonIncentiveLevels("MDI"))
      .thenReturn(
        listOf(
          PrisonIncentiveLevel(
            prisonId = "MDI",
            levelCode = "BAS",
            levelName = "Basic",
            remandTransferLimitInPence = 27_50,
            remandSpendLimitInPence = 275_00,
            convictedTransferLimitInPence = 5_50,
            convictedSpendLimitInPence = 55_00,
            visitOrders = 1,
            privilegedVisitOrders = 0,
          ),
          PrisonIncentiveLevel(
            prisonId = "MDI",
            levelCode = "STD",
            levelName = "Standard",
            defaultOnAdmission = true,
            remandTransferLimitInPence = 60_50,
            remandSpendLimitInPence = 605_00,
            convictedTransferLimitInPence = 19_80,
            convictedSpendLimitInPence = 198_00,
            visitOrders = 1,
            privilegedVisitOrders = 0,
          ),
          PrisonIncentiveLevel(
            prisonId = "MDI",
            levelCode = "ENH",
            levelName = "Enhanced",
            remandTransferLimitInPence = 66_00,
            remandSpendLimitInPence = 660_00,
            convictedTransferLimitInPence = 33_00,
            convictedSpendLimitInPence = 330_00,
            visitOrders = 1,
            privilegedVisitOrders = 0,
          ),
        ),
      )
  }

  @Test
  fun `maps responses from offender search`(): Unit = runBlocking {
    val offenders = listOf(
      OffenderSearchPrisoner(
        prisonerNumber = "A1409AE",
        bookingId = 110001,
        firstName = "JAMES",
        middleNames = "",
        lastName = "HALLS",
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = "MDI",
        alerts = listOf(
          PrisonerAlert(
            alertType = "H",
            alertCode = "HA",
            active = true,
            expired = false,
          ),
        ),
      ),
      OffenderSearchPrisoner(
        prisonerNumber = "G6123VU",
        bookingId = 110002,
        firstName = "RHYS",
        middleNames = "BARRY",
        lastName = "JONES",
        dateOfBirth = LocalDate.parse("1970-03-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = "MDI",
      ),
    )
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2")
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).plusYears(1),
      110002L to LocalDate.now(clock).plusYears(1),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-", "STD")

    verify(offenderSearchService, times(1)).getOffendersAtLocation(any(), eq("MDI-2-"))
    verify(locationsService, times(1)).getByKey(eq("MDI-2"))
    assertThat(reviews.locationDescription).isEqualTo("A houseblock")
    val reviewCount = reviews.levels.find { level -> level.levelCode == "STD" }?.reviewCount
    assertThat(reviewCount).isEqualTo(2)
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReviewDTO(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "James",
          lastName = "Halls",
          levelCode = "STD",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          hasAcctOpen = true,
          isNewToPrison = true,
          nextReviewDate = nextReviewDatesMap[110001L]!!,
          daysSinceLastReview = null,
        ),
        IncentiveReviewDTO(
          prisonerNumber = "G6123VU",
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          levelCode = "STD",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          hasAcctOpen = false,
          isNewToPrison = true,
          nextReviewDate = nextReviewDatesMap[110002L]!!,
          daysSinceLastReview = null,
        ),
      ),
    )
  }

  @Test
  fun `maps case notes from prison api`(): Unit = runBlocking {
    // Given
    val prisonerNumber = "G6123VU"
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(offenderSearchPrisoner(prisonerNumber))
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
    val nextReviewDatesMap = mapOf(offenders[0].bookingId to LocalDate.now(clock).plusYears(1))
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    whenever(behaviourService.getBehaviours(anyList()))
      .thenReturn(
        BehaviourSummary(
          mapOf(
            BookingTypeKey(bookingId = 110002L, caseNoteType = "POS")
              to CaseNoteSummary(
                key = BookingTypeKey(bookingId = 110002L, caseNoteType = "POS"),
                totalCaseNotes = 5,
                numSubTypeCount = 5,
              ),
            BookingTypeKey(bookingId = 110002L, caseNoteType = "NEG")
              to CaseNoteSummary(
                key = BookingTypeKey(bookingId = 110002L, caseNoteType = "NEG"),
                totalCaseNotes = 7,
                numSubTypeCount = 7,
              ),
          ),
          mapOf(110002L to LocalDateTime.now(clock).minusMonths(1)),
        ),
      )

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReviewDTO(
          prisonerNumber = prisonerNumber,
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          levelCode = "STD",
          positiveBehaviours = 5,
          negativeBehaviours = 7,
          hasAcctOpen = false,
          isNewToPrison = false,
          nextReviewDate = nextReviewDatesMap[110002L]!!,
          daysSinceLastReview = 31,
        ),
      ),
    )
  }

  @Test
  fun `defaults positive and negative behaviours to 0 if no case notes returned`(): Unit = runBlocking {
    // Given
    val prisonerNumber = "G6123VU"
    val expectedNextReviewDate = LocalDate.now(clock).plusYears(1)

    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    whenever(
      offenderSearchService.getOffendersAtLocation(any(), any()),
    ).thenReturn(listOf(offenderSearchPrisoner(prisonerNumber)))

    whenever(nextReviewDateGetterService.getMany(any())).thenReturn(mapOf(110002L to expectedNextReviewDate))

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReviewDTO(
          prisonerNumber = prisonerNumber,
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          levelCode = "STD",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          hasAcctOpen = false,
          isNewToPrison = true,
          nextReviewDate = expectedNextReviewDate,
          daysSinceLastReview = null,
        ),
      ),
    )
  }

  @Test
  fun `filters reviews by level`(): Unit = runBlocking {
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001),
      offenderSearchPrisoner("G6123VU", 110002),
      offenderSearchPrisoner("G6123VX", 110003),
    )
    val someFutureNextReviewDate = LocalDate.now(clock).plusYears(1)

    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "ENH",
            current = true,
            reviewType = ReviewType.REVIEW,
            reviewTime = LocalDateTime.now(clock).minusMonths(1),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "STD",
            current = false,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110003,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
        ),
      )

    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(110001, iepCode = "STD", reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110002, iepCode = "ENH", reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110003, iepCode = "STD", reviewTime = LocalDateTime.now(clock)),
        ),
      )
    whenever(nextReviewDateGetterService.getMany(offenders))
      .thenReturn(
        mapOf(
          110001L to someFutureNextReviewDate,
          110002L to someFutureNextReviewDate,
          110003L to someFutureNextReviewDate,
        ),
      )

    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    val reviewCount = reviews.levels.find { level -> level.levelCode == "STD" }?.reviewCount
    assertThat(reviewCount).isEqualTo(2)
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReviewDTO(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "Rhys",
          lastName = "Jones",
          levelCode = "STD",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          hasAcctOpen = false,
          isNewToPrison = true,
          nextReviewDate = someFutureNextReviewDate,
          daysSinceLastReview = null,
        ),
        IncentiveReviewDTO(
          prisonerNumber = "G6123VX",
          bookingId = 110003,
          firstName = "Rhys",
          lastName = "Jones",
          levelCode = "STD",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          hasAcctOpen = false,
          isNewToPrison = true,
          nextReviewDate = someFutureNextReviewDate,
          daysSinceLastReview = null,
        ),
      ),
    )
  }

  @DisplayName("sorting and filtering")
  @Nested
  inner class SortingAndFiltering {
    private val oldestReview = LocalDateTime.now(clock).minusDays(5)
    private val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).plusYears(1),
      110002L to oldestReview.toLocalDate().plusYears(1),
    )

    @Test
    fun `oldest date of next review is first by default`(): Unit = runBlocking {
      // Given
      whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

      // Then
      assertThat(reviews.reviews).isEqualTo(
        listOf(
          IncentiveReviewDTO(
            prisonerNumber = "G6123VU",
            bookingId = 110002,
            firstName = "Rhys",
            lastName = "Jones",
            levelCode = "STD",
            positiveBehaviours = 0,
            negativeBehaviours = 0,
            hasAcctOpen = false,
            isNewToPrison = true,
            nextReviewDate = nextReviewDatesMap[110002L]!!,
            daysSinceLastReview = null,
          ),
          IncentiveReviewDTO(
            prisonerNumber = "A1409AE",
            bookingId = 110001,
            firstName = "Rhys",
            lastName = "Jones",
            levelCode = "STD",
            positiveBehaviours = 0,
            negativeBehaviours = 0,
            hasAcctOpen = false,
            isNewToPrison = true,
            nextReviewDate = nextReviewDatesMap[110001L]!!,
            daysSinceLastReview = null,
          ),
        ),
      )
    }

    @Test
    fun `can reverse order by date of next review`(): Unit = runBlocking {
      // Given
      whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD", order = Sort.Direction.DESC)

      // Then
      assertThat(reviews.reviews).isSortedAccordingTo(compareByDescending(IncentiveReviewDTO::nextReviewDate))
    }

    @Test
    fun `can sort by non-default parameters`(): Unit = runBlocking {
      // Given
      whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews(
        "MDI",
        "MDI-2-1",
        "STD",
        sort = IncentiveReviewSort.PRISONER_NUMBER,
        order = Sort.Direction.DESC,
      )

      // Then
      assertThat(reviews.reviews).isSortedAccordingTo(compareByDescending(IncentiveReviewDTO::prisonerNumber))
    }
  }

  @Test
  fun `throw exception if cannot find incentive level one bookingId`(): Unit = runBlocking {
    // Given - we only have prisonerIepLevel records for 110001
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(
      listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
      ),
    )
    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
        ),
      )
    whenever(
      incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(eq(listOf(110001, 110002))),
    )
      .thenReturn(flowOf(prisonerIepLevel(110001, reviewTime = LocalDateTime.now(clock))))

    // When
    assertThatThrownBy {
      runBlocking { incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD") }
    }.isInstanceOf(ListOfDataNotFoundException::class.java)
      .hasMessage("No incentive levels found for ID(s) [110002]")
  }

  @Test
  fun `throw exception if cannot find incentive levels all bookingIds`(): Unit = runBlocking {
    // Given - we don't have prisonerIepLevel records for either bookingId
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(
      listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
      ),
    )

    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    whenever(
      incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(eq(listOf(110001, 110002))),
    )
      .thenReturn(emptyFlow())

    // When
    assertThatThrownBy {
      runBlocking { incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD") }
    }.isInstanceOf(ListOfDataNotFoundException::class.java)
      .hasMessage("No incentive levels found for ID(s) [110001, 110002]")
  }

  @Test
  fun `overdue count where 2 next review are in the past`(): Unit = runBlocking {
    // Given
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001),
      offenderSearchPrisoner("G6123VU", 110002),
      offenderSearchPrisoner("G6123VX", 110003),
    )
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110003,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
        ),
      )
    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(110001, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110002, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110003, reviewTime = LocalDateTime.now(clock)),
        ),
      )
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock),
      // next review will be 1 day before LocalDateTime.now(clock)
      110002L to LocalDate.now(clock).minusYears(1).minusDays(1),
      // next review will be 10 days before LocalDateTime.now(clock)
      110003L to LocalDate.now(clock).minusYears(1).minusDays(10),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    // Then
    val overdueCount = reviews.levels.sumOf { level -> level.overdueCount }
    assertThat(overdueCount).isEqualTo(2)
    assertThat(reviews.reviews).hasSize(3)
  }

  @Test
  fun `overdue count where 3 next review are in the past but on different levels`() {
    runBlocking {
      // Given
      whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
        offenderSearchPrisoner("G6123VX", 110003),
      )
      whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
      whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any())).thenReturn(emptyFlow())

      whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
        .thenReturn(
          flowOf(
            prisonerIepLevel(110001, iepCode = "STD", reviewTime = LocalDateTime.now(clock)),
            prisonerIepLevel(110002, iepCode = "BAS", reviewTime = LocalDateTime.now(clock)),
            prisonerIepLevel(110003, iepCode = "ENH", reviewTime = LocalDateTime.now(clock)),
          ),
        )
      val nextReviewDatesMap = mapOf(
        110001L to LocalDate.now(clock).minusYears(1),
        // next review will be 1 day before LocalDateTime.now(clock)
        110002L to LocalDate.now(clock).minusYears(1),
        // next review will be 10 days before LocalDateTime.now(clock)
        110003L to LocalDate.now(clock).minusYears(1),
      )
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

      // Then
      val overdueCount = reviews.levels.sumOf { level -> level.overdueCount }
      assertThat(overdueCount).isEqualTo(3)
      assertThat(reviews.reviews).hasSize(1)
    }
  }

  @Test
  fun `overdue count where 3 next review are in the past but not all in page of results`(): Unit = runBlocking {
    // Given
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001),
      offenderSearchPrisoner("G6123VU", 110002),
      offenderSearchPrisoner("G6123VX", 110003),
    )
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(offenders)
    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "STD",
            current = true,
            reviewType = ReviewType.REVIEW,
            reviewTime = LocalDateTime.now(clock).minusMonths(1),
          ),
          prisonerIepLevel(
            bookingId = 110001,
            iepCode = "BAS",
            current = false,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "BAS",
            current = true,
            reviewType = ReviewType.TRANSFER,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110002,
            iepCode = "BAS",
            current = false,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
          prisonerIepLevel(
            bookingId = 110003,
            iepCode = "ENH",
            current = true,
            reviewType = ReviewType.REVIEW,
            reviewTime = LocalDateTime.now(clock).minusMonths(1),
          ),
          prisonerIepLevel(
            bookingId = 110003,
            iepCode = "STD",
            current = false,
            reviewType = ReviewType.REVIEW,
            reviewTime = LocalDateTime.now(clock).minusMonths(2),
          ),
          prisonerIepLevel(
            bookingId = 110003,
            iepCode = "BAS",
            current = false,
            reviewType = ReviewType.INITIAL,
            reviewTime = LocalDateTime.now(clock),
          ),
        ),
      )

    whenever(incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(110001, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110002, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(110003, reviewTime = LocalDateTime.now(clock)),
        ),
      )
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).minusYears(1),
      // next review will be 1 day before LocalDateTime.now(clock)
      110002L to LocalDate.now(clock).minusYears(1),
      // next review will be 10 days before LocalDateTime.now(clock)
      110003L to LocalDate.now(clock).minusYears(1),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD", page = 0, size = 1)

    // Then
    val overdueCount = reviews.levels.sumOf { level -> level.overdueCount }
    assertThat(overdueCount).isEqualTo(3)
    assertThat(reviews.reviews).hasSize(1)
  }

  @Test
  fun `overdue count where no next reviews are in the past`(): Unit = runBlocking {
    // Given
    whenever(locationsService.getByKey(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.getOffendersAtLocation(any(), any())).thenReturn(
      listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
      ),
    )

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    // Then
    val overdueCount = reviews.levels.sumOf { level -> level.overdueCount }
    assertThat(overdueCount).isEqualTo(0)
    assertThat(reviews.reviews).hasSize(2)
  }

  private fun OngoingStubbing<Location>.thenReturnLocation(cellLocationPrefix: String) {
    val (prisonId, locationPrefix) = cellLocationPrefix.split("-", limit = 2)
    thenReturn(
      Location(
        id = "2475f250-434a-4257-afe7-b911f1773a4d",
        key = cellLocationPrefix,
        prisonId = prisonId,
        localName = "A houseblock",
        pathHierarchy = locationPrefix,
      ),
    )
  }

  private fun offenderSearchPrisoner(prisonerNumber: String, bookingId: Long = 110002) = OffenderSearchPrisoner(
    prisonerNumber = prisonerNumber,
    bookingId = bookingId,
    firstName = "RHYS",
    middleNames = "BARRY",
    lastName = "JONES",
    dateOfBirth = LocalDate.parse("1970-03-01"),
    receptionDate = LocalDate.parse("2020-07-01"),
    prisonId = "MDI",
  )
}
