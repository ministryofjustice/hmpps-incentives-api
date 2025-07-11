package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.count
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Prison
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.ReviewsConductedPrisonersReviewed
import java.time.LocalDate

@Service
class KpiService(
  private val kpiRepository: KpiRepository,
  private val prisonApiService: PrisonApiService,
  private val prisonerSearchService: PrisonerSearchService,
) {

  suspend fun getNumberOfReviewsConductedAndPrisonersReviewed(day: LocalDate): ReviewsConductedPrisonersReviewed {
    return kpiRepository.getNumberOfReviewsConductedAndPrisonersReviewed(day)
  }

  suspend fun getNumberOfPrisonersOverdue(): Int = coroutineScope {
    // Get a set containing all the prisonerNumber of everyone in prison (any prison)
    val allPrisoners = mutableSetOf<String>()
    getPrisons().map { prisonId ->
      async { getPrisonersInPrison(prisonId) }
    }.awaitAll().forEach { prisoners ->
      allPrisoners.addAll(prisoners.map(Prisoner::prisonerNumber))
    }

    println("Number of prisoners across the estate: ${allPrisoners.size}")

    // Get prisoner numbers overdue, "filter" out people no longer in prison and return the count
    kpiRepository
      .getPrisonerNumbersOverdueReview()
      .count { allPrisoners.contains(it) }
  }

  private suspend fun getPrisons(): List<String> {
    println("Getting list of prisons from Prison API...")
    return prisonApiService
      .getActivePrisons(true)
      .map(Prison::agencyId)
  }

  private suspend fun getPrisonersInPrison(prisonId: String): List<Prisoner> {
    println("Getting list of prisoners for $prisonId...")
    return prisonerSearchService.getPrisonersAtLocation(prisonId)
  }
}
