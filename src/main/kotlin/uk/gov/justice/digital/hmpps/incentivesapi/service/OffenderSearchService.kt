package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList

@Service
class OffenderSearchService(private val offenderSearchWebClient: WebClient) {
  /**
   * Searches for offenders in a prison using an optional cell location prefix (e.g. MDI-1)
   * returning a flow of pages.
   * Requires role PRISONER_IN_PRISON_SEARCH or PRISONER_SEARCH
   */
  fun findOffendersAtLocation(
    prisonId: String,
    cellLocationPrefix: String = "",
  ): Flow<List<OffenderSearchPrisoner>> =
    flow {
      var page = 0
      do {
        val pageOfData =
          offenderSearchWebClient.get()
            .uri(
              "/prison/{prisonId}/prisoners?cellLocationPrefix={cellLocationPrefix}&size={size}&page={page}&sort={sort}",
              mapOf(
                "prisonId" to prisonId,
                "cellLocationPrefix" to cellLocationPrefix,
                "size" to 500, // NB: API allows up 3,000 results per page
                "page" to page,
                "sort" to "prisonerNumber,ASC",
              ),
            )
            .retrieve()
            .awaitBody<OffenderSearchPrisonerList>()
        emit(pageOfData.content)
        page += 1
      } while (!pageOfData.last)
    }

  /**
   * Searches for offenders in a prison using an optional cell location prefix (e.g. MDI-1)
   * returning a complete list.
   * Requires role PRISONER_IN_PRISON_SEARCH or PRISONER_SEARCH
   */
  suspend fun getOffendersAtLocation(
    prisonId: String,
    cellLocationPrefix: String = "",
  ): List<OffenderSearchPrisoner> {
    val offenders = mutableListOf<OffenderSearchPrisoner>()
    findOffendersAtLocation(prisonId, cellLocationPrefix).collect {
      offenders.addAll(it)
    }
    return offenders
  }
}
