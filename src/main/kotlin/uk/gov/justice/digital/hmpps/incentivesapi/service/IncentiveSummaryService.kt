package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.apache.commons.text.WordUtils
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.data.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.IncentiveLevelSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.PrisonerIncentiveSummary
import java.time.Duration
import java.time.LocalDate

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiService
) {

  fun getIncentivesSummaryByLocation(prisonId: String, locationId: String, sortBy: SortColumn = SortColumn.NAME, sortDirection: Sort.Direction = Sort.Direction.ASC): Mono<BehaviourSummary> {

    val result = prisonApiService.findPrisonersAtLocation(prisonId, locationId)
      .collectList()
      .map {

        val bookingIds = it.map { p -> p.bookingId }.toList()
        val offenderNos = it.map { p -> p.offenderNo }.toList()

        if (bookingIds.isEmpty()) throw NoPrisonersAtLocationException(prisonId, locationId)

        Mono.zip(
          Mono.just(it),
          getIEPDetails(bookingIds),
          getCaseNoteUsage("POS", "IEP_ENC", offenderNos),
          getCaseNoteUsage("NEG", "IEP_WARN", offenderNos),
          getProvenAdjudications(bookingIds),
          getIepLevelsByDescription(prisonId)
        ).map { tuples ->
          getPrisonersByLevel(tuples.t1, tuples.t2)
            .map { prisonerIepLevelMap ->
              val iepLevel = lookupIepLevel(prisonerIepLevelMap, tuples.t6)
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
                    daysOnLevel = tuples.t2[p.bookingId]?.daysOnLevel ?: 0,
                    daysSinceLastReview = tuples.t2[p.bookingId]?.daysSinceReview ?: 0,
                    positiveBehaviours = tuples.t3[p.offenderNo]?.totalCaseNotes ?: 0,
                    incentiveEncouragements = tuples.t3[p.offenderNo]?.numSubTypeCount ?: 0,
                    negativeBehaviours = tuples.t4[p.offenderNo]?.totalCaseNotes ?: 0,
                    incentiveWarnings = tuples.t4[p.offenderNo]?.numSubTypeCount ?: 0,
                    provenAdjudications = tuples.t5[p.bookingId]?.provenAdjudicationCount ?: 0,
                  )
                }.sortedWith(sortBy.applySorting(sortDirection))
              )
            }.toList()
        }.map { levels ->
          Mono.zip(
            Mono.just(levels),
            getLocation(locationId),
            getIepLevelsByCode(prisonId)
          )
            .map { tuples ->
              BehaviourSummary(
                prisonId = prisonId,
                locationId = locationId,
                locationDescription = tuples.t2,
                incentiveLevelSummary = addMissingLevels(tuples.t1, tuples.t3)
              )
            }
        }
      }.flatMap { bs ->
        bs
          .flatMap { t -> t }
      }

    return result.defaultIfEmpty(BehaviourSummary(prisonId = prisonId, locationId = locationId))
  }

  private fun lookupIepLevel(prisonerMap: Map.Entry<String, List<PrisonerAtLocation>>, levels: Map<String, IepLevel>) =
    if (prisonerMap.key == missingLevel().iepLevel) {
      missingLevel()
    } else {
      levels[prisonerMap.key] ?: invalidLevel()
    }

  fun getPrisonersByLevel(prisoners: List<PrisonerAtLocation>, prisonerLevels: Map<Long, IepResult>): Map<String, List<PrisonerAtLocation>> =
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
    val additionalLevels = levelMap + mapOf(missingLevel().iepLevel to missingLevel(), invalidLevel().iepLevel to invalidLevel())
    return incentiveLevelSummaries.sortedWith(compareBy { v -> additionalLevels[v.level]?.sequence })
  }

  fun getProvenAdjudications(bookingIds: List<Long>): Mono<Map<Long, ProvenAdjudication>> =
    prisonApiService.retrieveProvenAdjudications(bookingIds)
      .collectMap {
        it.bookingId
      }

  fun getIEPDetails(bookingIds: List<Long>): Mono<Map<Long, IepResult>> =
    prisonApiService.getIEPSummaryPerPrisoner(bookingIds)
      .map {
        IepResult(
          bookingId = it.bookingId,
          iepLevel = it.iepLevel,
          daysSinceReview = it.daysSinceReview,
          daysOnLevel = calcDaysOnLevel(it)
        )
      }.collectMap {
        it.bookingId
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

  fun getCaseNoteUsage(type: String, subType: String, offenderNos: List<String>): Mono<Map<String, CaseNoteSummary>> =
    prisonApiService.retrieveCaseNoteCounts(type, offenderNos)
      .collectMultimap {
        it.offenderNo
      }.map {
        it.entries.map { cn ->
          CaseNoteSummary(
            offenderNo = cn.key,
            totalCaseNotes = calcTypeCount(cn.value.toList()),
            numSubTypeCount = calcTypeCount(cn.value.filter { cnc -> cnc.caseNoteSubType == subType }.toList())
          )
        }
      }.map {
        it.associateBy({ cn -> cn.offenderNo }, { cn -> cn })
      }

  private fun calcTypeCount(caseNoteUsage: List<CaseNoteUsage>): Int =
    if (caseNoteUsage.isNotEmpty()) {
      caseNoteUsage.map { it.numCaseNotes }.reduce { acc, next -> acc + next }
    } else {
      0
    }

  fun getLocation(locationId: String): Mono<String> =
    prisonApiService.getLocation(locationId)
      .map {
        it.description
      }

  fun getIepLevelsByDescription(prisonId: String): Mono<Map<String, IepLevel>> =
    prisonApiService.getIepLevelsForPrison(prisonId)
      .collectMap {
        it.iepDescription
      }

  fun getIepLevelsByCode(prisonId: String): Mono<Map<String, IepLevel>> =
    prisonApiService.getIepLevelsForPrison(prisonId)
      .collectMap {
        it.iepLevel
      }
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
    return if (sortDirection == Sort.Direction.DESC) { comparator.reversed() } else { comparator }
  }
}
