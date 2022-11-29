package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Recalculates and persists next review dates
 */
@Service
class NextReviewDateUpdaterService(
  private val clock: Clock,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val nextReviewDateRepository: NextReviewDateRepository,
  private val prisonApiService: PrisonApiService,
  private val offenderSearchService: OffenderSearchService,
  private val snsService: SnsService,
) {

  /**
   * Update next review date for the given bookingId
   *
   * @param bookingId of the offender to update
   *
   * @return the nextReviewDate for the given bookingId
   * */
  suspend fun update(bookingId: Long): LocalDate {
    val locationInfo = prisonApiService.getPrisonerInfo(bookingId, useClientCredentials = true)
    val prisonerNumber = locationInfo.offenderNo
    val offender = offenderSearchService.getOffender(prisonerNumber)

    return updateMany(listOf(offender))[offender.bookingId]!!
  }

  /**
   * Update next review date for the given offenders
   *
   * @param offenders is the list of offenders to update
   *
   * @return a map with bookingIds as keys and nextReviewDate as value
   * */
  suspend fun updateMany(offenders: List<OffenderSearchPrisoner>): Map<Long, LocalDate> {
    if (offenders.isEmpty()) {
      return emptyMap()
    }

    val offendersMap = offenders.associateBy(OffenderSearchPrisoner::bookingId)
    val bookingIds = offendersMap.keys.toList()

    val nextReviewDatesBeforeUpdate: Map<Long, LocalDate> = nextReviewDateRepository.findAllById(bookingIds).toList().toMapByBookingId()

    val iepLevels: Map<String, IepLevel> = prisonApiService.getIncentiveLevels()

    // NOTE: This is to account for bookingIds potentially without any review record
    val bookingIdsNoReviews = bookingIds.associateWith { emptyList<PrisonerIepLevel>() }
    val reviewsMap = bookingIdsNoReviews + prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds)
      .toList()
      .groupBy(PrisonerIepLevel::bookingId)

    val nextReviewDateRecords = reviewsMap.map {
      val bookingId = it.key
      val iepDetails = it.value.toIepDetails(iepLevels)
      val offender = offendersMap[bookingId]!!

      val nextReviewDate = NextReviewDateService(
        NextReviewDateInput(
          dateOfBirth = offender.dateOfBirth,
          receptionDate = offender.receptionDate,
          hasAcctOpen = offender.acctOpen,
          iepDetails = iepDetails,
        )
      ).calculate()

      NextReviewDate(
        bookingId = bookingId,
        nextReviewDate = nextReviewDate,
        new = !nextReviewDateRepository.existsById(bookingId),
        whenUpdated = LocalDateTime.now(clock),
      )
    }

    val nextReviewDatesAfterUpdate: Map<Long, LocalDate> = nextReviewDateRepository.saveAll(nextReviewDateRecords).toList().toMapByBookingId()

    // Determine which next review dates records actually changed
    val bookingIdsChanged = bookingIds.filter { bookingId ->
      nextReviewDatesBeforeUpdate[bookingId] != null && // only publish domain events when next review date changed
        nextReviewDatesAfterUpdate[bookingId] != nextReviewDatesBeforeUpdate[bookingId]
    }

    publishDomainEvents(bookingIdsChanged, offendersMap, nextReviewDatesAfterUpdate)

    return nextReviewDatesAfterUpdate
  }

  private suspend fun publishDomainEvents(bookingIdsChanged: List<Long>, offendersMap: Map<Long, OffenderSearchPrisoner>, nextReviewDatesMap: Map<Long, LocalDate>) = runBlocking {
    bookingIdsChanged.forEach { bookingId ->
      launch {
        snsService.publishDomainEvent(
          eventType = IncentivesDomainEventType.PRISONER_NEXT_REVIEW_DATE_CHANGED,
          description = "A prisoner next review date has changed",
          occurredAt = LocalDateTime.now(clock),
          additionalInformation = AdditionalInformation(
            id = bookingId,
            nomsNumber = offendersMap[bookingId]!!.prisonerNumber,
            nextReviewDate = nextReviewDatesMap[bookingId],
          ),
        )
      }
    }
  }
}
