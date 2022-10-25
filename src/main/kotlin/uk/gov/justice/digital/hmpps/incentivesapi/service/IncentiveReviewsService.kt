package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewResponse

@Service
class IncentiveReviewsService(private val offenderSearchService: OffenderSearchService) {
  /**
   * Returns incentive review information for a given location within a prison
   * NB: page is 1-based
   */
  suspend fun reviews(
    prisonId: String,
    cellLocationPrefix: String,
    page: Int = 1,
    size: Int = 20
  ): IncentiveReviewResponse {
    val response = offenderSearchService.findOffenders(prisonId, cellLocationPrefix, page - 1, size)

    return IncentiveReviewResponse(
      reviewCount = response.totalElements,
      reviews = response.content.map {
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
