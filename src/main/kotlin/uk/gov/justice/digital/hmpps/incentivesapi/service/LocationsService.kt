package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class LocationsService(private val locationsWebClient: WebClient) {
  /**
   * Get a location by it's key, e.g. `MDI-1`.
   * Requires role VIEW_LOCATIONS
   */
  suspend fun getByKey(key: String): Location {
    return locationsWebClient.get()
      .uri("/locations/key/$key")
      .retrieve()
      .awaitBody()
  }
}

data class Location(
  val id: String,
  val key: String,
  val prisonId: String,
  val localName: String?,
  val pathHierarchy: String,
  // Other fields omitted
)
