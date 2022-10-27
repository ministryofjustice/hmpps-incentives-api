package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList

@Service
class OffenderSearchService(private val offenderSearchWebClient: WebClient) {
  /**
   * Searches for offenders using a cell location prefix, e.g. MDI-1
   * Requires role ROLE_PRISONER_IN_PRISON_SEARCH or ROLE_PRISONER_SEARCH
   * NB: page is 0-based
   */
  suspend fun findOffenders(prisonId: String, cellLocationPrefix: String, page: Int = 0, size: Int = 20) =
    offenderSearchWebClient.get()
      .uri(
        "/prison/$prisonId/prisoners",
        mapOf(
          "cellLocationPrefix" to cellLocationPrefix,
          "page" to page,
          "size" to size,
          "sort" to listOf("prisonerNumber,ASC"),
        )
      )
      .retrieve()
      .awaitBody<OffenderSearchPrisonerList>()
}
