package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux

@Service
class PrisonApiAsyncService(private val prisonWebAsyncClient: WebClient) {

  fun findPrisonersAtLocation(prisonId: String, locationId: String): Flux<PrisonerAtLocation> =
    prisonWebAsyncClient.get()
      .uri("/api/locations/description/$locationId/inmates?returnIep=true")
      .header("Page-Limit", "3000")
      .retrieve()
      .bodyToFlux(PrisonerAtLocation::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun getIEPSummaryPerPrisoner(bookingIds: List<Long>) : Flux<IepSummary> =
    prisonWebAsyncClient.post()
      .uri("/api/bookings/iepSummary")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlux(IepSummary::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }


  fun retrieveCaseNoteCounts(type: String, offenderNos: List<String>) : Flux<CaseNoteUsage> =
     prisonWebAsyncClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToFlux(CaseNoteUsage::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

    fun retrieveProvenAdjudications(bookingIds: List<Long>) : Flux<ProvenAdjudication> =
       prisonWebAsyncClient.post()
        .uri("/api/bookings/proven-adjudications")
        .bodyValue(bookingIds)
        .retrieve()
        .bodyToFlux(ProvenAdjudication::class.java)
        .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

}