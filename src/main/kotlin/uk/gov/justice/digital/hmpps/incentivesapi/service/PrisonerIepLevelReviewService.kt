package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.config.NoDataFoundException
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CurrentIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPatchRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.validation.ValidationException

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val iepLevelRepository: IepLevelRepository,
  private val iepLevelService: IepLevelService,
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val authenticationFacade: AuthenticationFacade,
  private val clock: Clock,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getPrisonerIepLevelHistory(
    bookingId: Long,
    useNomisData: Boolean = true,
    withDetails: Boolean = true,
    useClientCredentials: Boolean = false,
  ): IepSummary {
    return if (useNomisData) {
      prisonApiService.getIEPSummaryForPrisoner(bookingId, withDetails, useClientCredentials)
    } else {
      buildIepSummary(prisonerIepLevelRepository.findAllByBookingIdOrderByReviewTimeDesc(bookingId), withDetails)
    }
  }

  suspend fun getPrisonerIepLevelHistory(prisonerNumber: String, useNomisData: Boolean = true): IepSummary {

    return if (useNomisData) {
      val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
      prisonApiService.getIEPSummaryPerPrisoner(listOf(prisonerInfo.bookingId)).first()
    } else {
      buildIepSummary(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(prisonerNumber))
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
  suspend fun persistSyncPostRequest(bookingId: Long, syncPostRequest: SyncPostRequest, includeLocation: Boolean): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId, true)
    var locationInfo: Location? = null
    if (includeLocation) {
      locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId, true)
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
    ).translate()
  }

  suspend fun handleSyncPostIepReviewRequest(bookingId: Long, syncPostRequest: SyncPostRequest): IepDetail {
    val iepDetail = persistPrisonerIepLevel(bookingId, syncPostRequest, true)

    publishDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_ADDED)

    return iepDetail
  }

  suspend fun handleSyncPatchIepReviewRequest(bookingId: Long, id: Long, syncPatchRequest: SyncPatchRequest): IepDetail {
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

    prisonerIepLevel = prisonerIepLevel.copy(
      reviewTime = syncPatchRequest.iepTime ?: prisonerIepLevel.reviewTime,
      commentText = syncPatchRequest.comment ?: prisonerIepLevel.commentText,
      current = syncPatchRequest.current ?: prisonerIepLevel.current,
    )

    val iepDetail = prisonerIepLevelRepository.save(prisonerIepLevel).translate()

    publishDomainEvent(iepDetail, IncentivesDomainEventType.IEP_REVIEW_UPDATED)
    publishAuditEvent(iepDetail, AuditType.IEP_REVIEW_UPDATED)

    return iepDetail
  }

  fun getCurrentIEPLevelForPrisoners(bookingIds: List<Long>, useNomisData: Boolean): Flow<CurrentIepLevel> {
    return if (useNomisData) {
      prisonApiService.getIEPSummaryPerPrisoner(bookingIds)
        .map {
          CurrentIepLevel(iepLevel = it.iepLevel, bookingId = it.bookingId)
        }
    } else {
      prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(bookingIds)
        .map {
          CurrentIepLevel(iepLevel = iepLevelRepository.findById(it.iepCode)?.iepDescription ?: "Unmapped", bookingId = it.bookingId)
        }
    }
  }

  suspend fun getReviewById(id: Long): IepDetail =
    prisonerIepLevelRepository.findById(id)?.translate() ?: throw NoDataFoundException(id)

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
        "incentives-api"
      )

      publishDomainEvent(
        prisonerIepLevel.translate(),
        IncentivesDomainEventType.IEP_REVIEW_INSERTED,
      )
      publishAuditEvent(
        prisonerIepLevel.translate(),
        AuditType.IEP_REVIEW_ADDED,
      )
    } ?: run {
      log.warn("prisonerNumber null for prisonOffenderEvent: $prisonOffenderEvent ")
    }
  }

  private suspend fun getIepLevelForReviewType(prisonerInfo: PrisonerAtLocation, reviewType: ReviewType): String {
    val iepLevelsForPrison = iepLevelService.getIepLevelsForPrison(prisonerInfo.agencyId)
    val iepLevel = when (reviewType) {
      ReviewType.INITIAL -> iepLevelsForPrison.first(IepLevel::default)

      ReviewType.TRANSFER -> {
        val iepHistory = getPrisonerIepLevelHistory(prisonerInfo.bookingId, useNomisData = true, withDetails = true, useClientCredentials = true).iepDetails
        val iepLevelBeforeTransfer = iepHistory.sortedBy(IepDetail::iepTime).lastOrNull { it.agencyId != prisonerInfo.agencyId }
          ?: throw NoDataFoundException(prisonerInfo.bookingId)
        iepLevelsForPrison.find { it.iepDescription == iepLevelBeforeTransfer.iepLevel }
          // ...or the highest level in the prison
          ?: iepLevelsForPrison.last()
      }

      else -> throw NotImplementedError("Not implemented for $reviewType")
    }
    return iepLevel.iepLevel
  }

  private suspend fun buildIepSummary(levels: Flow<PrisonerIepLevel>, withDetails: Boolean = true): IepSummary {
    val iepLevels = levels.map { it.translate() }.toList()

    val currentIep = iepLevels.first()
    return IepSummary(
      bookingId = currentIep.bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepLevel = currentIep.iepLevel,
      id = currentIep.id,
      prisonerNumber = currentIep.prisonerNumber,
      locationId = currentIep.locationId,
      iepDetails = if (withDetails) iepLevels else emptyList(),
    )
  }

  private suspend fun addIepReviewForPrisonerAtLocation(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview
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
    ).translate()

    prisonApiService.addIepReview(
      prisonerInfo.bookingId,
      IepReviewInNomis(
        iepLevel = iepReview.iepLevel,
        comment = iepReview.comment,
        reviewTime = reviewTime,
        reviewerUserName = reviewerUserName
      )
    )

    publishDomainEvent(newIepReview, IncentivesDomainEventType.IEP_REVIEW_INSERTED)
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
    prisonerIepLevelRepository.findFirstByBookingIdAndCurrentIsTrueOrderByReviewTimeDesc(prisonerInfo.bookingId)
      ?.let {
        prisonerIepLevelRepository.save(it.copy(current = false))
      }

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

  private suspend fun PrisonerIepLevel.translate() =
    IepDetail(
      id = id,
      bookingId = bookingId,
      iepDate = reviewTime.toLocalDate(),
      iepTime = reviewTime,
      agencyId = prisonId,
      iepLevel = iepLevelRepository.findById(iepCode)?.iepDescription ?: "Unmapped",
      iepCode = iepCode,
      reviewType = reviewType,
      comments = commentText,
      userId = reviewedBy,
      locationId = locationId,
      prisonerNumber = prisonerNumber,
      auditModuleName = "Incentives-API",
    )

  @Transactional
  suspend fun mergedPrisonerDetails(prisonerMergeEvent: HMPPSDomainEvent) {

    val removedPrisonerNumber = prisonerMergeEvent.additionalInformation.removedNomsNumber!!
    val remainingPrisonerNumber = prisonerMergeEvent.additionalInformation.nomsNumber!!
    log.info("Processing merge event: Prisoner Number Merge $removedPrisonerNumber -> $remainingPrisonerNumber")

    val activeReviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(removedPrisonerNumber)
      .map { review -> review.copy(prisonerNumber = remainingPrisonerNumber) }

    val remainingBookingId = prisonApiService.getPrisonerInfo(remainingPrisonerNumber, true).bookingId
    val inactiveReviews = prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDesc(remainingPrisonerNumber)
      .map { review -> review.copy(bookingId = remainingBookingId, current = false) }

    val reviewsToUpdate = merge(activeReviews, inactiveReviews)
    reviewsToUpdate.collect {
      prisonerIepLevelRepository.save(it)
    }

    val numberUpdated = reviewsToUpdate.count()
    if (numberUpdated > 0) {
      val message = "$numberUpdated incentive records updated from merge $removedPrisonerNumber -> $remainingPrisonerNumber. Updated to booking ID $remainingBookingId"
      log.info(message)
      auditService.sendMessage(
        AuditType.PRISONER_NUMBER_MERGE,
        remainingPrisonerNumber,
        message,
        "Incentives-API"
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
