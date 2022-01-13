package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux

@Service
class PrisonApiService(private val prisonWebClient: WebClient) {

  fun findPrisonersAtLocation(prisonId: String, locationId: String): Flux<PrisonerAtLocation> {
    return prisonWebClient.get()
      .uri("/api/locations/description/$locationId/inmates")
      .retrieve()
      .bodyToFlux(PrisonerAtLocation::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
  }
}

data class PrisonerAtLocation(
  val bookingId: Long,
  val facialImageId: Long,
  val firstName: String,
  val iepLevel: String,
  val lastName: String,
  val offenderNo: String,
)
