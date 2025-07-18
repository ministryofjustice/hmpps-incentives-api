package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.PageOfPrisoners
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.Prisoner

@Service
class PrisonerSearchService(
  private val prisonerSearchWebClient: WebClient,
  objectMapper: ObjectMapper,
) {
  private val responseFields by lazy {
    objectMapper.serializerProviderInstance.findValueSerializer(Prisoner::class.java).properties()
      .asSequence()
      .filterNot { it.name == "hasAcctOpen" }
      .joinToString(",") { it.name }
  }

  /**
   * Searches for prisoners in a prison using an optional cell location prefix (e.g. MDI-1)
   * returning a flow of pages.
   * Requires role PRISONER_IN_PRISON_SEARCH or PRISONER_SEARCH
   */
  fun findPrisonersAtLocation(prisonId: String, cellLocationPrefix: String = ""): Flow<List<Prisoner>> = flow {
    var page = 0
    do {
      val pageOfPrisoners = prisonerSearchWebClient.get()
        .uri(
          "/prison/{prisonId}/prisoners?" +
            "responseFields={responseFields}&" +
            "cellLocationPrefix={cellLocationPrefix}&" +
            "size={size}&page={page}&sort={sort}",
          mapOf(
            "prisonId" to prisonId,
            "responseFields" to responseFields,
            "cellLocationPrefix" to cellLocationPrefix,
            // NB: API allows up 3,000 results per page
            "size" to 500,
            "page" to page,
            "sort" to "prisonerNumber,ASC",
          ),
        )
        .retrieve()
        .awaitBody<PageOfPrisoners>()
      emit(pageOfPrisoners.content)
      page += 1
    } while (!pageOfPrisoners.last)
  }

  /**
   * Searches for prisoners in a prison using an optional cell location prefix (e.g. MDI-1)
   * returning a complete list.
   * Requires role PRISONER_IN_PRISON_SEARCH or PRISONER_SEARCH
   */
  suspend fun getPrisonersAtLocation(prisonId: String, cellLocationPrefix: String = ""): List<Prisoner> {
    val prisoners = mutableListOf<Prisoner>()
    findPrisonersAtLocation(prisonId, cellLocationPrefix).collect {
      prisoners.addAll(it)
    }
    return prisoners
  }

  /**
   * Get a prisoner info by prisonerNumber
   * Requires role PRISONER_SEARCH or VIEW_PRISONER_DATA or PRISONER_SEARCH__PRISONER__RO
   */
  suspend fun getPrisonerInfo(prisonerNumber: String): Prisoner {
    return prisonerSearchWebClient.get()
      .uri(
        "/prisoner/{prisonerNumber}?" +
          "responseFields={responseFields}",
        mapOf(
          "prisonerNumber" to prisonerNumber,
          "responseFields" to responseFields,
        ),
      )
      .retrieve()
      .awaitBody<Prisoner>()
  }
}
