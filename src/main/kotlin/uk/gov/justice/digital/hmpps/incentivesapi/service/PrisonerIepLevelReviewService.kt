package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.incentivesapi.config.AuthenticationFacade
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.daysSinceReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDateTime

@Service
class PrisonerIepLevelReviewService(
  private val prisonApiService: PrisonApiService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val iepLevelRepository: IepLevelRepository,
  private val authenticationFacade: AuthenticationFacade
) {
  suspend fun getPrisonerIepLevelHistory(bookingId: Long, useNomisData: Boolean = true): IepSummary {
    return if (useNomisData) {
      prisonApiService.getIEPSummaryPerPrisoner(listOf(bookingId)).first()
    } else {
      buildIepSummary(bookingId)
    }
  }

  private suspend fun buildIepSummary(bookingId: Long): IepSummary {
    val iepLevels = prisonerIepLevelRepository.findAllByBookingIdOrderBySequenceDesc(bookingId)
      .map {
        with(it) {
          IepDetail(
            bookingId = bookingId,
            sequence = sequence.toLong(),
            iepDate = reviewTime.toLocalDate(),
            iepTime = reviewTime,
            agencyId = prisonId,
            iepLevel = iepLevelRepository.findById(iepCode)?.iepDescription ?: "Unmapped",
            comments = commentText,
            userId = reviewedBy,
            auditModuleName = "Incentives-API"
          )
        }
      }

    val iepDetails = iepLevels.toList()
    val currentIep = iepDetails.first()
    return IepSummary(
      bookingId = bookingId,
      iepDate = currentIep.iepDate,
      iepTime = currentIep.iepTime,
      iepLevel = currentIep.iepLevel,
      iepDetails = iepDetails,
      daysSinceReview = daysSinceReview(iepDetails)
    )
  }

  suspend fun getPrisonerIepLevelHistory(prisonerNumber: String, useNomisData: Boolean = true): IepSummary {
    val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
    return getPrisonerIepLevelHistory(prisonerInfo.bookingId, useNomisData)
  }

  @Transactional
  suspend fun addIepReview(prisonerNumber: String, iepReview: IepReview) {
    val prisonerInfo = prisonApiService.getPrisonerInfo(prisonerNumber)
    addIepLevel(prisonerInfo, iepReview)
  }

  @Transactional
  suspend fun addIepReview(bookingId: Long, iepReview: IepReview) {
    val prisonerInfo = prisonApiService.getPrisonerInfo(bookingId)
    addIepLevel(prisonerInfo, iepReview)
  }

  private suspend fun addIepLevel(
    prisonerInfo: PrisonerAtLocation,
    iepReview: IepReview
  ) {
    val locationInfo = prisonApiService.getLocationById(prisonerInfo.assignedLivingUnitId)
    var nextSequence = 1

    prisonerIepLevelRepository.findOneByBookingIdAndCurrentIsTrue(prisonerInfo.bookingId)
      ?.let {
        prisonerIepLevelRepository.save(it.copy(current = false))
        nextSequence = it.sequence + 1
      }

    prisonerIepLevelRepository.save(
      PrisonerIepLevel(
        iepCode = iepReview.iepLevel,
        commentText = iepReview.comment,
        bookingId = prisonerInfo.bookingId,
        prisonId = locationInfo.agencyId,
        locationId = locationInfo.description,
        sequence = nextSequence,
        current = true,
        reviewedBy = authenticationFacade.getUsername(),
        reviewTime = LocalDateTime.now(),
        prisonerNumber = prisonerInfo.offenderNo
      )
    )
    prisonApiService.addIepReview(prisonerInfo.bookingId, iepReview)
  }
}
