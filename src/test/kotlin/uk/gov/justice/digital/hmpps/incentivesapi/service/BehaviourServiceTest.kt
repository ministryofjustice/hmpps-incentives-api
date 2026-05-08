package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("Behaviour service")
class BehaviourServiceTest {
  private val caseNotesApiService: CaseNotesApiService = mock()
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val behaviourService = BehaviourService(caseNotesApiService, clock)

  private val timeNow = LocalDateTime.now(clock)

  @Test
  fun `case note totals correct map against review dates`() {
    val reviews = listOf(
      prisonerIepLevel(
        bookingId = 110001,
        prisonerNumber = "110001",
        iepCode = "STD",
        current = true,
        reviewType = ReviewType.TRANSFER,
        reviewTime = timeNow.minusMonths(1),
      ),
      // real review
      prisonerIepLevel(
        bookingId = 110001,
        prisonerNumber = "110001",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.REVIEW,
        reviewTime = timeNow.minusMonths(2),
      ),
      prisonerIepLevel(
        bookingId = 110001,
        prisonerNumber = "110001",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(3),
      ),

      // real review
      prisonerIepLevel(
        bookingId = 110002,
        prisonerNumber = "110002",
        iepCode = "ENH",
        current = true,
        reviewType = ReviewType.REVIEW,
        reviewTime = timeNow.minusMonths(1),
      ),
      prisonerIepLevel(
        bookingId = 110002,
        prisonerNumber = "110002",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.TRANSFER,
        reviewTime = timeNow.minusMonths(2),
      ),
      prisonerIepLevel(
        bookingId = 110002,
        prisonerNumber = "110002",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.REVIEW,
        reviewTime = timeNow.minusMonths(3),
      ),
      prisonerIepLevel(
        bookingId = 110002,
        prisonerNumber = "110002",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(4),
      ),

      // presumed to be real review
      prisonerIepLevel(
        bookingId = 110003,
        prisonerNumber = "110003",
        iepCode = "STD",
        current = true,
        reviewType = ReviewType.MIGRATED,
        reviewTime = timeNow.minusMonths(14),
      ),

      prisonerIepLevel(
        bookingId = 110004,
        prisonerNumber = "110004",
        iepCode = "STD",
        current = true,
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(1),
      ),

      // real review
      prisonerIepLevel(
        bookingId = 110005,
        prisonerNumber = "110005",
        iepCode = "BAS",
        current = true,
        reviewType = ReviewType.REVIEW,
        reviewTime = timeNow.minusMonths(5),
      ),
      prisonerIepLevel(
        bookingId = 110005,
        prisonerNumber = "110005",
        iepCode = "STD",
        current = false,
        reviewType = ReviewType.MIGRATED,
        reviewTime = timeNow.minusMonths(8),
      ),
    )
    // dates from above of last real review
    val prisonerByLastRealReviewDate = mapOf(
      110001L to timeNow.minusMonths(2),
      110002L to timeNow.minusMonths(1),
      110003L to timeNow.minusMonths(14),
      110004L to null,
      110005L to timeNow.minusMonths(5),
    )
    // filters that would be used to call prison-api
    val prisonerByLastReviewDateOrDefaultPeriod = mapOf(
      110001L to timeNow.minusMonths(2),
      110002L to timeNow.minusMonths(1),
      110003L to timeNow.minusMonths(3),
      110004L to timeNow.minusMonths(3),
      110005L to timeNow.minusMonths(3),
    )

    runBlocking {
      val typeSubType = setOf(TypeSubTypeRequest(type = "POS"), TypeSubTypeRequest(type = "NEG"))
      whenever(
        caseNotesApiService.retrieveCaseNoteCountsByFromDate(
          typeSubType,
          setOf("110001"),
          prisonerByLastReviewDateOrDefaultPeriod[110001L]!!,
        ),
      ).thenReturn(
        flowOf(
          NoteUsageResponse(
            mapOf(
              "110001" to listOf(
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110001",
                  type = "POS",
                  subType = "IEP_ENC",
                  count = 2,
                ),
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110001",
                  type = "POS",
                  subType = "QUAL_ATT",
                  count = 1,
                ),
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110001",
                  type = "POS",
                  subType = "POS_GEN",
                  count = 1,
                ),
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110001",
                  type = "NEG",
                  subType = "IEP_WARN",
                  count = 1,
                ),
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110001",
                  type = "NEG",
                  subType = "BEHAVEWARN",
                  count = 1,
                ),
              ),
            ),
          ),
        ),
      )

      whenever(
        caseNotesApiService.retrieveCaseNoteCountsByFromDate(
          typeSubType,
          setOf("110002"),
          prisonerByLastReviewDateOrDefaultPeriod[110002L]!!,
        ),
      ).thenReturn(
        flowOf(
          NoteUsageResponse(
            mapOf(
              "110002" to listOf(
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110002",
                  type = "POS",
                  subType = "IEP_ENC",
                  count = 1,
                ),
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110002",
                  type = "POS",
                  subType = "QUAL_ATT",
                  count = 10,
                ),
              ),
            ),
          ),
        ),
      )

      // 110003, 110004, 110005 all truncate to the same default period — batched into one call
      whenever(
        caseNotesApiService.retrieveCaseNoteCountsByFromDate(
          typeSubType,
          setOf("110003", "110004", "110005"),
          prisonerByLastReviewDateOrDefaultPeriod[110003L]!!,
        ),
      ).thenReturn(
        flowOf(
          NoteUsageResponse(
            mapOf(
              "110003" to listOf(
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110003",
                  type = "NEG",
                  subType = "BEHAVEWARN",
                  count = 3,
                ),
              ),
              "110004" to listOf(
                UsageByPersonIdentifierResponse(
                  personIdentifier = "110004",
                  type = "POS",
                  subType = "IEP_ENC",
                  count = 1,
                ),
              ),
            ),
          ),
        ),
      )
      val behaviours = behaviourService.getBehaviours(reviews)

      assertThat(behaviours.caseNoteCountsByType).isEqualTo(
        mapOf(
          BookingTypeKey(bookingId = 110001, caseNoteType = "POS") to
            CaseNoteSummary(BookingTypeKey(bookingId = 110001, caseNoteType = "POS"), 4, 2),
          BookingTypeKey(bookingId = 110001, caseNoteType = "NEG") to
            CaseNoteSummary(BookingTypeKey(bookingId = 110001, caseNoteType = "NEG"), 2, 1),

          BookingTypeKey(bookingId = 110002, caseNoteType = "POS") to
            CaseNoteSummary(BookingTypeKey(bookingId = 110002, caseNoteType = "POS"), 11, 1),

          BookingTypeKey(bookingId = 110003, caseNoteType = "NEG") to
            CaseNoteSummary(BookingTypeKey(bookingId = 110003, caseNoteType = "NEG"), 3, 0),

          BookingTypeKey(bookingId = 110004, caseNoteType = "POS") to
            CaseNoteSummary(BookingTypeKey(bookingId = 110004, caseNoteType = "POS"), 1, 1),
        ),
      )
      assertThat(behaviours.lastRealReviews).isEqualTo(prisonerByLastRealReviewDate)

      verify(
        caseNotesApiService,
        times(3),
      ).retrieveCaseNoteCountsByFromDate(
        any(),
        any(),
        any(),
      )
    }
  }

  @Test
  fun `prisoners sharing default review period are batched into a single API call even with a drifting clock`() {
    // Simulates a real wall clock that advances by 1ms on each call to now(),
    // replicating the millisecond drift that occurs in production between successive
    // calls to LocalDateTime.now(clock) inside truncateReviewDate.
    var driftMs = 0L
    val driftingClock = object : Clock() {
      override fun getZone(): ZoneId = ZoneId.of("Europe/London")
      override fun withZone(zone: ZoneId): Clock = this
      override fun instant(): Instant = Instant.parse("2022-08-01T12:45:00.00Z").plusMillis(driftMs++)
    }
    val serviceWithDriftingClock = BehaviourService(caseNotesApiService, driftingClock)

    // Three prisoners with only INITIAL reviews — none qualify as a "real" review,
    // so all three fall back to the default 3-month lookback period.
    val reviews = listOf(
      prisonerIepLevel(
        bookingId = 200001L,
        prisonerNumber = "200001",
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(6),
      ),
      prisonerIepLevel(
        bookingId = 200002L,
        prisonerNumber = "200002",
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(6),
      ),
      prisonerIepLevel(
        bookingId = 200003L,
        prisonerNumber = "200003",
        reviewType = ReviewType.INITIAL,
        reviewTime = timeNow.minusMonths(6),
      ),
    )

    runBlocking {
      whenever(
        caseNotesApiService.retrieveCaseNoteCountsByFromDate(any(), any(), any()),
      ).thenReturn(flowOf(NoteUsageResponse(emptyMap())))

      serviceWithDriftingClock.getBehaviours(reviews)

      // All three prisoners share the same default period, so they must be batched
      // into a single API call. If the default period is recomputed per prisoner
      // (the old bug), each prisoner receives a different millisecond-offset
      // LocalDateTime, groupBy produces 3 distinct keys, and 3 calls are made.
      verify(caseNotesApiService, times(1)).retrieveCaseNoteCountsByFromDate(any(), any(), any())
    }
  }
}
