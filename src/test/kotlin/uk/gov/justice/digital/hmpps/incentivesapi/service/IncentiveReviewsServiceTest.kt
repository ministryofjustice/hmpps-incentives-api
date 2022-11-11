package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsage
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IncentiveReviewsServiceTest {
  private val prisonApiService: PrisonApiService = mock()
  private val offenderSearchService: OffenderSearchService = mock()
  private val nextReviewDateGetterService: NextReviewDateGetterService = mock()
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.systemDefault())
  private val incentiveReviewsService = IncentiveReviewsService(offenderSearchService, prisonApiService, nextReviewDateGetterService, clock)

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    // Fixes tests which do not explicitly mock retrieveCaseNoteCounts
    whenever(prisonApiService.retrieveCaseNoteCounts(any(), any())).thenReturn(emptyFlow())

    whenever(
      nextReviewDateGetterService.getMany(any())
    ).thenReturn(
      mapOf(
        110001L to LocalDate.parse("2022-12-12"),
        110002L to LocalDate.parse("2022-12-12"),
      )
    )
  }

  @Test
  fun `defaults to page 1, size 20`(): Unit = runBlocking {
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-1-2")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders()

    incentiveReviewsService.reviews("MDI", "MDI-1-2")
    verify(offenderSearchService, times(1)).findOffenders("MDI", "MDI-1-2", 0, 20)
  }

  @Test
  fun `accepts different pagination params`(): Unit = runBlocking {
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-1-2")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders()

    incentiveReviewsService.reviews("MDI", "MDI-1-2", 2, 40)
    verify(offenderSearchService, times(1)).findOffenders("MDI", "MDI-1-2", 1, 40)
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
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = "MDI",
        prisonName = "Moorland (HMP & YOI)",
        cellLocation = "2-1-002",
        locationDescription = "Moorland (HMP & YOI)",
        alerts = listOf(
          OffenderSearchPrisonerAlert(
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
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1970-03-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = "MDI",
        prisonName = "Moorland (HMP & YOI)",
        cellLocation = "2-1-003",
        locationDescription = "Moorland (HMP & YOI)",
        alerts = listOf(),
      ),
    )
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders(offenders)
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).plusYears(1),
      110002L to LocalDate.now(clock).plusYears(1),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    verify(offenderSearchService, times(1)).findOffenders(any(), eq("MDI-2-1"), any(), any())
    assertThat(reviews.locationDescription).isEqualTo("A houseblock")
    assertThat(reviews.reviewCount).isEqualTo(2)
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "James",
          lastName = "Halls",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          acctOpenStatus = true,
          nextReviewDate = nextReviewDatesMap[110001L]!!,
        ),
        IncentiveReview(
          prisonerNumber = "G6123VU",
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          acctOpenStatus = false,
          nextReviewDate = nextReviewDatesMap[110002L]!!,
        ),
      )
    )
  }

  @Test
  fun `maps case notes from prison api`(): Unit = runBlocking {
    // Given
    val prisonerNumber = "G6123VU"
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(offenderSearchPrisoner(prisonerNumber))
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any()))
      .thenReturnOffenders(offenders)
    val nextReviewDatesMap = mapOf(offenders[0].bookingId to LocalDate.now(clock).plusYears(1))
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    whenever(prisonApiService.retrieveCaseNoteCounts("POS", listOf(prisonerNumber)))
      .thenReturn(
        flowOf(
          CaseNoteUsage(
            offenderNo = prisonerNumber,
            caseNoteType = "POS",
            caseNoteSubType = "IEP_ENC",
            numCaseNotes = 5,
            latestCaseNote = null,
          )
        )
      )
    whenever(prisonApiService.retrieveCaseNoteCounts("NEG", listOf(prisonerNumber)))
      .thenReturn(
        flowOf(
          CaseNoteUsage(
            offenderNo = prisonerNumber,
            caseNoteType = "NEG",
            caseNoteSubType = "IEP_WARN",
            numCaseNotes = 7,
            latestCaseNote = null,
          )
        )
      )

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
          prisonerNumber = prisonerNumber,
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          positiveBehaviours = 5,
          negativeBehaviours = 7,
          acctOpenStatus = false,
          nextReviewDate = nextReviewDatesMap[110002L]!!,
        ),
      )
    )
  }

  @Test
  fun `defaults positive and negative behaviours to 0 if no case notes returned`(): Unit = runBlocking {
    // Given
    val prisonerNumber = "G6123VU"
    val expectedNextReviewDate = LocalDate.now(clock).plusYears(1)

    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any()))
      .thenReturnOffenders(listOf(offenderSearchPrisoner(prisonerNumber)))

    whenever(prisonApiService.retrieveCaseNoteCounts("POS", listOf(prisonerNumber))).thenReturn(emptyFlow())
    whenever(prisonApiService.retrieveCaseNoteCounts("NEG", listOf(prisonerNumber))).thenReturn(emptyFlow())

    whenever(nextReviewDateGetterService.getMany(any())).thenReturn(mapOf(110002L to expectedNextReviewDate))

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
          prisonerNumber = prisonerNumber,
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          acctOpenStatus = false,
          nextReviewDate = expectedNextReviewDate,
        ),
      )
    )
  }

  @Test
  fun `oldest date of next review is first`(): Unit = runBlocking {
    // Given
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    val offenders = listOf(
      offenderSearchPrisoner("A1409AE", 110001L),
      offenderSearchPrisoner("G6123VU", 110002L),
    )
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any()))
      .thenReturnOffenders(offenders)

    val oldestReview = LocalDateTime.now(clock).minusDays(5)
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock).plusYears(1),
      110002L to oldestReview.toLocalDate().plusYears(1),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    // Then
    assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
          prisonerNumber = "G6123VU",
          bookingId = 110002,
          firstName = "Rhys",
          lastName = "Jones",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          acctOpenStatus = false,
          nextReviewDate = nextReviewDatesMap[110002L]!!,
        ),
        IncentiveReview(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "Rhys",
          lastName = "Jones",
          positiveBehaviours = 0,
          negativeBehaviours = 0,
          acctOpenStatus = false,
          nextReviewDate = nextReviewDatesMap[110001L]!!,
        ),
      )
    )
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
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any()))
      .thenReturnOffenders(offenders)
    val nextReviewDatesMap = mapOf(
      110001L to LocalDate.now(clock),
      // next review will be 1 day before LocalDateTime.now(clock)
      110002L to LocalDate.now(clock).minusYears(1).minusDays(1),
      // next review will be 10 days before LocalDateTime.now(clock)
      110003L to LocalDate.now(clock).minusYears(1).minusDays(10),
    )
    whenever(nextReviewDateGetterService.getMany(offenders)).thenReturn(nextReviewDatesMap)

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    // Then
    assertThat(reviews.overdueCount).isEqualTo(2)
  }

  @Test
  fun `overdue count where no next reviews are in the past`(): Unit = runBlocking {
    // Given
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any()))
      .thenReturnOffenders(
        listOf(
          offenderSearchPrisoner("A1409AE", 110001),
          offenderSearchPrisoner("G6123VU", 110002),
        )
      )

    // When
    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    // Then
    assertThat(reviews.overdueCount).isEqualTo(0)
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
      )
    )
  }

  private fun OngoingStubbing<OffenderSearchPrisonerList>.thenReturnOffenders(offenders: List<OffenderSearchPrisoner> = emptyList()) {
    thenReturn(
      OffenderSearchPrisonerList(
        totalElements = offenders.size,
        content = offenders,
      )
    )
  }

  private fun offenderSearchPrisoner(prisonerNumber: String, bookingId: Long = 110002) = OffenderSearchPrisoner(
    prisonerNumber = prisonerNumber,
    bookingId = bookingId,
    firstName = "RHYS",
    middleNames = "BARRY",
    lastName = "JONES",
    status = "ACTIVE IN",
    inOutStatus = "IN",
    dateOfBirth = LocalDate.parse("1970-03-01"),
    receptionDate = LocalDate.parse("2020-07-01"),
    prisonId = "MDI",
    prisonName = "Moorland (HMP & YOI)",
    cellLocation = "2-1-003",
    locationDescription = "Moorland (HMP & YOI)",
    alerts = listOf(),
  )

  private fun prisonerIepLevel(bookingId: Long, reviewTime: LocalDateTime = LocalDateTime.now(clock)) = PrisonerIepLevel(
    iepCode = "STD",
    prisonId = "MDI",
    locationId = "MDI-1-1-004",
    bookingId = bookingId,
    current = true,
    reviewedBy = "TEST_STAFF1",
    reviewTime = reviewTime,
    prisonerNumber = "A1234AB"
  )
}
