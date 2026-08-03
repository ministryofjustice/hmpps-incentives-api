package uk.gov.justice.digital.hmpps.incentivesapi.service

import jakarta.validation.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BookingSwitchRepairOutcome
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BookingSwitchRepairResult
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerBasicInfo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.daysSinceReviewCalc
import uk.gov.justice.digital.hmpps.incentivesapi.dto.findDefaultOnAdmission
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PrisonerIncentiveReviewService(
  private val prisonerSearchService: PrisonerSearchService,
  private val incentiveReviewRepository: IncentiveReviewRepository,
  private val incentiveLevelService: IncentiveLevelAuditedService,
  private val prisonIncentiveLevelService: PrisonIncentiveLevelAuditedService,
  private val nearestPrisonIncentiveLevelService: NearestPrisonIncentiveLevelService,
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val authenticationHolder: HmppsReactiveAuthenticationHolder,
  private val clock: Clock,
  private val nextReviewDateGetterService: NextReviewDateGetterService,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
  private val nextReviewDateRepository: NextReviewDateRepository,
  private val incentiveStoreService: IncentiveStoreService,
  private val transactionalOperator: TransactionalOperator,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getPrisonerIncentiveHistory(bookingId: Long, withDetails: Boolean = true): IncentiveReviewSummary {
    val reviews = incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)
    if (reviews.count() == 0) throw IncentiveReviewNotFoundException("No Incentive Reviews for booking ID $bookingId")
    return buildIepSummary(reviews, incentiveLevelService.getAllIncentiveLevelsMapByCode(), withDetails)
  }

  suspend fun getPrisonerIncentiveHistory(prisonerNumber: String): IncentiveReviewSummary {
    val reviews = incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber)
    if (reviews.count() == 0) {
      throw IncentiveReviewNotFoundException("No Incentive Reviews for prisoner number $prisonerNumber")
    }
    return buildIepSummary(reviews, incentiveLevelService.getAllIncentiveLevelsMapByCode())
  }

  suspend fun addIncentiveReview(
    prisonerNumber: String,
    createIncentiveReviewRequest: CreateIncentiveReviewRequest,
  ): IncentiveReviewDetail {
    val prisonerInfo = prisonerSearchService.getPrisonerInfo(prisonerNumber)
    return addIncentiveReviewForPrisonerAtLocation(prisonerInfo, createIncentiveReviewRequest)
  }

  suspend fun getReviewById(id: Long): IncentiveReviewDetail = incentiveReviewRepository.findById(
    id,
  )?.toIncentiveReviewDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())
    ?: throw NoDataFoundException(id)

  suspend fun processOffenderEvent(prisonOffenderEvent: HMPPSDomainEvent) =
    when (prisonOffenderEvent.additionalInformation?.reason) {
      "NEW_ADMISSION" -> createIncentiveReviewForReceivedPrisoner(prisonOffenderEvent, ReviewType.INITIAL)
      // NOTE: This may NOT be a recall. Someone could be readmitted back to prison for a number of other reasons (e.g., remands)
      "READMISSION" -> createIncentiveReviewForReceivedPrisoner(prisonOffenderEvent, ReviewType.READMISSION)
      "TRANSFERRED" -> createIncentiveReviewForReceivedPrisoner(prisonOffenderEvent, ReviewType.TRANSFER)
      "READMISSION_SWITCH_BOOKING" -> reinstateReviewsAfterBookingSwitch(prisonOffenderEvent)
      "MERGE" -> mergedPrisonerDetails(prisonOffenderEvent)
      else -> {
        log.debug("Ignoring prisonOffenderEvent with reason ${prisonOffenderEvent.additionalInformation?.reason}")
      }
    }

  suspend fun processPrisonerAlertsUpdatedEvent(prisonOffenderEvent: HMPPSDomainEvent) {
    val acctAdded: Boolean = prisonOffenderEvent.additionalInformation?.alertsAdded
      ?.contains(PrisonerAlert.ACCT_ALERT_CODE) == true
    val acctRemoved: Boolean = prisonOffenderEvent.additionalInformation?.alertsRemoved
      ?.contains(PrisonerAlert.ACCT_ALERT_CODE) == true

    if (acctAdded || acctRemoved) {
      updateNextReviewDate(prisonOffenderEvent)
    } else {
      log.debug(
        "Ignoring 'prisoner-offender-search.prisoner.alerts-updated' event, No ACCT alerts added/removed: prisonerNumber = {}, alertsAdded = {}, alertsRemoved = {}",
        prisonOffenderEvent.additionalInformation?.nomsNumber,
        prisonOffenderEvent.additionalInformation?.alertsAdded,
        prisonOffenderEvent.additionalInformation?.alertsRemoved,
      )
    }
  }

  private suspend fun updateNextReviewDate(prisonOffenderEvent: HMPPSDomainEvent) {
    prisonOffenderEvent.additionalInformation?.bookingId?.let { bookingId ->
      val changes = transactionalOperator.executeAndAwait {
        nextReviewDateUpdaterService.updateWriteOnly(bookingId)
      }
      nextReviewDateUpdaterService.publishDomainEvents(changes)
    } ?: run {
      log.error("Could not update next review date: bookingId null for prisonOffenderEvent: $prisonOffenderEvent")
    }
  }

  private suspend fun createIncentiveReviewForReceivedPrisoner(
    prisonOffenderEvent: HMPPSDomainEvent,
    reviewType: ReviewType,
  ) {
    prisonOffenderEvent.additionalInformation?.nomsNumber?.let {
      val prisonerInfo = prisonerSearchService.getPrisonerInfo(it)
      val iepLevel = getIncentiveLevelForReviewType(prisonerInfo, reviewType)
      val comment = getReviewCommentForEvent(prisonOffenderEvent)

      val incentiveReview = incentiveStoreService.saveIncentiveReview(
        IncentiveReview(
          levelCode = iepLevel,
          commentText = comment,
          bookingId = prisonerInfo.bookingId,
          prisonId = prisonerInfo.prisonId,
          current = true,
          reviewedBy = SYSTEM_USERNAME,
          reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
          reviewType = reviewType,
          prisonerNumber = prisonerInfo.prisonerNumber,
        ),
      )

      val iepDetail = incentiveReview.toIncentiveReviewDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())
      publishReviewDomainEvent(
        iepDetail,
        IncentivesDomainEventType.IEP_REVIEW_INSERTED,
      )
      publishAuditEvent(
        iepDetail,
        AuditType.IEP_REVIEW_ADDED,
      )
    } ?: run {
      log.warn("prisonerNumber null for prisonOffenderEvent: $prisonOffenderEvent ")
    }
  }

  private suspend fun getIncentiveLevelForReviewType(prisonerInfo: PrisonerBasicInfo, reviewType: ReviewType): String {
    val prisonIncentiveLevels = prisonIncentiveLevelService.getActivePrisonIncentiveLevels(prisonerInfo.prisonId)
    val defaultLevelCode = prisonIncentiveLevels.findDefaultOnAdmission(prisonerInfo.prisonId).levelCode

    return when (reviewType) {
      ReviewType.INITIAL, ReviewType.READMISSION -> {
        defaultLevelCode // admission should always be the default
      }
      ReviewType.TRANSFER -> {
        try {
          val iepHistory =
            getPrisonerIncentiveHistory(
              prisonerInfo.bookingId,
              withDetails = true,
            ).incentiveReviewDetails
          val levelCodeBeforeTransfer =
            iepHistory.sortedBy(IncentiveReviewDetail::iepTime).lastOrNull {
              it.agencyId != prisonerInfo.prisonId
            }?.iepCode
              ?: defaultLevelCode // if no previous prison
          nearestPrisonIncentiveLevelService.findNearestHighestLevel(
            prisonerInfo.prisonId,
            levelCodeBeforeTransfer,
          )
        } catch (_: IncentiveReviewNotFoundException) {
          defaultLevelCode // this is to handle no reviews - only an issue before migration
        }
      }

      else -> throw NotImplementedError("Not implemented for $reviewType")
    }
  }

  private fun getReviewCommentForEvent(prisonOffenderEvent: HMPPSDomainEvent) =
    when (prisonOffenderEvent.additionalInformation?.reason) {
      "NEW_ADMISSION", "READMISSION" -> "Default level assigned on arrival"
      "TRANSFERRED" -> "Level transferred from previous establishment"
      else -> prisonOffenderEvent.description
    }

  private suspend fun buildIepSummary(
    levels: Flow<IncentiveReview>,
    incentiveLevels: Map<String, IncentiveLevel>,
    withDetails: Boolean = true,
  ): IncentiveReviewSummary {
    val reviews = levels.toList()
    val iepDetails = reviews.map { it.toIncentiveReviewDetail(incentiveLevels) }

    // Reviews arrive newest first, but the newest is not always the current one. A prisoner
    // mistakenly admitted onto a new booking has a default-level review that is newer yet no longer
    // current, and it must not mask the level they hold on the booking they are actually on; a
    // backdated review is current while an older-dated one is newer. `current` is what
    // IncentiveStoreService.saveIncentiveReview asserts, so it decides. Falling back to the newest
    // keeps behaviour for any prisoner whose rows predate the flag being maintained.
    val currentIep = reviews.zip(iepDetails).firstOrNull { (review, _) -> review.current }?.second
      ?: iepDetails.firstOrNull()
      ?: throw IncentiveReviewNotFoundException("Not Found incentive reviews")

    val incentiveReviewSummary = IncentiveReviewSummary(
      bookingId = currentIep.bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepCode = currentIep.iepCode,
      iepLevel = currentIep.iepLevel,
      id = currentIep.id,
      prisonerNumber = currentIep.prisonerNumber,
      incentiveReviewDetails = iepDetails,
      nextReviewDate = nextReviewDateGetterService.get(currentIep.bookingId),
      daysSinceReview = daysSinceReviewCalc(currentIep.iepDate, clock),
    )

    if (!withDetails) {
      incentiveReviewSummary.incentiveReviewDetails = emptyList()
    }

    return incentiveReviewSummary
  }

  private suspend fun addIncentiveReviewForPrisonerAtLocation(
    prisonerInfo: PrisonerBasicInfo,
    createIncentiveReviewRequest: CreateIncentiveReviewRequest,
  ): IncentiveReviewDetail {
    if (createIncentiveReviewRequest.reviewTime != null &&
      createIncentiveReviewRequest.reviewTime.isAfter(LocalDateTime.now(clock))
    ) {
      throw ValidationException("Review time cannot be in the future")
    }

    val reviewTime = createIncentiveReviewRequest.reviewTime ?: LocalDateTime.now(clock)
    val reviewerUserName = createIncentiveReviewRequest.reviewedBy ?: authenticationHolder.getPrincipal()

    val newIepReview = incentiveStoreService.saveIncentiveReview(
      IncentiveReview(
        levelCode = createIncentiveReviewRequest.iepLevel,
        commentText = createIncentiveReviewRequest.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = prisonerInfo.prisonId,
        current = true,
        reviewedBy = reviewerUserName,
        reviewTime = reviewTime,
        reviewType = createIncentiveReviewRequest.reviewType ?: ReviewType.REVIEW,
        prisonerNumber = prisonerInfo.prisonerNumber,
      ),
    ).toIncentiveReviewDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())

    // Propagate the new IEP review to other services
    publishReviewDomainEvent(newIepReview, IncentivesDomainEventType.IEP_REVIEW_INSERTED)

    publishAuditEvent(newIepReview, AuditType.IEP_REVIEW_ADDED)

    return newIepReview
  }

  /**
   * Snapshot of the fields prisoner-search caches for a prisoner's current incentive: the level,
   * the review date/time and the next review date. Used to detect whether a merge or booking move
   * has changed what downstream consumers display.
   */
  private data class CurrentReviewSnapshot(
    val review: IncentiveReview,
    val nextReviewDate: LocalDate?,
  )

  private suspend fun currentReviewSnapshotFor(prisonerNumber: String): CurrentReviewSnapshot? {
    val current = incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber)
      .firstOrNull { it.current } ?: return null
    // Read the persisted next review date directly rather than via NextReviewDateGetterService,
    // which would (re)compute and persist it — an unwanted side effect when only snapshotting.
    val nextReviewDate = nextReviewDateRepository.findById(current.bookingId)?.nextReviewDate
    return CurrentReviewSnapshot(current, nextReviewDate)
  }

  /**
   * Publishes an [IncentivesDomainEventType.IEP_REVIEW_UPDATED] domain event when a prisoner's
   * current incentive snapshot has changed (e.g. after a merge or booking move), so downstream
   * consumers such as prisoner-search can refresh. The snapshot covers the level, the review
   * date/time and the next review date — the fields prisoner-search caches. No event is published
   * if all three are unchanged.
   */
  private suspend fun publishIfCurrentReviewChanged(
    prisonerNumber: String,
    before: CurrentReviewSnapshot?,
  ): CurrentReviewSnapshot? {
    val after = currentReviewSnapshotFor(prisonerNumber) ?: return null
    val changed = before == null ||
      before.review.levelCode != after.review.levelCode ||
      before.review.reviewTime != after.review.reviewTime ||
      before.nextReviewDate != after.nextReviewDate
    if (changed) {
      val detail = after.review.toIncentiveReviewDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())
      publishReviewDomainEvent(detail, IncentivesDomainEventType.IEP_REVIEW_UPDATED)
    }
    return after
  }

  private suspend fun publishReviewDomainEvent(
    incentiveReviewDetail: IncentiveReviewDetail,
    eventType: IncentivesDomainEventType,
  ) {
    val description: String = when (eventType) {
      IncentivesDomainEventType.IEP_REVIEW_INSERTED -> "An IEP review has been added"
      IncentivesDomainEventType.IEP_REVIEW_UPDATED -> "An IEP review has been updated"
      IncentivesDomainEventType.IEP_REVIEW_DELETED -> "An IEP review has been deleted"
      else -> {
        throw IllegalArgumentException("Tried to publish a review event with a non-review event type: $eventType")
      }
    }

    snsService.publishDomainEvent(
      eventType,
      description,
      occurredAt = incentiveReviewDetail.iepTime,
      AdditionalInformation(
        id = incentiveReviewDetail.id,
        nomsNumber = incentiveReviewDetail.prisonerNumber,
      ),
    )
  }

  private suspend fun publishAuditEvent(incentiveReviewDetail: IncentiveReviewDetail, auditType: AuditType) {
    auditService.sendMessage(
      auditType,
      incentiveReviewDetail.id.toString(),
      incentiveReviewDetail,
      incentiveReviewDetail.userId,
    )
  }

  /**
   * Handles a `READMISSION_SWITCH_BOOKING` received event.
   *
   * NOMIS users correct a mistakenly-created new booking by releasing the prisoner and re-admitting
   * them onto their earlier booking. prisoner-search reports that as `READMISSION_SWITCH_BOOKING`
   * and, by its own definition, only when the booking the prisoner is now on is *not* their
   * highest-numbered one — so the mistaken bookings always have higher ids.
   */
  private suspend fun reinstateReviewsAfterBookingSwitch(prisonOffenderEvent: HMPPSDomainEvent) {
    val prisonerNumber = prisonOffenderEvent.additionalInformation?.nomsNumber ?: run {
      log.warn("prisonerNumber null for prisonOffenderEvent: $prisonOffenderEvent")
      return
    }
    reinstateReviewsAfterBookingSwitch(prisonerNumber, repairedBy = SYSTEM_USERNAME, dryRun = false)
  }

  /**
   * Repairs a prisoner whose incentive level was left behind on a booking NOMIS has since switched
   * away from — for those affected before [reinstateReviewsAfterBookingSwitch] was handling the
   * event, and for any the event is missed for since.
   *
   * The repair is the same operation the event triggers, so it also publishes
   * `incentives.iep-review.updated`, which a database-level fix could not do. Running it against a
   * prisoner who is already correct is a safe no-op, so it can be re-run freely.
   */
  suspend fun repairAfterBookingSwitch(prisonerNumber: String, dryRun: Boolean = false) =
    reinstateReviewsAfterBookingSwitch(
      prisonerNumber,
      repairedBy = authenticationHolder.getPrincipal(),
      dryRun = dryRun,
    )

  /**
   * The `NEW_ADMISSION`/`READMISSION` event for the mistaken booking will already have written a
   * default-level review against it. Because `current` is unique only per booking, that review is
   * still current and, being the most recent by review time, wins every prisoner-scoped read —
   * masking the level the prisoner actually holds on the reinstated booking.
   *
   * Reviews are only flipped, never deleted, so the prisoner's history stays intact.
   */
  private suspend fun reinstateReviewsAfterBookingSwitch(
    prisonerNumber: String,
    repairedBy: String,
    dryRun: Boolean,
  ): BookingSwitchRepairResult {
    val correctBookingId = prisonerSearchService.getPrisonerInfo(prisonerNumber).bookingId
    val before = currentReviewSnapshotFor(prisonerNumber)

    val reviews = incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber).toList()
    // Booking ids are sequential, so a booking above the one the prisoner is now on was created
    // after it and is one they are not on — an admission that was abandoned. Staff sometimes get it
    // wrong more than once before switching back, so every such booking is stood down, not just the
    // newest. Genuine history from earlier sentences sits on *lower* bookings, is routinely left
    // current because nothing ever clears it, and is excluded by the id comparison.
    //
    // Only reviews this service wrote on admission are touched. A human review on a later booking is
    // a real decision about the prisoner and is never stood down, even though the booking was
    // abandoned — see the note on IR-1808 about what that leaves behind.
    val supersededReviews = reviews.filter {
      it.current && it.bookingId > correctBookingId && it.reviewedBy == SYSTEM_USERNAME
    }
    // `reviews` is ordered by review time descending, so this is the latest on the correct booking
    val latestOnCorrectBooking = reviews.firstOrNull { it.bookingId == correctBookingId }
    val reviewToReinstate = latestOnCorrectBooking?.takeUnless { it.current }

    if (supersededReviews.isEmpty() && reviewToReinstate == null) {
      val message = "Booking switch for $prisonerNumber: nothing to reinstate on booking $correctBookingId"
      log.info(message)
      return BookingSwitchRepairResult(
        prisonerNumber = prisonerNumber,
        bookingId = correctBookingId,
        outcome = BookingSwitchRepairOutcome.NOTHING_TO_DO,
        dryRun = dryRun,
        levelCodeBefore = before?.review?.levelCode,
        levelCodeAfter = before?.review?.levelCode,
        reviewIdsStoodDown = emptyList(),
        reviewIdReinstated = null,
        message = message,
      )
    }

    val supersededIds = supersededReviews.map { it.id }
    val message = "Reinstated incentive level on booking $correctBookingId for $prisonerNumber " +
      "after booking switch; ${supersededReviews.size} review(s) on later bookings no longer current"

    if (dryRun) {
      log.info("Dry run — would have $message")
      return BookingSwitchRepairResult(
        prisonerNumber = prisonerNumber,
        bookingId = correctBookingId,
        outcome = BookingSwitchRepairOutcome.REPAIRED,
        dryRun = true,
        levelCodeBefore = before?.review?.levelCode,
        // of the reviews left current afterwards, the newest is what a prisoner-scoped read picks
        levelCodeAfter = reviews.firstOrNull {
          it.id == reviewToReinstate?.id || (it.current && it.id !in supersededIds)
        }?.levelCode,
        reviewIdsStoodDown = supersededIds,
        reviewIdReinstated = reviewToReinstate?.id,
        message = "Dry run — would have $message",
      )
    }

    val changes = transactionalOperator.executeAndAwait {
      // Stand the mistaken booking down before reinstating the correct one; they are different
      // bookings, so the per-booking unique index on current = true is never violated
      incentiveReviewRepository
        .saveAll(supersededReviews.map { it.copy(current = false, new = false) })
        .collect {}
      reviewToReinstate?.let { incentiveReviewRepository.save(it.copy(current = true, new = false)) }
      nextReviewDateUpdaterService.updateWriteOnly(correctBookingId)
    }
    nextReviewDateUpdaterService.publishDomainEvents(changes)

    log.info(message)
    val after = publishIfCurrentReviewChanged(prisonerNumber, before)
    auditService.sendMessage(
      AuditType.PRISONER_BOOKING_SWITCHED,
      prisonerNumber,
      message,
      repairedBy,
    )

    return BookingSwitchRepairResult(
      prisonerNumber = prisonerNumber,
      bookingId = correctBookingId,
      outcome = BookingSwitchRepairOutcome.REPAIRED,
      dryRun = false,
      levelCodeBefore = before?.review?.levelCode,
      levelCodeAfter = after?.review?.levelCode,
      reviewIdsStoodDown = supersededIds,
      reviewIdReinstated = reviewToReinstate?.id,
      message = message,
    )
  }

  private suspend fun mergedPrisonerDetails(prisonerMergeEvent: HMPPSDomainEvent) {
    val removedPrisonerNumber = prisonerMergeEvent.additionalInformation?.removedNomsNumber!!
    val remainingPrisonerNumber = prisonerMergeEvent.additionalInformation.nomsNumber!!
    log.info("Processing merge event: Prisoner Number Merge $removedPrisonerNumber -> $remainingPrisonerNumber")

    val survivorBefore = currentReviewSnapshotFor(remainingPrisonerNumber)

    val activeReviews = incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(removedPrisonerNumber)
      .map { review -> review.copy(prisonerNumber = remainingPrisonerNumber) }

    val remainingBookingId = prisonerSearchService.getPrisonerInfo(remainingPrisonerNumber).bookingId
    val reviewsFromOldBooking =
      incentiveReviewRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(remainingPrisonerNumber)
        .map { review -> review.copy(bookingId = remainingBookingId, current = false, new = true, id = 0) }

    val reviewsToUpdate = merge(activeReviews, reviewsFromOldBooking).toList()
    incentiveStoreService.updateMergedReviews(reviewsToUpdate, remainingBookingId)

    val numberUpdated = reviewsToUpdate.count()
    if (numberUpdated > 0) {
      val message =
        "$numberUpdated incentive records updated from merge $removedPrisonerNumber -> $remainingPrisonerNumber. Updated to booking ID $remainingBookingId"
      log.info(message)
      auditService.sendMessage(
        AuditType.PRISONER_NUMBER_MERGE,
        remainingPrisonerNumber,
        message,
        SYSTEM_USERNAME,
      )
      publishIfCurrentReviewChanged(remainingPrisonerNumber, survivorBefore)
    } else {
      log.info("No incentive records found for $removedPrisonerNumber, no records updated")
    }
  }

  @Transactional
  suspend fun processBookingMovedEvent(bookingMovedEvent: HMPPSBookingMovedDomainEvent) {
    val bookingId = bookingMovedEvent.additionalInformation.bookingId
    val removedPrisonerNumber = bookingMovedEvent.additionalInformation.movedFromNomsNumber
    val remainingPrisonerNumber = bookingMovedEvent.additionalInformation.movedToNomsNumber
    log.info("Moving incentive reviews for booking $bookingId from $removedPrisonerNumber to $remainingPrisonerNumber")

    val movedFromBefore = currentReviewSnapshotFor(removedPrisonerNumber)
    val movedToBefore = currentReviewSnapshotFor(remainingPrisonerNumber)

    incentiveReviewRepository.saveAll(
      incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)
        .toList()
        .filter { it.prisonerNumber == removedPrisonerNumber }
        .onEach { it.prisonerNumber = remainingPrisonerNumber },
    ).collect {}

    publishIfCurrentReviewChanged(removedPrisonerNumber, movedFromBefore)
    publishIfCurrentReviewChanged(remainingPrisonerNumber, movedToBefore)
  }
}

class IncentiveReviewNotFoundException(
  message: String,
) : RuntimeException(message)
