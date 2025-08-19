package uk.gov.justice.digital.hmpps.incentivesapi.service

import jakarta.validation.ValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerBasicInfo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.findDefaultOnAdmission
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PrisonerIncentiveReviewService(
  private val prisonApiService: PrisonApiService,
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
  private val incentiveStoreService: IncentiveStoreService,
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
      "MERGE" -> mergedPrisonerDetails(prisonOffenderEvent)
      else -> {
        log.debug("Ignoring prisonOffenderEvent with reason ${prisonOffenderEvent.additionalInformation?.reason}")
      }
    }

  @Transactional
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
      nextReviewDateUpdaterService.update(bookingId)
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
    val iepDetails = levels.map { it.toIncentiveReviewDetail(incentiveLevels) }.toList()

    val currentIep = iepDetails.firstOrNull() ?: throw IncentiveReviewNotFoundException("Not Found incentive reviews")

    val incentiveReviewSummary = IncentiveReviewSummary(
      bookingId = currentIep.bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepCode = currentIep.iepCode,
      iepLevel = currentIep.iepLevel,
      id = currentIep.id,
      prisonerNumber = currentIep.prisonerNumber,
      locationId = currentIep.locationId,
      incentiveReviewDetails = iepDetails,
      nextReviewDate = nextReviewDateGetterService.get(currentIep.bookingId),
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

  private suspend fun mergedPrisonerDetails(prisonerMergeEvent: HMPPSDomainEvent) {
    val removedPrisonerNumber = prisonerMergeEvent.additionalInformation?.removedNomsNumber!!
    val remainingPrisonerNumber = prisonerMergeEvent.additionalInformation.nomsNumber!!
    log.info("Processing merge event: Prisoner Number Merge $removedPrisonerNumber -> $remainingPrisonerNumber")

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
    incentiveReviewRepository.saveAll(
      incentiveReviewRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)
        .toList()
        .filter { it.prisonerNumber == removedPrisonerNumber }
        .onEach { it.prisonerNumber = remainingPrisonerNumber },
    ).collect {}
  }
}

class IncentiveReviewNotFoundException(
  message: String,
) : RuntimeException(message)
