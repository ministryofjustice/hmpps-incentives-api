package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.apache.commons.text.WordUtils
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.data.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.IncentiveLevelSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.PrisonerIncentiveSummary
import java.time.Duration
import java.time.LocalDate

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiService,
  private val iepLevelService: IepLevelService
) {

  suspend fun getIncentivesSummaryByLocation(
    prisonId: String,
    locationId: String,
    sortBy: SortColumn = SortColumn.NAME,
    sortDirection: Sort.Direction = Sort.Direction.ASC
  ): BehaviourSummary {

    return coroutineScope {

      val levels = iepLevelService.getIepLevelsForPrison(prisonId)

      val prisonerList = async { prisonApiService.findPrisonersAtLocation(prisonId, locationId) }
      val prisoners = prisonerList.await().toList()

      if (prisoners.isEmpty()) throw NoPrisonersAtLocationException(prisonId, locationId)

      val offenderNos = prisoners.map { p -> p.offenderNo }.toList()
      val positiveCaseNotes = async { getCaseNoteUsage("POS", "IEP_ENC", offenderNos) }
      val negativeCaseNotes = async { getCaseNoteUsage("NEG", "IEP_WARN", offenderNos) }

      val bookingIds = prisoners.map { p -> p.bookingId }.toList()
      val provenAdjudications = async { getProvenAdjudications(bookingIds) }
      val iepDetails = getIEPDetails(bookingIds)

      val iepLevels = levels.toList()
      val positiveCount = positiveCaseNotes.await()
      val negativeCount = negativeCaseNotes.await()

      val prisonersByLevel = getPrisonersByLevel(prisoners, iepDetails)
        .map { prisonerIepLevelMap ->
          val iepLevel = lookupIepLevel(prisonerIepLevelMap, iepLevels.associateBy { it.iepDescription })
          IncentiveLevelSummary(
            level = iepLevel.iepLevel,
            levelDescription = iepLevel.iepDescription,
            prisonerBehaviours = prisonerIepLevelMap.value.map { p ->

              PrisonerIncentiveSummary(
                firstName = WordUtils.capitalizeFully(p.firstName),
                lastName = WordUtils.capitalizeFully(p.lastName),
                prisonerNumber = p.offenderNo,
                bookingId = p.bookingId,
                imageId = p.facialImageId,
                daysOnLevel = iepDetails[p.bookingId]?.daysOnLevel ?: 0,
                daysSinceLastReview = iepDetails[p.bookingId]?.daysSinceReview ?: 0,
                positiveBehaviours = positiveCount[p.offenderNo]?.totalCaseNotes ?: 0,
                incentiveEncouragements = positiveCount[p.offenderNo]?.numSubTypeCount ?: 0,
                negativeBehaviours = negativeCount[p.offenderNo]?.totalCaseNotes ?: 0,
                incentiveWarnings = negativeCount[p.offenderNo]?.numSubTypeCount ?: 0,
                provenAdjudications = provenAdjudications.await()[p.bookingId]?.provenAdjudicationCount ?: 0,
              )
            }.sortedWith(sortBy.applySorting(sortDirection))
          )
        }.toList()

      val location = async { getLocation(locationId) }
      val iepLevelsByCode = iepLevels.associateBy { it.iepLevel }

      BehaviourSummary(
        prisonId = prisonId,
        locationId = locationId,
        locationDescription = location.await(),
        incentiveLevelSummary = addMissingLevels(prisonersByLevel, iepLevelsByCode)
      )
    }
  }

  private fun lookupIepLevel(prisonerMap: Map.Entry<String, List<PrisonerAtLocation>>, levels: Map<String, IepLevel>) =
    if (prisonerMap.key == missingLevel().iepLevel) {
      missingLevel()
    } else {
      levels[prisonerMap.key] ?: invalidLevel()
    }

  fun getPrisonersByLevel(
    prisoners: List<PrisonerAtLocation>,
    prisonerLevels: Map<Long, IepResult>
  ): Map<String, List<PrisonerAtLocation>> =
    prisoners.groupBy {
      prisonerLevels[it.bookingId]?.iepLevel ?: missingLevel().iepLevel
    }

  fun addMissingLevels(
    data: List<IncentiveLevelSummary>,
    levelMap: Map<String, IepLevel>
  ): List<IncentiveLevelSummary> {
    val currentLevels = data.groupBy { it.level }

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

  suspend fun getProvenAdjudications(bookingIds: List<Long>): Map<Long, ProvenAdjudication> =
    prisonApiService.retrieveProvenAdjudications(bookingIds)
      .toList().associateBy {
        it.bookingId
      }

  suspend fun getIEPDetails(bookingIds: List<Long>): Map<Long, IepResult> =
    prisonApiService.getIEPSummaryPerPrisoner(bookingIds)
      .map {
        val (daysSinceReview, daysOnLevel) = calcReviewAndDaysOnLevel(it)
        IepResult(
          bookingId = it.bookingId,
          iepLevel = it.iepLevel,
          daysSinceReview = daysSinceReview,
          daysOnLevel = daysOnLevel
        )
      }.toList().associateBy {
        it.bookingId
      }

  fun calcReviewAndDaysOnLevel(iepSummary: IepSummary): Pair<Int, Int> {
    val currentIepDate = LocalDate.now().atStartOfDay()
    return Pair(
      Duration.between(iepSummary.iepDetails.first().iepDate.atStartOfDay(), currentIepDate).toDays().toInt(),
      calcDaysOnLevel(iepSummary)
    )
  }

  fun calcDaysOnLevel(iepSummary: IepSummary): Int {
    val currentIepDate = LocalDate.now().atStartOfDay()
    var daysOnLevel = 0

    run iepCheck@{
      iepSummary.iepDetails.forEach {
        if (it.iepLevel != iepSummary.iepLevel) {
          return@iepCheck
        }
        daysOnLevel = Duration.between(it.iepDate.atStartOfDay(), currentIepDate).toDays().toInt()
      }
    }

    return daysOnLevel
  }

  suspend fun getCaseNoteUsage(type: String, subType: String, offenderNos: List<String>): Map<String, CaseNoteSummary> =
    prisonApiService.retrieveCaseNoteCounts(type, offenderNos)
      .toList().groupBy {
        it.offenderNo
      }.map { cn ->
        CaseNoteSummary(
          offenderNo = cn.key,
          totalCaseNotes = calcTypeCount(cn.value.toList()),
          numSubTypeCount = calcTypeCount(cn.value.filter { cnc -> cnc.caseNoteSubType == subType }.toList())
        )
      }.associateBy {
        it.offenderNo
      }

  private fun calcTypeCount(caseNoteUsage: List<CaseNoteUsage>): Int =
    if (caseNoteUsage.isNotEmpty()) {
      caseNoteUsage.map { it.numCaseNotes }.reduce { acc, next -> acc + next }
    } else {
      0
    }

  suspend fun getLocation(locationId: String): String =
    prisonApiService.getLocation(locationId).description
}

fun invalidLevel() = IepLevel(iepLevel = "INV", iepDescription = "Invalid", sequence = 98)
fun missingLevel() = IepLevel(iepLevel = "MIS", iepDescription = "Missing", sequence = 99)

class NoPrisonersAtLocationException(prisonId: String, locationId: String) :
  Exception("No prisoners found at prison $prisonId, location $locationId")

data class IepResult(
  val bookingId: Long,
  val iepLevel: String,
  val daysSinceReview: Int,
  val daysOnLevel: Int
)

data class CaseNoteSummary(
  val offenderNo: String,
  val totalCaseNotes: Int,
  val numSubTypeCount: Int
)

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
