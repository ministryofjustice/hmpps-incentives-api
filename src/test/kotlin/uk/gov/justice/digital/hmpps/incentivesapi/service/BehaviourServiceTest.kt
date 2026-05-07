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
}
