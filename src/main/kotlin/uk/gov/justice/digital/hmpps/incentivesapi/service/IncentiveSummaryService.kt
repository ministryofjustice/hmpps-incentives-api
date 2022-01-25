package uk.gov.justice.digital.hmpps.incentivesapi.service

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

  fun getIncentivesSummaryByLocation(prisonId: String, locationId: String): Mono<BehaviourSummary> {

    val result = prisonApiService.findPrisonersAtLocation(prisonId, locationId)
      .collectList()
      .map {

        val bookingIds = it.map { p -> p.bookingId }.toList()
        val offenderNos = it.map { p -> p.offenderNo }.toList()

        if (bookingIds.isEmpty()) throw NoPrisonersAtLocationException(prisonId, locationId)

        Mono.zip(
          Mono.just(it.groupBy { p -> p.iepLevel }),
          getIEPDetails(bookingIds),
          getCaseNoteUsage("POS", "IEP_ENC", offenderNos),
          getCaseNoteUsage("NEG", "IEP_WARN", offenderNos),
          getProvenAdjudications(bookingIds)
        ).map { tuples ->
          tuples.t1.map { prisoner ->
            IncentiveLevelSummary(
              level = prisoner.key,
              prisonerBehaviours = prisoner.value.map { p ->
                PrisonerIncentiveSummary(
                  firstName = p.firstName,
                  lastName = p.lastName,
                  prisonerNumber = p.offenderNo,
                  bookingId = p.bookingId,
                  imageId = p.facialImageId,
                  daysOnLevel = tuples.t2[p.bookingId]?.daysOnLevel?:0,
                  daysSinceLastReview = tuples.t2[p.bookingId]?.daysSinceReview ?: 0,
                  positiveBehaviours = tuples.t3[p.offenderNo]?.totalCaseNotes ?: 0,
                  incentiveEncouragements = tuples.t3[p.offenderNo]?.numSubTypeCount ?: 0,
                  negativeBehaviours = tuples.t4[p.offenderNo]?.totalCaseNotes ?: 0,
                  incentiveWarnings = tuples.t4[p.offenderNo]?.numSubTypeCount ?: 0,
                  provenAdjudications = tuples.t5[p.bookingId]?.provenAdjudicationCount ?: 0,
                )
              }
            )
          }.toList()
        }.map { levels ->
          Mono.zip(
            Mono.just(levels),
            getLocation(locationId),
            getIepLevels(prisonId),
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

  fun addMissingLevels(
    data: List<IncentiveLevelSummary>,
    levelMap: Map<String, IepLevel>
  ): List<IncentiveLevelSummary> {
    val currentLevels = data.groupBy { it.level }

    return levelMap.entries
      .filter {
        currentLevels[it.key] == null
      }.map {
        IncentiveLevelSummary(level = it.key, prisonerBehaviours = listOf())
      } + data
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
          daysSinceReview = it.daysSinceReview,
          daysOnLevel = calcDaysOnLevel(it)
        )
      }.collectMap {
        it.bookingId
      }

  private fun calcDaysOnLevel(iepSummary: IepSummary): Int {
    val currentIepDate = iepSummary.iepDate
    var daysOnLevel: Int = getDaysOnLevel(iepSummary.iepDetails.last().iepDate, currentIepDate, iepSummary.daysSinceReview)

    run iepCheck@{
      iepSummary.iepDetails.forEach {
        if (it.iepLevel != iepSummary.iepLevel) {
          daysOnLevel = getDaysOnLevel(it.iepDate, currentIepDate, iepSummary.daysSinceReview)
          return@iepCheck
        }
      }
    }

    return daysOnLevel
  }

  private fun getDaysOnLevel(
    oldIepDate: LocalDate,
    currentIepDate: LocalDate,
    daysSinceReview: Int
  ): Int {
    val daysOnLevelBetweenReview = Duration.between(oldIepDate.atStartOfDay(), currentIepDate.atStartOfDay()).toDays()
    return daysOnLevelBetweenReview.toInt() + daysSinceReview - 1
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
        it.locationPrefix
      }

  fun getIepLevels(prisonId: String): Mono<Map<String, IepLevel>> =
    prisonApiService.getIepLevelsForPrison(prisonId)
      .collectMap {
        it.iepDescription
      }
}

class NoPrisonersAtLocationException(prisonId: String, locationId: String) :
  Exception("No prisoners found at prison $prisonId, location $locationId")

data class IepResult(
  val bookingId: Long,
  val daysSinceReview: Int,
  val daysOnLevel: Int
)

data class CaseNoteSummary(
  val offenderNo: String,
  val totalCaseNotes: Int,
  val numSubTypeCount: Int
)
