package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Recalculates and persists next review dates
 */
@Service
class NextReviewDateUpdaterService(
  private val clock: Clock,
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val nextReviewDateRepository: NextReviewDateRepository,
  private val prisonApiService: PrisonApiService,
  private val snsService: SnsService,
) {

  /**
   * Update the next review date for the given bookingId
   *
   * @param bookingId of the prisoner to update
   *
   * @return the nextReviewDate for the given bookingId
   * */
  suspend fun update(bookingId: Long): LocalDate {
    val prisonerInfo = prisonApiService.getPrisonerExtraInfo(bookingId, useClientCredentials = true)

    return updateMany(listOf(prisonerInfo))[prisonerInfo.bookingId]!!
  }

  /**
   * Update next review date for the given prisoners
   *
   * @param prisoners is the list of prisoners to update
   *
   * @return a map with bookingIds as keys and nextReviewDate as value
   * */
  suspend fun updateMany(prisoners: List<PrisonerInfoForNextReviewDate>): Map<Long, LocalDate> {
    if (prisoners.isEmpty()) {
      return emptyMap()
    }

    val prisonerMap = prisoners.associateBy(PrisonerInfoForNextReviewDate::bookingId)
    val bookingIds = prisonerMap.keys.toList()

    val nextReviewDatesBeforeUpdate: Map<Long, LocalDate> = nextReviewDateRepository.findAllById(
      bookingIds,
    ).toList().toMapByBookingId()

    // NOTE: This is to account for bookingIds potentially without any review record
    val bookingIdsNoReviews = bookingIds.associateWith { emptyList<IncentiveReview>() }
    val reviewsMap =
      bookingIdsNoReviews + incentiveReviewRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
        .toList()
        .groupBy(IncentiveReview::bookingId)

    val nextReviewDateRecords = reviewsMap.map { (bookingId, incentiveRecords) ->
      val prisoner = prisonerMap[bookingId]!!

      val nextReviewDate = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = prisoner.dateOfBirth,
          receptionDate = prisoner.receptionDate,
          hasAcctOpen = prisoner.hasAcctOpen,
          incentiveRecords = incentiveRecords,
        ),
      ).calculate()

      NextReviewDate(
        bookingId = bookingId,
        nextReviewDate = nextReviewDate,
        new = !nextReviewDateRepository.existsById(bookingId),
        whenUpdated = LocalDateTime.now(clock),
      )
    }

    val nextReviewDatesAfterUpdate: Map<Long, LocalDate> = nextReviewDateRepository.saveAll(
      nextReviewDateRecords,
    ).toList().toMapByBookingId()

    // Determine which next review dates records actually changed
    val bookingIdsChanged = bookingIds.filter { bookingId ->
      nextReviewDatesBeforeUpdate[bookingId] != null &&
        // only publish domain events when next review date changed
        nextReviewDatesAfterUpdate[bookingId] != nextReviewDatesBeforeUpdate[bookingId]
    }

    publishDomainEvents(bookingIdsChanged, prisonerMap, nextReviewDatesAfterUpdate)

    return nextReviewDatesAfterUpdate
  }

  private fun publishDomainEvents(
    bookingIdsChanged: List<Long>,
    prisonerMap: Map<Long, PrisonerInfoForNextReviewDate>,
    nextReviewDatesMap: Map<Long, LocalDate>,
  ) {
    bookingIdsChanged.forEach { bookingId ->
      snsService.publishDomainEvent(
        eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
        description = "A prisoner's next incentive review date has changed",
        occurredAt = LocalDateTime.now(clock),
        additionalInformation = AdditionalInformation(
          id = bookingId,
          nomsNumber = prisonerMap[bookingId]!!.prisonerNumber,
          nextReviewDate = nextReviewDatesMap[bookingId],
        ),
      )
    }
  }
}

interface PrisonerInfoForNextReviewDate {
  val bookingId: Long
  val prisonerNumber: String
  val dateOfBirth: LocalDate
  val receptionDate: LocalDate
  val hasAcctOpen: Boolean
}
