package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList

@Service
class OffenderSearchService(private val offenderSearchWebClient: WebClient) {
  /**
   * Searches for offenders using a cell location prefix, e.g. MDI-1
   * Requires role ROLE_PRISONER_IN_PRISON_SEARCH or ROLE_PRISONER_SEARCH
   */
  suspend fun findOffenders(prisonId: String, cellLocationPrefix: String): List<OffenderSearchPrisoner> {
    val offenders = mutableListOf<OffenderSearchPrisoner>()
    var page = 0
    do {
      val pageOfData = offenderSearchWebClient.get()
        .uri(
          "/prison/{prisonId}/prisoners?cellLocationPrefix={cellLocationPrefix}&size={size}&page={page}&sort={sort}",
          mapOf(
            "prisonId" to prisonId,
            "cellLocationPrefix" to cellLocationPrefix,
            "size" to 200, // NB: this is the max allowed page size
            "page" to page,
            "sort" to "prisonerNumber,ASC",
          ),
        )
        .retrieve()
        .awaitBody<OffenderSearchPrisonerList>()
      offenders.addAll(pageOfData.content)
      page += 1
    } while (!pageOfData.last)
    return offenders
  }
}
