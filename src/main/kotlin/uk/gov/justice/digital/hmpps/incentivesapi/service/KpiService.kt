package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Prison
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.ReviewsConductedPrisonersReviewed
import java.time.LocalDate

@Service
class KpiService(
  private val kpiRepository: KpiRepository,
  private val prisonApiService: PrisonApiService,
  private val offenderSearchService: OffenderSearchService,
) {

  fun getNumberOfReviewsConductedAndPrisonersReviewed(day: LocalDate): ReviewsConductedPrisonersReviewed = runBlocking {
    kpiRepository.getNumberOfReviewsConductedAndPrisonersReviewed(day)
  }

  fun getNumberOfPrisonersOverdue(): Int = runBlocking {
    // Get a set containing all the prisonerNumber of everyone in prison (any prison)
    val allPrisoners = mutableSetOf<String>()
    val prisonersInPrisons = getPrisons().map { prisonId -> async { getPrisonersInPrison(prisonId) } }.awaitAll()
    prisonersInPrisons.forEach { prisoners ->
      allPrisoners.addAll(prisoners.map(OffenderSearchPrisoner::prisonerNumber))
    }

    println("Number of prisoners across the estate: ${allPrisoners.size}")

    // Get prisoner numbers overdue, "filter" out people no longer in prison and return the count
    kpiRepository
      .getPrisonerNumbersOverdueReview()
      .count { allPrisoners.contains(it.prisonerNumber) }
  }

  private fun getPrisons(): List<String> = runBlocking {
    println("Getting list of prisons from Prison API...")
    prisonApiService
      .getActivePrisons(true)
      .map(Prison::agencyId)
  }

  private suspend fun getPrisonersInPrison(prisonId: String): List<OffenderSearchPrisoner> {
    println("Getting list of prisoners for $prisonId...")
    return offenderSearchService.getOffendersAtLocation(prisonId)
  }
}
