package uk.gov.justice.digital.hmpps.incentivesapi.service

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
    val prisonerNumbersInPrison = mutableSetOf<String>()
    getPrisons().forEach { prisonId ->
      prisonerNumbersInPrison.addAll(getPrisonersNumbers(prisonId))
    }

    println("Number of prisoners across the estate: ${prisonerNumbersInPrison.count()}")

    // Get prisoner numbers overdue, filter out people no longer in prison and return the count
    kpiRepository
      .getPrisonerNumbersOverdueReview()
      .filter { prisonerNumbersInPrison.contains(it.prisonerNumber) }.count()
  }

  private fun getPrisons(): List<String> = runBlocking {
    println("Getting list of prisons from Prison API...")
    prisonApiService
      .getActivePrisons(true)
      .map(Prison::agencyId)
  }

  private fun getPrisonersNumbers(prisonId: String): Set<String> = runBlocking {
    println("Getting list of prisoners for $prisonId...")
    // TODO: Change page size to 3000 to reduce number of requests for each prison
    offenderSearchService.getOffendersAtLocation(prisonId).map(OffenderSearchPrisoner::prisonerNumber).toSet()
  }
}
