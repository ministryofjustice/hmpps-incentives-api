package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Prison
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerExtraInfo

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
) {

  private fun getClient(useClientCredentials: Boolean = false): WebClient {
    return if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient
  }

  suspend fun getPrisonerExtraInfo(bookingId: Long, useClientCredentials: Boolean = false): PrisonerExtraInfo {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/bookings/$bookingId?extraInfo=true")
      .retrieve()
      .awaitBody()
  }

  suspend fun getActivePrisons(useClientCredentials: Boolean = false): List<Prison> {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/agencies/prisons")
      .retrieve()
      .awaitBody()
  }
}
