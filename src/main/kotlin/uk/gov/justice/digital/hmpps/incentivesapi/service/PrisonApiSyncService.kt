package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class PrisonApiSyncService(private val prisonWebSyncClient: WebClient) {

  fun findPrisonersAtLocation(prisonId: String, locationId: String): List<PrisonerAtLocation> {
    return prisonWebSyncClient.get()
      .uri("/api/locations/description/$locationId/inmates?returnIep=true")
      .header("Page-Limit", "3000")
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<PrisonerAtLocation>>() {})
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
      .block()!!
  }

  fun getIEPSummaryPerPrisoner(bookingIds: List<Long>) : List<IepSummary> {
    return prisonWebSyncClient.post()
      .uri("/api/bookings/iepSummary")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<IepSummary>>() {})
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
      .block()!!
  }

  fun retrieveCaseNoteCounts(type: String, offenderNos: List<String>) : List<CaseNoteUsage>{
    return prisonWebSyncClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<CaseNoteUsage>>() {})
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
      .block()!!
  }

  fun retrieveProvenAdjudications(bookingIds: List<Long>) : List<ProvenAdjudication> =
    prisonWebSyncClient.post()
      .uri("/api/bookings/proven-adjudications")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<ProvenAdjudication>>() {})
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
      .block()!!
}
