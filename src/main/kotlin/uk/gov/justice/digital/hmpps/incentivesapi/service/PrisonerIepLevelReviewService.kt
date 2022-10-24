package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.FeatureFlagsService
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.config.ReviewAddedSyncMechanism
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CurrentIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPatchRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Supplier
import javax.validation.ValidationException

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val iepLevelService: IepLevelService,
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val authenticationFacade: AuthenticationFacade,
  private val clock: Clock,
  private val featureFlagsService: FeatureFlagsService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getPrisonerIepLevelHistory(
    bookingId: Long,
    withDetails: Boolean = true,
    useClientCredentials: Boolean = false,
  ): IepSummary {
    return if (!featureFlagsService.isIncentivesDataSourceOfTruth()) {
      prisonApiService.getIEPSummaryForPrisoner(bookingId, withDetails, useClientCredentials)
    } else {
      val reviews = prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId)
      if (reviews.count() == 0) throw IncentiveReviewNotFoundException("No Incentive Reviews for booking ID $bookingId")
      buildIepSummary(reviews, getIncentiveLevels(), withDetails)
    }
  }

  suspend fun getPrisonerIepLevelHistory(prisonerNumber: String): IepSummary {

    return if (!featureFlagsService.isIncentivesDataSourceOfTruth()) {
      val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
      prisonApiService.getIEPSummaryPerPrisoner(listOf(prisonerInfo.bookingId)).first()
    } else {
      val reviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber)
      if (reviews.count() == 0) throw IncentiveReviewNotFoundException("No Incentive Reviews for prisoner number $prisonerNumber")
      buildIepSummary(reviews, getIncentiveLevels())
    }
  }

  @Transactional
  suspend fun addIepReview(prisonerNumber: String, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
    return addIepReviewForPrisonerAtLocation(prisonerInfo, iepReview)
  }

  @Transactional
  suspend fun addIepReview(bookingId: Long, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId)
    return addIepReviewForPrisonerAtLocation(prisonerInfo, iepReview)
  }

  @Transactional
  suspend fun persistSyncPostRequest(
    bookingId: Long,
    syncPostRequest: SyncPostRequest,
    includeLocation: Boolean
  ): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId, true)
    var locationInfo: Location? = null
    if (includeLocation) {
      locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId, true)
    }

    if (syncPostRequest.current) {
      updateIepLevelsWithCurrentFlagToFalse(bookingId)
    }

    return prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        iepCode = syncPostRequest.iepLevel,
        commentText = syncPostRequest.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = syncPostRequest.prisonId,
        locationId = locationInfo?.description,
        current = syncPostRequest.current,
        reviewedBy = syncPostRequest.userId,
        reviewTime = syncPostRequest.iepTime,
        reviewType = syncPostRequest.reviewType,
        prisonerNumber = prisonerInfo.offenderNo
      )
    ).translate(getIncentiveLevels())
  }

  suspend fun handleSyncPostIepReviewRequest(bookingId: Long, syncPostRequest: SyncPostRequest): IepDetail {
    val iepDetail = persistSyncPostRequest(bookingId, syncPostRequest, true)

    publishDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_ADDED)

    return iepDetail
  }

  suspend fun handleSyncPatchIepReviewRequest(
    bookingId: Long,
    id: Long,
    syncPatchRequest: SyncPatchRequest
  ): IepDetail {
    if (listOf(syncPatchRequest.iepTime, syncPatchRequest.comment, syncPatchRequest.current).all { it == null }) {
      throw ValidationException("Please provide fields to update")
    }

    var prisonerIepLevel: PrisonerIepLevel? = prisonerIepLevelRepository.findById(id)
    if (prisonerIepLevel == null) {
      log.debug("PrisonerIepLevel with ID $id not found")
      throw NoDataFoundException(id)
    }
    // Check bookingId on found record matches the bookingId provided
    if (prisonerIepLevel.bookingId != bookingId) {
      log.warn("Patch of PrisonerIepLevel with ID $id failed because provided bookingID ($bookingId) didn't match bookingId on DB record (${prisonerIepLevel.bookingId})")
      throw NoDataFoundException(bookingId)
    }

    syncPatchRequest.current?.let {
      updateIepLevelsWithCurrentFlagToFalse(bookingId)
    }

    prisonerIepLevel = prisonerIepLevel.copy(
      reviewTime = syncPatchRequest.iepTime ?: prisonerIepLevel.reviewTime,
      commentText = syncPatchRequest.comment ?: prisonerIepLevel.commentText,
      current = syncPatchRequest.current ?: prisonerIepLevel.current,
    )

    val iepDetail = prisonerIepLevelRepository.save(prisonerIepLevel).translate(getIncentiveLevels())

    publishDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_UPDATED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_UPDATED)

    return iepDetail
  }

  suspend fun handleSyncDeleteIepReviewRequest(bookingId: Long, id: Long) {
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

    prisonerIepLevelRepository.delete(prisonerIepLevel)

    // If the deleted record had `current=true`, the latest IEP review becomes current
    prisonerIepLevel.current.let {
      // The deleted record was current, set new current to the latest IEP review
      prisonerIepLevelRepository.findFirstByBookingIdOrderByReviewTimeDesc(bookingId)?.run {
        prisonerIepLevelRepository.save(this.copy(current = true))
      }
    }

    val iepDetail = prisonerIepLevel.translate(getIncentiveLevels())
    publishDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_DELETED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_DELETED)
  }

  suspend fun getCurrentIEPLevelForPrisoners(bookingIds: List<Long>): Flow<CurrentIepLevel> {
    return if (!featureFlagsService.isIncentivesDataSourceOfTruth()) {
      prisonApiService.getIEPSummaryPerPrisoner(bookingIds)
        .map {
          CurrentIepLevel(iepLevel = it.iepLevel, bookingId = it.bookingId)
        }
    } else {
      val incentiveLevels = getIncentiveLevels()
      prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
        .map {
          CurrentIepLevel(
            iepLevel = incentiveLevels[it.iepCode]?.iepDescription ?: "Unmapped",
            bookingId = it.bookingId
          )
        }
    }
  }

  private suspend fun getIncentiveLevels(): Map<String, IepLevel> {
    return prisonApiService.getIepLevels().toList().associateBy { iep -> iep.iepLevel }
  }

  suspend fun getReviewById(id: Long): IepDetail =
    prisonerIepLevelRepository.findById(id)?.translate(getIncentiveLevels()) ?: throw NoDataFoundException(id)

  @Transactional
  suspend fun processOffenderEvent(prisonOffenderEvent: HMPPSDomainEvent) =
    when (prisonOffenderEvent.additionalInformation.reason) {
      "ADMISSION" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.INITIAL)
      "TRANSFERRED" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.TRANSFER)
      "MERGE" -> mergedPrisonerDetails(prisonOffenderEvent)
      else -> {
        log.debug("Ignoring prisonOffenderEvent with reason ${prisonOffenderEvent.additionalInformation.reason}")
      }
    }

  private suspend fun createIepForReceivedPrisoner(prisonOffenderEvent: HMPPSDomainEvent, reviewType: ReviewType) {
    prisonOffenderEvent.additionalInformation.nomsNumber?.let {
      val prisonerInfo = prisonApiService.getPrisonerInfo(it, true)
      val iepLevel = getIepLevelForReviewType(prisonerInfo, reviewType)

      val iepReview = IepReview(
        iepLevel = iepLevel,
        comment = prisonOffenderEvent.description,
        reviewType = reviewType
      )

      val locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId, true)
      val prisonerIepLevel = persistIepLevel(
        prisonerInfo,
        iepReview,
        locationInfo,
        LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        "INCENTIVES_API"
      )

      publishDomainEvent(
        prisonerIepLevel.translate(getIncentiveLevels()),
        IncentivesDomainEventType.IEP_REVIEW_INSERTED,
      )
      publishAuditEvent(
        prisonerIepLevel.translate(getIncentiveLevels()),
        AuditType.IEP_REVIEW_ADDED,
      )
    } ?: run {
      log.warn("prisonerNumber null for prisonOffenderEvent: $prisonOffenderEvent ")
    }
  }

  private suspend fun getIepLevelForReviewType(prisonerInfo: PrisonerAtLocation, reviewType: ReviewType): String {
    val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonerInfo.agencyId, useClientCredentials = true)
    val defaultLevel = iepLevelsForPrison.find(IepLevel::default) ?: iepLevelsForPrison.first() // if no default use the lowest sequence
    val iepLevel = when (reviewType) {
      ReviewType.INITIAL -> {
        defaultLevel // admission should always be the default
      }
      ReviewType.TRANSFER -> {
        try {
          val iepHistory =
            getPrisonerIepLevelHistory(
              prisonerInfo.bookingId,
              withDetails = true,
              useClientCredentials = true
            ).iepDetails
          val iepLevelBeforeTransfer =
            iepHistory.sortedBy(IepDetail::iepTime).lastOrNull { it.agencyId != prisonerInfo.agencyId }?.iepCode
              ?: defaultLevel // if no previous prison
          iepLevelsForPrison.find { it.iepLevel == iepLevelBeforeTransfer }
            ?: defaultLevel // if we don't match a level - go to the default.
        } catch (e: IncentiveReviewNotFoundException) {
          defaultLevel // this is to handle no reviews - only an issue before migration
        }
      }

      else -> throw NotImplementedError("Not implemented for $reviewType")
    }
    return iepLevel.iepLevel
  }

  private suspend fun buildIepSummary(
    levels: Flow<PrisonerIepLevel>,
    incentiveLevels: Map<String, IepLevel>,
    withDetails: Boolean = true
  ): IepSummary {
    val iepLevels = levels.map { it.translate(incentiveLevels) }

    val currentIep = iepLevels.firstOrNull() ?: throw IncentiveReviewNotFoundException("Not Found incentive reviews")

    return IepSummary(
      bookingId = currentIep.bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepLevel = currentIep.iepLevel,
      id = currentIep.id,
      prisonerNumber = currentIep.prisonerNumber,
      locationId = currentIep.locationId,
      iepDetails = if (withDetails) iepLevels.toList() else emptyList(),
    )
  }

  private suspend fun addIepReviewForPrisonerAtLocation(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview,
  ): IepDetail {
    val locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId)

    val reviewTime = LocalDateTime.now(clock)
    val reviewerUserName = authenticationFacade.getUsername()

    val newIepReview = persistIepLevel(
      prisonerInfo,
      iepReview,
      locationInfo,
      reviewTime,
      reviewerUserName
    ).translate(getIncentiveLevels())

    // Propagate new IEP review to other services
    if (featureFlagsService.reviewAddedSyncMechanism() == ReviewAddedSyncMechanism.DOMAIN_EVENT) {
      publishDomainEvent(newIepReview, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
    } else {
      prisonApiService.addIepReview(
        prisonerInfo.bookingId,
        IepReviewInNomis(
          iepLevel = iepReview.iepLevel,
          comment = iepReview.comment,
          reviewTime = reviewTime,
          reviewerUserName = reviewerUserName
        )
      )
    }

    publishAuditEvent(newIepReview, AuditType.IEP_REVIEW_ADDED)

    return newIepReview
  }

  suspend fun persistIepLevel(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview,
    locationInfo: Location,
    reviewTime: LocalDateTime,
    reviewerUserName: String,
  ): PrisonerIepLevel {
    updateIepLevelsWithCurrentFlagToFalse(prisonerInfo.bookingId)

    return prisonerIepLevelRepository.save(
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
        prisonerNumber = prisonerInfo.offenderNo
      )
    )
  }

  private suspend fun publishDomainEvent(
    iepDetail: IepDetail,
    eventType: IncentivesDomainEventType,
  ) {
    iepDetail.id?.let {
      snsService.sendIepReviewEvent(iepDetail.id, iepDetail.prisonerNumber ?: "N/A", iepDetail.iepTime, eventType)
    } ?: run {
      log.warn("IepDetail has `null` id, domain event not published: $iepDetail")
    }
  }

  private suspend fun publishAuditEvent(
    iepDetail: IepDetail,
    auditType: AuditType,
  ) {
    iepDetail.id?.let {
      auditService.sendMessage(
        auditType,
        iepDetail.id.toString(),
        iepDetail,
        iepDetail.userId,
      )
    } ?: run {
      log.warn("IepDetail has `null` id, audit event not published: $iepDetail")
    }
  }

  private suspend fun updateIepLevelsWithCurrentFlagToFalse(bookingId: Long) {
    prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(bookingId))
      .collect {
        prisonerIepLevelRepository.save(it.copy(current = false))
      }
  }

  private suspend fun PrisonerIepLevel.translate(incentiveLevels: Map<String, IepLevel>) =
    IepDetail(
      id = id,
      bookingId = bookingId,
      iepDate = reviewTime.toLocalDate(),
      iepTime = reviewTime,
      agencyId = prisonId,
      iepLevel = incentiveLevels[iepCode]?.iepDescription ?: "Unmapped",
      iepCode = iepCode,
      reviewType = reviewType,
      comments = commentText,
      userId = reviewedBy,
      locationId = locationId,
      prisonerNumber = prisonerNumber,
      auditModuleName = "INCENTIVES_API",
    )

  @Transactional
  suspend fun mergedPrisonerDetails(prisonerMergeEvent: HMPPSDomainEvent) {

    val removedPrisonerNumber = prisonerMergeEvent.additionalInformation.removedNomsNumber!!
    val remainingPrisonerNumber = prisonerMergeEvent.additionalInformation.nomsNumber!!
    log.info("Processing merge event: Prisoner Number Merge $removedPrisonerNumber -> $remainingPrisonerNumber")

    val activeReviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(removedPrisonerNumber)
      .map { review -> review.copy(prisonerNumber = remainingPrisonerNumber) }

    val remainingBookingId = prisonApiService.getPrisonerInfo(remainingPrisonerNumber, true).bookingId
    val inactiveReviews =
      prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(remainingPrisonerNumber)
        .map { review -> review.copy(bookingId = remainingBookingId, current = false) }

    val reviewsToUpdate = merge(activeReviews, inactiveReviews)
    reviewsToUpdate.collect {
      prisonerIepLevelRepository.save(it)
    }

    val numberUpdated = reviewsToUpdate.count()
    if (numberUpdated > 0) {
      val message =
        "$numberUpdated incentive records updated from merge $removedPrisonerNumber -> $remainingPrisonerNumber. Updated to booking ID $remainingBookingId"
      log.info(message)
      auditService.sendMessage(
        AuditType.PRISONER_NUMBER_MERGE,
        remainingPrisonerNumber,
        message,
        "INCENTIVES_API"
      )
    } else {
      log.info("No incentive records found for $removedPrisonerNumber, no records updated")
    }
  }
}

@OptIn(FlowPreview::class)
fun <T> merge(vararg flows: Flow<T>): Flow<T> = flowOf(*flows).flattenMerge()

data class IepReviewInNomis(
  val iepLevel: String,
  val comment: String,
  val reviewTime: LocalDateTime,
  val reviewerUserName: String,
)

class IncentiveReviewNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<IncentiveReviewNotFoundException> {
  override fun get(): IncentiveReviewNotFoundException {
    return IncentiveReviewNotFoundException(message)
  }
}
