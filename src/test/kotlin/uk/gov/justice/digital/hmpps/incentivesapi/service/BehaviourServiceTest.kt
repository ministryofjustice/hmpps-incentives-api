package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BehaviourServiceTest {
  private val prisonApiService: PrisonApiService = mock()
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val behaviourService = BehaviourService(prisonApiService, clock)

  private val timeNow = LocalDateTime.now(clock)

  @Test
  fun `case note totals correct map against review dates`() {
    val reviews =
      listOf(
        prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = true, reviewType = ReviewType.TRANSFER, reviewTime = timeNow.minusMonths(1)),
        prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = false, reviewType = ReviewType.REVIEW, reviewTime = timeNow.minusMonths(2)), // real review
        prisonerIepLevel(bookingId = 110001, iepCode = "STD", current = false, reviewType = ReviewType.INITIAL, reviewTime = timeNow.minusMonths(3)),
        prisonerIepLevel(bookingId = 110002, iepCode = "ENH", current = true, reviewType = ReviewType.REVIEW, reviewTime = timeNow.minusMonths(1)), // real review
        prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = false, reviewType = ReviewType.TRANSFER, reviewTime = timeNow.minusMonths(2)),
        prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = false, reviewType = ReviewType.REVIEW, reviewTime = timeNow.minusMonths(3)),
        prisonerIepLevel(bookingId = 110002, iepCode = "STD", current = false, reviewType = ReviewType.INITIAL, reviewTime = timeNow.minusMonths(4)),
        prisonerIepLevel(bookingId = 110003, iepCode = "STD", current = true, reviewType = ReviewType.MIGRATED, reviewTime = timeNow.minusMonths(14)), // presumed to be real review
        prisonerIepLevel(bookingId = 110004, iepCode = "STD", current = true, reviewType = ReviewType.INITIAL, reviewTime = timeNow.minusMonths(1)),
        prisonerIepLevel(bookingId = 110005, iepCode = "BAS", current = true, reviewType = ReviewType.REVIEW, reviewTime = timeNow.minusMonths(5)), // real review
        prisonerIepLevel(bookingId = 110005, iepCode = "STD", current = false, reviewType = ReviewType.MIGRATED, reviewTime = timeNow.minusMonths(8)),
      )
    // dates from above of last real review
    val prisonerByLastRealReviewDate =
      mapOf(
        110001L to timeNow.minusMonths(2),
        110002L to timeNow.minusMonths(1),
        110003L to timeNow.minusMonths(14),
        110004L to null,
        110005L to timeNow.minusMonths(5),
      )
    // filters that would be used to call prison-api
    val prisonerByLastReviewDateOrDefaultPeriod =
      mapOf(
        110001L to timeNow.minusMonths(2),
        110002L to timeNow.minusMonths(1),
        110003L to timeNow.minusMonths(3),
        110004L to timeNow.minusMonths(3),
        110005L to timeNow.minusMonths(3),
      )

    runBlocking {
      whenever(prisonApiService.retrieveCaseNoteCountsByFromDate(listOf("POS", "NEG"), prisonerByLastReviewDateOrDefaultPeriod)).thenReturn(
        flowOf(
          PrisonerCaseNoteByTypeSubType(bookingId = 110001, caseNoteType = "POS", caseNoteSubType = "IEP_ENC", numCaseNotes = 2),
          PrisonerCaseNoteByTypeSubType(bookingId = 110001, caseNoteType = "POS", caseNoteSubType = "QUAL_ATT", numCaseNotes = 1),
          PrisonerCaseNoteByTypeSubType(bookingId = 110001, caseNoteType = "POS", caseNoteSubType = "POS_GEN", numCaseNotes = 1),
          PrisonerCaseNoteByTypeSubType(bookingId = 110001, caseNoteType = "NEG", caseNoteSubType = "IEP_WARN", numCaseNotes = 1),
          PrisonerCaseNoteByTypeSubType(bookingId = 110001, caseNoteType = "NEG", caseNoteSubType = "BEHAVEWARN", numCaseNotes = 1),
          PrisonerCaseNoteByTypeSubType(bookingId = 110002, caseNoteType = "POS", caseNoteSubType = "IEP_ENC", numCaseNotes = 1),
          PrisonerCaseNoteByTypeSubType(bookingId = 110002, caseNoteType = "POS", caseNoteSubType = "QUAL_ATT", numCaseNotes = 10),
          PrisonerCaseNoteByTypeSubType(bookingId = 110003, caseNoteType = "NEG", caseNoteSubType = "BEHAVEWARN", numCaseNotes = 3),
          PrisonerCaseNoteByTypeSubType(bookingId = 110004, caseNoteType = "POS", caseNoteSubType = "IEP_ENC", numCaseNotes = 1),
        ),
      )

      val behaviours = behaviourService.getBehaviours(reviews)

      assertThat(behaviours.caseNoteCountsByType).isEqualTo(
        mapOf(
          BookingTypeKey(bookingId = 110001, caseNoteType = "POS") to CaseNoteSummary(BookingTypeKey(bookingId = 110001, caseNoteType = "POS"), 4, 2),
          BookingTypeKey(bookingId = 110001, caseNoteType = "NEG") to CaseNoteSummary(BookingTypeKey(bookingId = 110001, caseNoteType = "NEG"), 2, 1),
          BookingTypeKey(bookingId = 110002, caseNoteType = "POS") to CaseNoteSummary(BookingTypeKey(bookingId = 110002, caseNoteType = "POS"), 11, 1),
          BookingTypeKey(bookingId = 110003, caseNoteType = "NEG") to CaseNoteSummary(BookingTypeKey(bookingId = 110003, caseNoteType = "NEG"), 3, 0),
          BookingTypeKey(bookingId = 110004, caseNoteType = "POS") to CaseNoteSummary(BookingTypeKey(bookingId = 110004, caseNoteType = "POS"), 1, 1),
        ),
      )
      assertThat(behaviours.lastRealReviews).isEqualTo(prisonerByLastRealReviewDate)

      verify(prisonApiService).retrieveCaseNoteCountsByFromDate(listOf("POS", "NEG"), prisonerByLastReviewDateOrDefaultPeriod)
    }
  }
}
