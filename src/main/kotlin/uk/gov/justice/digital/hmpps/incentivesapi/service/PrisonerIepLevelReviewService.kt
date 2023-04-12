package uk.gov.justice.digital.hmpps.incentivesapi.service

import jakarta.validation.ValidationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CurrentIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveRecordUpdate
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Supplier

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val iepLevelService: IepLevelService,
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val authenticationFacade: AuthenticationFacade,
  private val clock: Clock,
  private val nextReviewDateGetterService: NextReviewDateGetterService,
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService,
  private val incentiveStoreService: IncentiveStoreService,
  private val incentiveLevelService: IncentiveLevelService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getPrisonerIepLevelHistory(
    bookingId: Long,
    withDetails: Boolean = true,
  ): IepSummary {
    val reviews = prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)
    if (reviews.count() == 0) throw IncentiveReviewNotFoundException("No Incentive Reviews for booking ID $bookingId")
    return buildIepSummary(reviews, incentiveLevelService.getAllIncentiveLevelsMapByCode(), withDetails)
  }

  suspend fun getPrisonerIepLevelHistory(prisonerNumber: String): IepSummary {
    val reviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber)
    if (reviews.count() == 0) {
      throw IncentiveReviewNotFoundException("No Incentive Reviews for prisoner number $prisonerNumber")
    }
    return buildIepSummary(reviews, incentiveLevelService.getAllIncentiveLevelsMapByCode())
  }

  suspend fun addIepReview(prisonerNumber: String, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
    return addIepReviewForPrisonerAtLocation(prisonerInfo, iepReview)
  }

  suspend fun addIepReview(bookingId: Long, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId)
    return addIepReviewForPrisonerAtLocation(prisonerInfo, iepReview)
  }

  suspend fun updateIncentiveRecord(
    bookingId: Long,
    id: Long,
    update: IncentiveRecordUpdate,
  ): IepDetail {
    if (listOf(update.reviewTime, update.comment, update.current).all { it == null }) {
      throw ValidationException("Please provide fields to update")
    }

    val prisonerIepLevel = prisonerIepLevelRepository.findById(id) ?: throw NoDataFoundException(id)

    // Check bookingId on found record matches the bookingId provided
    if (prisonerIepLevel.bookingId != bookingId) {
      log.warn("Patch of PrisonerIepLevel with ID $id failed because provided bookingID ($bookingId) didn't match bookingId on DB record (${prisonerIepLevel.bookingId})")
      throw NoDataFoundException(bookingId)
    }

    val iepDetail = incentiveStoreService.updateIncentiveRecord(update, prisonerIepLevel)
      .toIepDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())

    publishReviewDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_UPDATED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_UPDATED)

    return iepDetail
  }

  suspend fun deleteIncentiveRecord(bookingId: Long, id: Long) {
    val prisonerIepLevel: PrisonerIepLevel? = prisonerIepLevelRepository.findById(id)
    if (prisonerIepLevel == null) {
      log.debug("PrisonerIepLevel with ID $id not found")
      throw NoDataFoundException(id)
    }
    // Check bookingId on found record matches the bookingId provided
    if (prisonerIepLevel.bookingId != bookingId) {
      log.warn("Delete of PrisonerIepLevel with ID $id failed because provided bookingID ($bookingId) didn't match bookingId on DB record (${prisonerIepLevel.bookingId})")
      throw NoDataFoundException(bookingId)
    }

    incentiveStoreService.deleteIncentiveRecord(prisonerIepLevel)

    val iepDetail = prisonerIepLevel.toIepDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())
    publishReviewDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_DELETED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_DELETED)
  }

  suspend fun getCurrentIEPLevelForPrisoners(bookingIds: List<Long>): List<CurrentIepLevel> {
    val incentiveLevels = incentiveLevelService.getAllIncentiveLevelsMapByCode()
    return prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
      .map {
        CurrentIepLevel(
          iepLevel = incentiveLevels[it.iepCode]?.name ?: "Unmapped",
          bookingId = it.bookingId,
        )
      }.toList()
  }

  suspend fun getReviewById(id: Long): IepDetail =
    prisonerIepLevelRepository.findById(id)?.toIepDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode()) ?: throw NoDataFoundException(id)

  suspend fun processOffenderEvent(prisonOffenderEvent: HMPPSDomainEvent) =
    when (prisonOffenderEvent.additionalInformation?.reason) {
      "NEW_ADMISSION" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.INITIAL)
      // NOTE: This may NOT be a recall. Someone could be readmitted back to prison for a number of other reasons (e.g. remands)
      "READMISSION" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.READMISSION)
      "TRANSFERRED" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.TRANSFER)
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
      log.debug("Ignoring 'prisoner-offender-search.prisoner.alerts-updated' event, No ACCT alerts added/removed: prisonerNumber = ${prisonOffenderEvent.additionalInformation?.nomsNumber}, alertsAdded = ${prisonOffenderEvent.additionalInformation?.alertsAdded}, alertsRemoved = ${prisonOffenderEvent.additionalInformation?.alertsRemoved}")
    }
  }

  private suspend fun updateNextReviewDate(prisonOffenderEvent: HMPPSDomainEvent) {
    prisonOffenderEvent.additionalInformation?.bookingId?.let { bookingId ->
      nextReviewDateUpdaterService.update(bookingId)
    } ?: run {
      log.error("Could not update next review date: bookingId null for prisonOffenderEvent: $prisonOffenderEvent")
    }
  }

  private suspend fun createIepForReceivedPrisoner(prisonOffenderEvent: HMPPSDomainEvent, reviewType: ReviewType) {
    prisonOffenderEvent.additionalInformation?.nomsNumber?.let {
      val prisonerInfo = prisonApiService.getPrisonerInfo(it, true)
      val iepLevel = getIepLevelForReviewType(prisonerInfo, reviewType)
      val comment = getReviewCommentForEvent(prisonOffenderEvent)

      val iepReview = IepReview(
        iepLevel = iepLevel,
        comment = comment,
        reviewType = reviewType,
      )

      val locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId, true)

      val prisonerIepLevel = incentiveStoreService.saveIncentiveReview(

        PrisonerIepLevel(
          iepCode = iepReview.iepLevel,
          commentText = iepReview.comment,
          bookingId = prisonerInfo.bookingId,
          prisonId = locationInfo.agencyId,
          locationId = locationInfo.description,
          current = true,
          reviewedBy = SYSTEM_USERNAME,
          reviewTime = LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
          reviewType = iepReview.reviewType ?: ReviewType.REVIEW,
          prisonerNumber = prisonerInfo.offenderNo,
        ),
      )

      val iepDetail = prisonerIepLevel.toIepDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())
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

  private suspend fun getIepLevelForReviewType(prisonerInfo: PrisonerAtLocation, reviewType: ReviewType): String {
    val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonerInfo.agencyId, useClientCredentials = true)
    val defaultLevelCode = iepLevelService.chooseDefaultLevel(prisonerInfo.agencyId, iepLevelsForPrison)

    return when (reviewType) {
      ReviewType.INITIAL, ReviewType.READMISSION -> {
        defaultLevelCode // admission should always be the default
      }
      ReviewType.TRANSFER -> {
        try {
          val iepHistory =
            getPrisonerIepLevelHistory(
              prisonerInfo.bookingId,
              withDetails = true,
            ).iepDetails
          val levelCodeBeforeTransfer =
            iepHistory.sortedBy(IepDetail::iepTime).lastOrNull { it.agencyId != prisonerInfo.agencyId }?.iepCode
              ?: defaultLevelCode // if no previous prison
          iepLevelService.findNearestHighestLevel(prisonerInfo.agencyId, iepLevelsForPrison, levelCodeBeforeTransfer)
        } catch (e: IncentiveReviewNotFoundException) {
          defaultLevelCode // this is to handle no reviews - only an issue before migration
        }
      }

      else -> throw NotImplementedError("Not implemented for $reviewType")
    }
  }

  private fun getReviewCommentForEvent(prisonOffenderEvent: HMPPSDomainEvent) = when (prisonOffenderEvent.additionalInformation?.reason) {
    "NEW_ADMISSION", "READMISSION" -> "Default level assigned on arrival"
    "TRANSFERRED" -> "Level transferred from previous establishment"
    else -> prisonOffenderEvent.description
  }

  private suspend fun buildIepSummary(
    levels: Flow<PrisonerIepLevel>,
    incentiveLevels: Map<String, IncentiveLevel>,
    withDetails: Boolean = true,
  ): IepSummary {
    val iepDetails = levels.map { it.toIepDetail(incentiveLevels) }.toList()

    val currentIep = iepDetails.firstOrNull() ?: throw IncentiveReviewNotFoundException("Not Found incentive reviews")

    val iepSummary = IepSummary(
      bookingId = currentIep.bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepCode = currentIep.iepCode,
      iepLevel = currentIep.iepLevel,
      id = currentIep.id,
      prisonerNumber = currentIep.prisonerNumber,
      locationId = currentIep.locationId,
      iepDetails = iepDetails,
      nextReviewDate = nextReviewDateGetterService.get(currentIep.bookingId),
    )

    if (!withDetails) {
      iepSummary.iepDetails = emptyList()
    }

    return iepSummary
  }

  suspend fun addIepReviewForPrisonerAtLocation(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview,
  ): IepDetail {
    val locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId)

    val reviewTime = LocalDateTime.now(clock)
    val reviewerUserName = authenticationFacade.getUsername()

    val newIepReview = incentiveStoreService.saveIncentiveReview(
      PrisonerIepLevel(
        iepCode = iepReview.iepLevel,
        commentText = iepReview.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = locationInfo.agencyId,
        locationId = locationInfo.description,
        current = true,
        reviewedBy = reviewerUserName,
        reviewTime = reviewTime,
        reviewType = iepReview.reviewType ?: ReviewType.REVIEW,
        prisonerNumber = prisonerInfo.offenderNo,
      ),
    ).toIepDetail(incentiveLevelService.getAllIncentiveLevelsMapByCode())

    // Propagate new IEP review to other services
    publishReviewDomainEvent(newIepReview, IncentivesDomainEventType.IEP_REVIEW_INSERTED)

    publishAuditEvent(newIepReview, AuditType.IEP_REVIEW_ADDED)

    return newIepReview
  }

  private suspend fun publishReviewDomainEvent(
    iepDetail: IepDetail,
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
      occurredAt = iepDetail.iepTime,
      AdditionalInformation(
        id = iepDetail.id,
        nomsNumber = iepDetail.prisonerNumber,
      ),
    )
  }

  private suspend fun publishAuditEvent(
    iepDetail: IepDetail,
    auditType: AuditType,
  ) {
    auditService.sendMessage(
      auditType,
      iepDetail.id.toString(),
      iepDetail,
      iepDetail.userId,
    )
  }

  @Transactional
  suspend fun mergedPrisonerDetails(prisonerMergeEvent: HMPPSDomainEvent) {
    val removedPrisonerNumber = prisonerMergeEvent.additionalInformation?.removedNomsNumber!!
    val remainingPrisonerNumber = prisonerMergeEvent.additionalInformation.nomsNumber!!
    log.info("Processing merge event: Prisoner Number Merge $removedPrisonerNumber -> $remainingPrisonerNumber")

    val activeReviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(removedPrisonerNumber)
      .map { review -> review.copy(prisonerNumber = remainingPrisonerNumber) }

    val remainingBookingId = prisonApiService.getPrisonerInfo(remainingPrisonerNumber, true).bookingId
    val reviewsFromOldBooking =
      prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(remainingPrisonerNumber)
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
}

@OptIn(FlowPreview::class)
fun <T> merge(vararg flows: Flow<T>): Flow<T> = flowOf(*flows).flattenMerge()

class IncentiveReviewNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<IncentiveReviewNotFoundException> {
  override fun get(): IncentiveReviewNotFoundException {
    return IncentiveReviewNotFoundException(message)
  }
}
