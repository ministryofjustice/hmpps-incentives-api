package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientId
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import java.util.concurrent.ConcurrentHashMap

class ClientCachingOAuth2AuthorizedClientService(
  private val clientRegistrationRepository: ReactiveClientRegistrationRepository,
) : ReactiveOAuth2AuthorizedClientService {
  private val authorizedClients: MutableMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient> = ConcurrentHashMap()

  override fun <T : OAuth2AuthorizedClient?> loadAuthorizedClient(
    clientRegistrationId: String?,
    principalName: String?,
  ): Mono<T?> = Mono.justOrEmpty(clientRegistrationId)
    .flatMap { id -> clientRegistrationRepository.findByRegistrationId(id) }
    .mapNotNull { clientRegistration ->
      @Suppress("UNCHECKED_CAST")
      authorizedClients[OAuth2AuthorizedClientId(clientRegistration.registrationId, SYSTEM_USERNAME)] as? T
    }

  override fun saveAuthorizedClient(authorizedClient: OAuth2AuthorizedClient, principal: Authentication?): Mono<Void> {
    authorizedClients[
      OAuth2AuthorizedClientId(authorizedClient.clientRegistration.registrationId, SYSTEM_USERNAME),
    ] = authorizedClient
    return Mono.empty()
  }

  override fun removeAuthorizedClient(clientRegistrationId: String?, principalName: String?): Mono<Void> {
    return Mono.justOrEmpty(clientRegistrationId)
      .flatMap { id -> clientRegistrationRepository.findByRegistrationId(id) }
      .doOnNext { registration ->
        val principal = principalName ?: SYSTEM_USERNAME
        authorizedClients.remove(OAuth2AuthorizedClientId(registration.registrationId, principal))
      }
      .then()
  }
}
