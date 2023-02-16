package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.apache.commons.text.WordUtils
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevelSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonerIncentiveSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.ProvenAdjudication
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiService,
  private val offenderSearchService: OffenderSearchService,
  private val iepLevelService: IepLevelService,
  private val prisonerIepLevelRepository: PrisonerIepLevelRepository,
  private val behaviourService: BehaviourService,
  private val clock: Clock,
) {

  suspend fun getIncentivesSummaryByLocation(
    prisonId: String,
    locationId: String,
    sortBy: SortColumn = SortColumn.NAME,
    sortDirection: Sort.Direction = Sort.Direction.ASC
  ): BehaviourSummary = coroutineScope {
    val prisoners = offenderSearchService.findOffenders(prisonId, locationId)

    if (prisoners.isEmpty()) throw NoPrisonersAtLocationException(prisonId, locationId)

    val iepLevelsDeferred = async { iepLevelService.getIepLevelsForPrison(prisonId) }

    val bookingIds = prisoners.map(OffenderSearchPrisoner::bookingId)
    val provenAdjudications = async { getProvenAdjudications(bookingIds) }

    val reviews = prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds = bookingIds).toList()
    val iepDetails = getCurrentAndHistoricalReviews(reviews).associateBy(IepResult::bookingId)

    val deferredBehaviourCaseNotesSinceLastReview = async {
      behaviourService.getBehaviours(prisonerIepLevelRepository.findAllByBookingIdInOrderByReviewTimeDesc(bookingIds = bookingIds).toList())
    }

    val iepLevels = iepLevelsDeferred.await()
    val iepLevelsByCode = iepLevels.associateBy(IepLevel::iepLevel)
    val iepLevelsByDescription = iepLevels.associateBy(IepLevel::iepDescription)

    val behaviourCaseNotesSinceLastReview = deferredBehaviourCaseNotesSinceLastReview.await()

    val prisonersByLevel = getPrisonersByLevel(prisoners, iepDetails)
      .map { prisonerIepLevelMap ->
        val iepLevel = lookupIepLevel(prisonerIepLevelMap, iepLevelsByDescription)
        IncentiveLevelSummary(
          level = iepLevel.iepLevel,
          levelDescription = iepLevel.iepDescription,
          prisonerBehaviours = prisonerIepLevelMap.value.map { p ->

            PrisonerIncentiveSummary(
              firstName = WordUtils.capitalizeFully(p.firstName),
              lastName = WordUtils.capitalizeFully(p.lastName),
              prisonerNumber = p.prisonerNumber,
              bookingId = p.bookingId,
              daysOnLevel = iepDetails[p.bookingId]?.daysOnLevel ?: 0,
              daysSinceLastReview = iepDetails[p.bookingId]?.daysSinceReview ?: 0,
              positiveBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(p.bookingId, "POS")]?.totalCaseNotes ?: 0,
              incentiveEncouragements = behaviourCaseNotesSinceLastReview[BookingTypeKey(p.bookingId, "POS")]?.numSubTypeCount ?: 0,
              negativeBehaviours = behaviourCaseNotesSinceLastReview[BookingTypeKey(p.bookingId, "NEG")]?.totalCaseNotes ?: 0,
              incentiveWarnings = behaviourCaseNotesSinceLastReview[BookingTypeKey(p.bookingId, "NEG")]?.numSubTypeCount ?: 0,
              provenAdjudications = provenAdjudications.await()[p.bookingId]?.provenAdjudicationCount ?: 0,
            )
          }.sortedWith(sortBy.applySorting(sortDirection))
        )
      }

    val location = getLocation(locationId)

    BehaviourSummary(
      prisonId = prisonId,
      locationId = locationId,
      locationDescription = location,
      incentiveLevelSummary = addMissingLevels(prisonersByLevel, iepLevelsByCode)
    )
  }

  private fun lookupIepLevel(prisonerMap: Map.Entry<String, List<OffenderSearchPrisoner>>, levels: Map<String, IepLevel>) =
    if (prisonerMap.key == missingLevel().iepLevel) {
      missingLevel()
    } else {
      levels[prisonerMap.key] ?: invalidLevel()
    }

  private fun getPrisonersByLevel(
    prisoners: List<OffenderSearchPrisoner>,
    prisonerLevels: Map<Long, IepResult>
  ): Map<String, List<OffenderSearchPrisoner>> =
    prisoners.groupBy {
      prisonerLevels[it.bookingId]?.iepLevel ?: missingLevel().iepLevel
    }

  private fun addMissingLevels(
    data: List<IncentiveLevelSummary>,
    levelMap: Map<String, IepLevel>
  ): List<IncentiveLevelSummary> {
    val currentLevels = data.groupBy(IncentiveLevelSummary::level)

    val incentiveLevelSummaries = data + levelMap.entries
      .filter {
        currentLevels[it.key] == null
      }.map {
        IncentiveLevelSummary(level = it.key, levelDescription = it.value.iepDescription, prisonerBehaviours = listOf())
      }
    val additionalLevels =
      levelMap + mapOf(missingLevel().iepLevel to missingLevel(), invalidLevel().iepLevel to invalidLevel())
    return incentiveLevelSummaries.sortedWith(compareBy { v -> additionalLevels[v.level]?.sequence })
  }

  private suspend fun getProvenAdjudications(bookingIds: List<Long>): Map<Long, ProvenAdjudication> =
    prisonApiService.retrieveProvenAdjudications(bookingIds)
      .toList().associateBy(ProvenAdjudication::bookingId)

  private suspend fun getCurrentAndHistoricalReviews(reviews: List<PrisonerIepLevel>): List<IepResult> {
    val incentiveLevels = prisonApiService.getIncentiveLevels()
    return reviews
      .sortedByDescending { it.reviewTime }
      .groupBy { it.bookingId }
      .map {
        val review = it.value
        val latestReview = review.firstOrNull(PrisonerIepLevel::isRealReview) ?: review.firstOrNull()
        IepResult(
          bookingId = it.key,
          iepLevel = latestReview?.let { incentiveLevels[latestReview.iepCode]?.iepDescription ?: invalidLevel().iepLevel }
            ?: missingLevel().iepLevel,
          daysSinceReview = latestReview?.let {
            Duration.between(
              latestReview.reviewTime.toLocalDate().atStartOfDay(),
              LocalDateTime.now(clock)
            ).toDays().toInt()
          },
          daysOnLevel = daysOnLevel(
            clock,
            review
              .map { iep ->
                IepDetail(
                  iepCode = iep.iepCode,
                  iepLevel = incentiveLevels[iep.iepCode]?.iepDescription ?: invalidLevel().iepDescription,
                  reviewType = iep.reviewType,
                  bookingId = iep.bookingId,
                  agencyId = iep.prisonId,
                  userId = iep.reviewedBy,
                  iepDate = iep.reviewTime.toLocalDate(),
                  iepTime = iep.reviewTime
                )
              }.sortedByDescending { iep -> iep.iepTime }
          )
        )
      }
  }

  private suspend fun getLocation(locationId: String): String =
    prisonApiService.getLocation(locationId).description
}

fun invalidLevel() = IepLevel(iepLevel = "INV", iepDescription = "Invalid", sequence = 98)
fun missingLevel() = IepLevel(iepLevel = "MIS", iepDescription = "No Review", sequence = 99)

class NoPrisonersAtLocationException(prisonId: String, locationId: String) :
  Exception("No prisoners found at prison $prisonId, location $locationId")

data class IepResult(
  val bookingId: Long,
  val iepLevel: String,
  val daysSinceReview: Int?,
  val daysOnLevel: Int?
)

fun daysOnLevel(clock: Clock, iepDetails: List<IepDetail>): Int {
  val today = LocalDateTime.now(clock)

  val earliestMatchingIepDetail = iepDetails.reduce { earliestMatchingIepDetail, iepDetail ->
    if (iepDetail.iepLevel == earliestMatchingIepDetail.iepLevel) {
      iepDetail
    } else {
      return@reduce earliestMatchingIepDetail
    }
  }

  return Duration.between(earliestMatchingIepDetail.iepDate.atStartOfDay(), today).toDays().toInt()
}

enum class SortColumn {
  NUMBER,
  NAME,
  DAYS_ON_LEVEL,
  POS_BEHAVIOURS,
  NEG_BEHAVIOURS,
  DAYS_SINCE_LAST_REVIEW,
  INCENTIVE_WARNINGS,
  INCENTIVE_ENCOURAGEMENTS,
  PROVEN_ADJUDICATIONS;

  fun applySorting(sortDirection: Sort.Direction = Sort.Direction.ASC): Comparator<PrisonerIncentiveSummary> {

    val comparator = when (this) {
      NUMBER -> compareBy(PrisonerIncentiveSummary::prisonerNumber)
      NAME -> compareBy(PrisonerIncentiveSummary::lastName, PrisonerIncentiveSummary::firstName)
      DAYS_ON_LEVEL -> compareBy(PrisonerIncentiveSummary::daysOnLevel)
      POS_BEHAVIOURS -> compareBy(PrisonerIncentiveSummary::positiveBehaviours)
      NEG_BEHAVIOURS -> compareBy(PrisonerIncentiveSummary::negativeBehaviours)
      DAYS_SINCE_LAST_REVIEW -> compareBy(PrisonerIncentiveSummary::daysSinceLastReview)
      INCENTIVE_WARNINGS -> compareBy(PrisonerIncentiveSummary::incentiveWarnings)
      INCENTIVE_ENCOURAGEMENTS -> compareBy(PrisonerIncentiveSummary::incentiveEncouragements)
      PROVEN_ADJUDICATIONS -> compareBy(PrisonerIncentiveSummary::provenAdjudications)
    }
    return if (sortDirection == Sort.Direction.DESC) {
      comparator.reversed()
    } else {
      comparator
    }
  }
}
