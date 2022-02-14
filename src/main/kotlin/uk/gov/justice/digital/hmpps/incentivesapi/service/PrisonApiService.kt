package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.validation.constraints.NotEmpty

@Service
class PrisonApiService(private val prisonWebClient: WebClient) {

  fun findPrisonersAtLocation(prisonId: String, locationId: String): Flux<PrisonerAtLocation> =
    prisonWebClient.get()
      .uri("/api/locations/description/$locationId/inmates")
      .header("Page-Limit", "3000")
      .retrieve()
      .bodyToFlux(PrisonerAtLocation::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun getIEPSummaryPerPrisoner(@NotEmpty bookingIds: List<Long>): Flux<IepSummary> =
    prisonWebClient.post()
      .uri("/api/bookings/iepSummary")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlux(IepSummary::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun retrieveCaseNoteCounts(type: String, @NotEmpty offenderNos: List<String>): Flux<CaseNoteUsage> =
    prisonWebClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToFlux(CaseNoteUsage::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun retrieveProvenAdjudications(@NotEmpty bookingIds: List<Long>): Flux<ProvenAdjudication> =
    prisonWebClient.post()
      .uri("/api/bookings/proven-adjudications")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlux(ProvenAdjudication::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun getIepLevelsForPrison(prisonId: String): Flux<IepLevel> =
    prisonWebClient.get()
      .uri("/api/agencies/$prisonId/iepLevels")
      .retrieve()
      .bodyToFlux(IepLevel::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }

  fun getLocation(locationId: String): Mono<PrisonLocation> =
    prisonWebClient.get()
      .uri("/api/locations/code/$locationId")
      .retrieve()
      .bodyToMono(PrisonLocation::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
}
