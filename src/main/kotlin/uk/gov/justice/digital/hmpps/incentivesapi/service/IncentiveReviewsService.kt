package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse

@Service
class IncentiveReviewsService(
  private val offenderSearchService: OffenderSearchService,
  private val prisonApiService: PrisonApiService,
) {
  /**
   * Returns incentive review information for a given location within a prison
   * NB: page is 1-based
   */
  suspend fun reviews(
    prisonId: String,
    cellLocationPrefix: String,
    page: Int = 1,
    size: Int = 20
  ): IncentiveReviewResponse = coroutineScope {
    val deferredOffenders = async { offenderSearchService.findOffenders(prisonId, cellLocationPrefix, page - 1, size) }
    val deferredLocationDescription = async {
      try {
        prisonApiService.getLocation(cellLocationPrefix).description
      } catch (e: NotFound) {
        "Unknown location"
      }
    }

    val offenders = deferredOffenders.await()
    val locationDescription = deferredLocationDescription.await()

    IncentiveReviewResponse(
      reviewCount = offenders.totalElements,
      locationDescription = locationDescription,
      reviews = offenders.content.map {
        IncentiveReview(
          prisonerNumber = it.prisonerNumber,
          bookingId = it.bookingId.toLong(),
          firstName = it.firstName,
          lastName = it.lastName,
          acctOpenStatus = it.acctOpen,
        )
      }
    )
  }
}
