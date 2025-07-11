package uk.gov.justice.digital.hmpps.incentivesapi.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck

@Component("hmppsAuth")
class AuthHealthPing(
  @Qualifier("authWebClient") webClient: WebClient,
) : ReactiveHealthPingCheck(webClient)

@Component("prisonApi")
class PrisonHealthPing(
  @Qualifier("prisonHealthWebClient") webClient: WebClient,
) : ReactiveHealthPingCheck(webClient)

@Component("prisonerSearch")
class PrisonerSearchHealthPing(
  @Qualifier("prisonerSearchHealthWebClient") webClient: WebClient,
) : ReactiveHealthPingCheck(webClient)

@Component("locations")
class LocationsHealthPing(
  @Qualifier("locationsHealthWebClient") webClient: WebClient,
) : ReactiveHealthPingCheck(webClient)
