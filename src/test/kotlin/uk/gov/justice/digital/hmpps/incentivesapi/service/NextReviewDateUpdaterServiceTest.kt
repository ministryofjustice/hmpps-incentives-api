package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NextReviewDateUpdaterServiceTest {
  private var clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.systemDefault())
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val nextReviewDateRepository: NextReviewDateRepository = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val offenderSearchService: OffenderSearchService = mock()
  private val snsService: SnsService = mock()

  private val nextReviewDateUpdaterService = NextReviewDateUpdaterService(
    clock,
    prisonerIepLevelRepository,
    nextReviewDateRepository,
    prisonApiService,
    offenderSearchService,
    snsService,
  )

  private val iepLevels = mapOf(
    "BAS" to IepLevel(iepLevel = "BAS", iepDescription = "Basic", sequence = 1),
    "STD" to IepLevel(iepLevel = "STD", iepDescription = "Standard", sequence = 2),
    "ENH" to IepLevel(iepLevel = "ENH", iepDescription = "Enhanced", sequence = 3),
    "EN2" to IepLevel(iepLevel = "EN2", iepDescription = "Enhanced 2", sequence = 4),
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    whenever(prisonApiService.getIncentiveLevels())
      .thenReturn(iepLevels)
  }

  @Nested
  inner class UpdateAllTest {
    private val offender1 = offenderSearchPrisoner("AB123C", 123L)
    private val offender2 = offenderSearchPrisoner("XY456Z", 456L)

    private val offenders = listOf(offender1, offender2)

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(prisonApiService.getIncentiveLevels())
        .thenReturn(iepLevels)
    }

    @Test
    fun `updateMany() when no reviews in database`(): Unit = runBlocking {
      val bookingIds = listOf(offender1.bookingId, offender2.bookingId)
      whenever(
        prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
      ).thenReturn(emptyFlow())
      whenever(nextReviewDateRepository.findAllById(bookingIds))
        .thenReturn(emptyFlow())

      val expectedDate1 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender1.dateOfBirth,
          receptionDate = offender1.receptionDate,
          hasAcctOpen = offender1.acctOpen,
          iepDetails = emptyList(),
        )
      ).calculate()
      val expectedDate2 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender2.dateOfBirth,
          receptionDate = offender2.receptionDate,
          hasAcctOpen = offender2.acctOpen,
          iepDetails = emptyList(),
        )
      ).calculate()

      whenever(nextReviewDateRepository.existsById(offender1.bookingId))
        .thenReturn(false)
      whenever(nextReviewDateRepository.existsById(offender2.bookingId))
        .thenReturn(false)
      val expectedRecordsFlow = flowOf(
        NextReviewDate(offender1.bookingId, expectedDate1, new = true, whenUpdated = LocalDateTime.now(clock)),
        NextReviewDate(offender2.bookingId, expectedDate2, new = true, whenUpdated = LocalDateTime.now(clock)),
      )
      val expectedRecordsList = expectedRecordsFlow.toList()
      whenever(nextReviewDateRepository.saveAll(expectedRecordsList))
        .thenReturn(expectedRecordsFlow)

      val result = nextReviewDateUpdaterService.updateMany(offenders)

      assertThat(result).isEqualTo(
        mapOf(
          offender1.bookingId to expectedDate1,
          offender2.bookingId to expectedDate2,
        )
      )

      verify(nextReviewDateRepository, times(1))
        .saveAll(expectedRecordsList)

      // Doesn't publish 'next-review-date-changed' event for new records
      verify(snsService, times(0))
        .publishDomainEvent(any(), any(), any(), any())
    }

    @Test
    fun `updateMany() when some reviews in database`(): Unit = runBlocking {
      val offender2Reviews = flowOf(
        prisonerIepLevel(offender2.bookingId, reviewTime = LocalDateTime.now(clock)),
        prisonerIepLevel(offender2.bookingId, reviewTime = LocalDateTime.now(clock).minusMonths(11)),
      )
      val bookingIds = listOf(offender1.bookingId, offender2.bookingId)
      whenever(
        prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
      ).thenReturn(offender2Reviews)

      whenever(nextReviewDateRepository.existsById(offender1.bookingId)).thenReturn(false)
      whenever(nextReviewDateRepository.existsById(offender2.bookingId)).thenReturn(true)
      whenever(nextReviewDateRepository.findAllById(bookingIds))
        .thenReturn(
          flowOf(
            NextReviewDate(
              bookingId = offender2.bookingId,
              // NOTE: next review date is out-of-date and will change
              nextReviewDate = offender2Reviews.last().reviewTime.plusYears(1).toLocalDate(),
            )
          )
        )

      val expectedDate1 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender1.dateOfBirth,
          receptionDate = offender1.receptionDate,
          hasAcctOpen = offender1.acctOpen,
          iepDetails = emptyList(),
        )
      ).calculate()
      val expectedDate2 = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender2.dateOfBirth,
          receptionDate = offender2.receptionDate,
          hasAcctOpen = offender2.acctOpen,
          iepDetails = offender2Reviews.toList().toIepDetails(iepLevels),
        )
      ).calculate()

      val expectedRecordsFlow = flowOf(
        NextReviewDate(offender1.bookingId, expectedDate1, new = true, whenUpdated = LocalDateTime.now(clock)),
        NextReviewDate(offender2.bookingId, expectedDate2, new = false, whenUpdated = LocalDateTime.now(clock)),
      )
      val expectedRecordsList = expectedRecordsFlow.toList()
      whenever(nextReviewDateRepository.saveAll(expectedRecordsList))
        .thenReturn(expectedRecordsFlow)

      val result = nextReviewDateUpdaterService.updateMany(offenders)

      assertThat(result).isEqualTo(
        mapOf(
          offender1.bookingId to expectedDate1,
          offender2.bookingId to expectedDate2,
        )
      )

      verify(nextReviewDateRepository, times(1))
        .saveAll(expectedRecordsList)

      // Doesn't publish 'next-review-date-changed' event as this is a new record
      verify(snsService, times(0))
        .publishDomainEvent(
          eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
          description = "A prisoner next review date has changed",
          occurredAt = LocalDateTime.now(clock),
          additionalInformation = AdditionalInformation(
            id = offender1.bookingId,
            nomsNumber = offender1.prisonerNumber,
          ),
        )
      // Next review date changed for offender2, check 'next-review-date-changed' event was published
      verify(snsService, times(1))
        .publishDomainEvent(
          eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
          description = "A prisoner next review date has changed",
          occurredAt = LocalDateTime.now(clock),
          additionalInformation = AdditionalInformation(
            id = offender2.bookingId,
            nomsNumber = offender2.prisonerNumber,
            nextReviewDate = expectedDate2,
          ),
        )
    }
  }

  @Test
  fun `update() calculate, save and return the next review date`(): Unit = runBlocking {
    val bookingId = 1234L
    val prisonerNumber = "A1244AB"

    val locationInfo = prisonerAtLocation(bookingId, prisonerNumber)
    whenever(prisonApiService.getPrisonerInfo(bookingId, useClientCredentials = true))
      .thenReturn(locationInfo)
    val offender = offenderSearchPrisoner(prisonerNumber, bookingId)
    whenever(offenderSearchService.getOffender(locationInfo.offenderNo))
      .thenReturn(offender)
    val reviews = flowOf(
      prisonerIepLevel(bookingId),
      prisonerIepLevel(bookingId),
    )
    whenever(nextReviewDateRepository.findAllById(listOf(bookingId)))
      .thenReturn(emptyFlow())
    whenever(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(listOf(bookingId)))
      .thenReturn(reviews)

    val expectedNextReviewDate = NextReviewDateService(
      NextReviewDateInput(
        dateOfBirth = offender.dateOfBirth,
        receptionDate = offender.receptionDate,
        hasAcctOpen = offender.acctOpen,
        iepDetails = reviews.toList().toIepDetails(iepLevels),
      )
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

    verify(nextReviewDateRepository, times(1))
      .saveAll(expectedRecordsList)

    // Doesn't publish 'next-review-date-changed' event for new records
    verify(snsService, times(0))
      .publishDomainEvent(any(), any(), any(), any())
  }
}
