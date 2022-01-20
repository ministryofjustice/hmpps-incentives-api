package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.data.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.IncentiveLevelSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.PrisonerIncentiveSummary

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiAsyncService
) {

  fun getIncentivesSummaryByLocation(prisonId: String, locationId: String): Mono<BehaviourSummary> =
    getPrisoners(prisonId, locationId)
      .collectMultimap {
        it.currentIepLevel
      }.map {
        it.entries.associate { (level, prisoners) ->
          level to prisoners.toList()
        }
      }.map {
        it.entries.map { prisoners ->
          IncentiveLevelSummary(
            level = prisoners.key!!,
            levelDescription = "",
            prisonerBehaviours = prisoners.value
          )
        }
      }.mapNotNull { summary ->
        BehaviourSummary(
          prisonId = prisonId,
          locationId = locationId,
          locationDescription = "WING",
          incentiveLevelSummary = summary
        )
      }

  private fun getPrisoners(prisonId: String, locationId: String): Flux<PrisonerIncentiveSummary> =
    prisonApiService.findPrisonersAtLocation(prisonId, locationId)
      .map {
        PrisonerIncentiveSummary(
          prisonerNumber = it.offenderNo,
          firstName = it.firstName,
          lastName = it.lastName,
          imageId = it.facialImageId,
          bookingId = it.bookingId,
          currentIepLevel = it.iepLevel
        )
      }


  fun getIncentivesSummaryByLocation2(prisonId: String, locationId: String): Mono<Any> =
    prisonApiService.findPrisonersAtLocation(prisonId, locationId)
      .collectMultimap {
        it.iepLevel
      }.map {
        it.entries.associate { (level, prisoners) ->
          level to prisoners.toList()
        }
      }.map {

      }
}



