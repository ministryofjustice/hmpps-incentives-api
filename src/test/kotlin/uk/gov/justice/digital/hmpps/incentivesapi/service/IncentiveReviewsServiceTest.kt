package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IncentiveReviewsServiceTest {
  private val prisonApiService: PrisonApiService = mock()
  private val iepLevelService: IepLevelService = mock()
  private val offenderSearchService: OffenderSearchService = mock()
  private val behaviourService: BehaviourService = mock()
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val incentiveReviewsService = IncentiveReviewsService(offenderSearchService, prisonApiService, iepLevelService, prisonerIepLevelRepository, nextReviewDateGetterService, behaviourService, clock)

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock retrieveCaseNoteCounts
    whenever(behaviourService.getBehaviours(anyList())).thenReturn(BehaviourSummary(emptyMap(), emptyMap()))

    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110002, iepCode = "BAS", current = true, reviewType = ReviewType.REVIEW, reviewTime = LocalDateTime.now(clock).minusMonths(1)),
          prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = false, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock).minusMonths(2)),
        ),
      )
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
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

    whenever(iepLevelService.getIepLevelsForPrison("MDI", useClientCredentials = true))
      .thenReturn(
        listOf(
          IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
          IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
          IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).plusYears(1),
      110002L to LocalDate.now(clock).plusYears(1),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    verify(offenderSearchService, times(1)).findOffenders(any(), eq("MDI-2-1"))
    assertThat(reviews.locationDescription).isEqualTo("A houseblock")
    val reviewCount = reviews.levels.find { level -> level.levelCode == "STD" }?.reviewCount
    assertThat(reviewCount).isEqualTo(2)
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
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
        IncentiveReview(
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(offenderSearchPrisoner(prisonerNumber))
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
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
        IncentiveReview(
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

    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(listOf(offenderSearchPrisoner(prisonerNumber)))

    whenever(nextReviewDateGetterService.getMany(any())).thenReturn(mapOf(110002L to expectedNextReviewDate))

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
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

    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110002, iepCode = "ENH", current = true, reviewType = ReviewType.REVIEW, reviewTime = LocalDateTime.now(clock).minusMonths(1)),
          prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = false, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110003, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
        ),
      )

    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
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
        IncentiveReview(
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
        IncentiveReview(
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
      whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD")

      // Then
      assertThat(reviews.reviews).isEqualTo(
        listOf(
          IncentiveReview(
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
          IncentiveReview(
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
      whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
      whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

      // When
      val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1", "STD", order = Sort.Direction.DESC)

      // Then
      assertThat(reviews.reviews).isSortedAccordingTo(compareByDescending(IncentiveReview::nextReviewDate))
    }

    @Test
    fun `can sort by non-default parameters`(): Unit = runBlocking {
      // Given
      whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001L),
        offenderSearchPrisoner("G6123VU", 110002L),
      )
      whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
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
      assertThat(reviews.reviews).isSortedAccordingTo(compareByDescending(IncentiveReview::prisonerNumber))
    }
  }

  @Test
  fun `throw exception if cannot find incentive level one bookingId`(): Unit = runBlocking {
    // Given - we only have prisonerIepLevel records for 110001
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(
      listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
      ),
    )
    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
        ),
      )
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(eq(listOf(110001, 110002))))
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(
      listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
      ),
    )

    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(emptyFlow())

    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(eq(listOf(110001, 110002))))
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001),
      offenderSearchPrisoner("G6123VU", 110002),
      offenderSearchPrisoner("G6123VX", 110003),
    )
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110003, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
        ),
      )
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
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
      whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
      val offenders = listOf(
        offenderSearchPrisoner("A1409AE", 110001),
        offenderSearchPrisoner("G6123VU", 110002),
        offenderSearchPrisoner("G6123VX", 110003),
      )
      whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
      whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any())).thenReturn(emptyFlow())

      whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001),
      offenderSearchPrisoner("G6123VU", 110002),
      offenderSearchPrisoner("G6123VX", 110003),
    )
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(offenders)
    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(any()))
      .thenReturn(
        flowOf(
          prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.REVIEW, reviewTime = LocalDateTime.now(clock).minusMonths(1)),
          prisonerIepLevel(bookingId = 110001, iepCode = "BAS", current = false, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110002, iepCode = "BAS", current = true, reviewType = ReviewType.TRANSFER, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110002, iepCode = "BAS", current = false, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
          prisonerIepLevel(bookingId = 110003, iepCode = "ENH", current = true, reviewType = ReviewType.REVIEW, reviewTime = LocalDateTime.now(clock).minusMonths(1)),
          prisonerIepLevel(bookingId = 110003, iepCode = "STD", current = false, reviewType = ReviewType.REVIEW, reviewTime = LocalDateTime.now(clock).minusMonths(2)),
          prisonerIepLevel(bookingId = 110003, iepCode = "BAS", current = false, reviewType = ReviewType.INITIAL, reviewTime = LocalDateTime.now(clock)),
        ),
      )

    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any()))
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
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any())).thenReturn(
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

  private fun OngoingStubbing<PrisonLocation>.thenReturnLocation(cellLocationPrefix: String) {
    val (prisonId, locationPrefix) = cellLocationPrefix.split("-", limit = 2)
    thenReturn(
      PrisonLocation(
        agencyId = prisonId,
        locationPrefix = locationPrefix,
        description = "A houseblock",
        locationType = "WING",
        userDescription = null,
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
