package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("Next review date updater service")
class NextReviewDateUpdaterServiceTest {
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))
  private val incentiveReviewRepository: IncentiveReviewRepository = mock()
  private val nextReviewDateRepository: NextReviewDateRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val snsService: SnsService = mock()

  private val nextReviewDateUpdaterService = NextReviewDateUpdaterService(
    clock,
    incentiveReviewRepository,
    nextReviewDateRepository,
    prisonerSearchService,
    snsService,
  )

  @DisplayName("update all")
  @Nested
  inner class UpdateAllTest {
    private val prisoner1 = mockPrisoner("AB123C", 123L)
    private val prisoner2 = mockPrisoner("XY456Z", 456L)

    private val prisoners = listOf(prisoner1, prisoner2)

    @Test
    fun `updateMany() when no reviews in database`(): Unit = runBlocking {
      val bookingIds = listOf(prisoner1.bookingId, prisoner2.bookingId)
      whenever(
        incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds),
      ).thenReturn(emptyFlow())
      whenever(nextReviewDateRepository.findAllById(bookingIds))
        .thenReturn(emptyFlow())

      val expectedDate1 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = prisoner1.dateOfBirth,
          receptionDate = prisoner1.receptionDate,
          hasAcctOpen = prisoner1.hasAcctOpen,
          incentiveRecords = emptyList(),
        ),
      ).calculate()
      val expectedDate2 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = prisoner2.dateOfBirth,
          receptionDate = prisoner2.receptionDate,
          hasAcctOpen = prisoner2.hasAcctOpen,
          incentiveRecords = emptyList(),
        ),
      ).calculate()

      whenever(nextReviewDateRepository.existsById(prisoner1.bookingId))
        .thenReturn(false)
      whenever(nextReviewDateRepository.existsById(prisoner2.bookingId))
        .thenReturn(false)
      val expectedRecordsFlow = flowOf(
        NextReviewDate(prisoner1.bookingId, expectedDate1, new = true, whenUpdated = LocalDateTime.now(clock)),
        NextReviewDate(prisoner2.bookingId, expectedDate2, new = true, whenUpdated = LocalDateTime.now(clock)),
      )
      val expectedRecordsList = expectedRecordsFlow.toList()
      whenever(nextReviewDateRepository.saveAll(expectedRecordsList))
        .thenReturn(expectedRecordsFlow)

      val result = nextReviewDateUpdaterService.updateMany(prisoners)

      assertThat(result).isEqualTo(
        mapOf(
          prisoner1.bookingId to expectedDate1,
          prisoner2.bookingId to expectedDate2,
        ),
      )

      @Suppress("UnusedFlow")
      verify(nextReviewDateRepository, times(1))
        .saveAll(expectedRecordsList)

      // Doesn't publish 'next-review-date-changed' event for new records
      verify(snsService, times(0))
        .publishDomainEvent(any(), any(), any(), any())
    }

    @Test
    fun `updateMany() when some reviews in database`(): Unit = runBlocking {
      val prisoner2Reviews = flowOf(
        prisonerIepLevel(prisoner2.bookingId, reviewTime = LocalDateTime.now(clock)),
        prisonerIepLevel(prisoner2.bookingId, reviewTime = LocalDateTime.now(clock).minusMonths(11)),
      )
      val bookingIds = listOf(prisoner1.bookingId, prisoner2.bookingId)
      whenever(
        incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds),
      ).thenReturn(prisoner2Reviews)

      whenever(nextReviewDateRepository.existsById(prisoner1.bookingId)).thenReturn(false)
      whenever(nextReviewDateRepository.existsById(prisoner2.bookingId)).thenReturn(true)
      whenever(nextReviewDateRepository.findAllById(bookingIds))
        .thenReturn(
          flowOf(
            NextReviewDate(
              bookingId = prisoner2.bookingId,
              // NOTE: next review date is out-of-date and will change
              nextReviewDate = prisoner2Reviews.last().reviewTime.plusYears(1).toLocalDate(),
            ),
          ),
        )

      val expectedDate1 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = prisoner1.dateOfBirth,
          receptionDate = prisoner1.receptionDate,
          hasAcctOpen = prisoner1.hasAcctOpen,
          incentiveRecords = emptyList(),
        ),
      ).calculate()
      val expectedDate2 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = prisoner2.dateOfBirth,
          receptionDate = prisoner2.receptionDate,
          hasAcctOpen = prisoner2.hasAcctOpen,
          incentiveRecords = prisoner2Reviews.toList(),
        ),
      ).calculate()

      val expectedRecordsFlow = flowOf(
        NextReviewDate(prisoner1.bookingId, expectedDate1, new = true, whenUpdated = LocalDateTime.now(clock)),
        NextReviewDate(prisoner2.bookingId, expectedDate2, new = false, whenUpdated = LocalDateTime.now(clock)),
      )
      val expectedRecordsList = expectedRecordsFlow.toList()
      whenever(nextReviewDateRepository.saveAll(expectedRecordsList))
        .thenReturn(expectedRecordsFlow)

      val result = nextReviewDateUpdaterService.updateMany(prisoners)

      assertThat(result).isEqualTo(
        mapOf(
          prisoner1.bookingId to expectedDate1,
          prisoner2.bookingId to expectedDate2,
        ),
      )

      @Suppress("UnusedFlow")
      verify(nextReviewDateRepository, times(1))
        .saveAll(expectedRecordsList)

      // Doesn't publish 'next-review-date-changed' event as this is a new record
      verify(snsService, times(0))
        .publishDomainEvent(
          eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
          description = "A prisoner's next incentive review date has changed",
          occurredAt = LocalDateTime.now(clock),
          additionalInformation = AdditionalInformation(
            id = prisoner1.bookingId,
            nomsNumber = prisoner1.prisonerNumber,
          ),
        )
      // Next review date changed for prisoner2, check 'next-review-date-changed' event was published
      verify(snsService, times(1))
        .publishDomainEvent(
          eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
          description = "A prisoner's next incentive review date has changed",
          occurredAt = LocalDateTime.now(clock),
          additionalInformation = AdditionalInformation(
            id = prisoner2.bookingId,
            nomsNumber = prisoner2.prisonerNumber,
            nextReviewDate = expectedDate2,
          ),
        )
    }
  }

  @Test
  fun `update() calculate, save and return the next review date`(): Unit = runBlocking {
    val bookingId = 1234L
    val prisonerNumber = "A1244AB"

    val prisonerInfo = mockPrisoner(prisonerNumber, bookingId)
    whenever(prisonerSearchService.getPrisonerInfo(bookingId))
      .thenReturn(prisonerInfo)

    val reviews = flowOf(
      prisonerIepLevel(bookingId, reviewTime = LocalDateTime.now(clock)),
      prisonerIepLevel(bookingId, reviewTime = LocalDateTime.now(clock)),
    )
    whenever(nextReviewDateRepository.findAllById(listOf(bookingId)))
      .thenReturn(emptyFlow())
    whenever(incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(listOf(bookingId)))
      .thenReturn(reviews)

    val expectedNextReviewDate = NextReviewDateService(
      NextReviewDateInput(
        dateOfBirth = prisonerInfo.dateOfBirth,
        receptionDate = prisonerInfo.receptionDate,
        hasAcctOpen = prisonerInfo.hasAcctOpen,
        incentiveRecords = reviews.toList(),
      ),
    ).calculate()

    whenever(nextReviewDateRepository.existsById(bookingId)).thenReturn(true)
    val expectedRecordsFlow = flowOf(
      NextReviewDate(bookingId, expectedNextReviewDate, new = false, whenUpdated = LocalDateTime.now(clock)),
    )
    val expectedRecordsList = expectedRecordsFlow.toList()
    whenever(nextReviewDateRepository.saveAll(expectedRecordsList))
      .thenReturn(expectedRecordsFlow)

    val result = nextReviewDateUpdaterService.update(bookingId)

    assertThat(result).isEqualTo(expectedNextReviewDate)

    @Suppress("UnusedFlow")
    verify(nextReviewDateRepository, times(1))
      .saveAll(expectedRecordsList)

    // Doesn't publish 'next-review-date-changed' event for new records
    verify(snsService, times(0))
      .publishDomainEvent(any(), any(), any(), any())
  }
}
