package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepMigration
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.daysSinceReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val iepLevelRepository: IepLevelRepository,
  private val iepLevelService: IepLevelService,
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
      buildIepSummary(prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(bookingId), withDetails)
    }
  }

  suspend fun getPrisonerIepLevelHistory(prisonerNumber: String, useNomisData: Boolean = true): IepSummary {

    return if (useNomisData) {
      val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
      prisonApiService.getIEPSummaryPerPrisoner(listOf(prisonerInfo.bookingId)).first()
    } else {
      buildIepSummary(prisonerIepLevelRepository.findAllByPrisonerNumberOrderByReviewTimeDescSequenceDesc(prisonerNumber))
    }
  }

  @Transactional
  suspend fun addIepReview(prisonerNumber: String, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
    return addIepLevel(prisonerInfo, iepReview)
  }

  @Transactional
  suspend fun addIepReview(bookingId: Long, iepReview: IepReview): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId)
    return addIepLevel(prisonerInfo, iepReview)
  }

  @Transactional
  suspend fun addIepMigration(bookingId: Long, iepMigration: IepMigration): IepDetail {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId)
    return prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        iepCode = iepMigration.iepLevel,
        commentText = iepMigration.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = iepMigration.prisonId,
        locationId = iepMigration.locationId,
        sequence = 0,
        current = iepMigration.current,
        reviewedBy = iepMigration.userId,
        reviewTime = iepMigration.iepTime,
        reviewType = iepMigration.reviewType,
        prisonerNumber = prisonerInfo.offenderNo
      )
    ).translate()
  }

  fun getCurrentIEPLevelForPrisoners(bookingIds: List<Long>, useNomisData: Boolean): Flow<CurrentIepLevel> {
    return if (useNomisData) {
      prisonApiService.getIEPSummaryPerPrisoner(bookingIds)
        .map {
          CurrentIepLevel(iepLevel = it.iepLevel, bookingId = it.bookingId)
        }
    } else {
      prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrue(bookingIds)
        .map {
          CurrentIepLevel(iepLevel = iepLevelRepository.findById(it.iepCode)?.iepDescription ?: "Unmapped", bookingId = it.bookingId)
        }
    }
  }

  suspend fun getReviewById(id: Long): IepDetail =
    prisonerIepLevelRepository.findById(id)
      ?.let {
        with(it) {
          translate()
        }
      } ?: throw NoDataFoundException(id)

  @Transactional
  suspend fun processReceivedPrisoner(prisonOffenderEvent: HMPPSDomainEvent) =
    when (prisonOffenderEvent.additionalInformation.reason) {
      "ADMISSION" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.INITIAL)
      "TRANSFERRED" -> createIepForReceivedPrisoner(prisonOffenderEvent, ReviewType.TRANSFER)
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
      persistIepLevel(
        prisonerInfo,
        iepReview,
        locationInfo,
        LocalDateTime.parse(prisonOffenderEvent.occurredAt, DateTimeFormatter.ISO_DATE_TIME),
        "incentives-api"
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
        iepLevelsForPrison.find { it.iepLevel == iepLevelBeforeTransfer.iepLevel }
          // ...or the highest level in the prison
          ?: iepLevelsForPrison.last()
      }

      else -> throw NotImplementedError("Not implemented for $reviewType")
    }
    return iepLevel.iepLevel
  }

  private suspend fun buildIepSummary(levels: Flow<PrisonerIepLevel>, withDetails: Boolean = true): IepSummary {
    val iepLevels = levels.map {
      with(it) {
        translate()
      }
    }.toList()

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
      daysSinceReview = daysSinceReview(iepLevels)
    )
  }

  private suspend fun addIepLevel(
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

    return newIepReview
  }

  suspend fun persistIepLevel(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview,
    locationInfo: Location,
    reviewTime: LocalDateTime,
    reviewerUserName: String,
  ): PrisonerIepLevel {
    var nextSequence = 1
    prisonerIepLevelRepository.findOneByBookingIdAndCurrentIsTrue(prisonerInfo.bookingId)
      ?.let {
        prisonerIepLevelRepository.save(it.copy(current = false))
        nextSequence = it.sequence + 1
      }

    return prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        iepCode = iepReview.iepLevel,
        commentText = iepReview.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = locationInfo.agencyId,
        locationId = locationInfo.description,
        sequence = nextSequence,
        current = true,
        reviewedBy = reviewerUserName,
        reviewTime = reviewTime,
        reviewType = iepReview.reviewType ?: ReviewType.REVIEW,
        prisonerNumber = prisonerInfo.offenderNo
      )
    )
  }

  private suspend fun PrisonerIepLevel.translate() =
    IepDetail(
      id = id,
      bookingId = bookingId,
      sequence = sequence.toLong(),
      iepDate = reviewTime.toLocalDate(),
      iepTime = reviewTime,
      agencyId = prisonId,
      iepLevel = iepLevelRepository.findById(iepCode)?.iepDescription ?: "Unmapped",
      reviewType = reviewType,
      comments = commentText,
      userId = reviewedBy,
      locationId = locationId,
      prisonerNumber = prisonerNumber,
      auditModuleName = "Incentives-API"
    )
}

data class IepReviewInNomis(
  val iepLevel: String,
  val comment: String,
  val reviewTime: LocalDateTime,
  val reviewerUserName: String,
)
