package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveUsernameAwareTokenRequestOAuth2AuthorizedClientManager
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @param:Value($$"${api.base.url.oauth}") val authBaseUri: String,
  @param:Value($$"${api.base.url.prison}") private val prisonRootUri: String,
  @param:Value($$"${api.base.url.prisoner-search}") private val prisonerSearchUri: String,
  @param:Value($$"${api.base.url.locations}") private val locationsUri: String,
  @param:Value($$"${api.base.url.case-notes}") private val caseNotesApiUri: String,
  @param:Value($$"${api.health-timeout:10s}") val healthTimeout: Duration,
  @param:Value($$"${api.timeout:2m}") val timeout: Duration,
) {

  @Bean
  fun prisonHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.reactiveHealthWebClient(prisonRootUri, healthTimeout)

  @Bean
  fun locationsHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.reactiveHealthWebClient(locationsUri, healthTimeout)

  @Bean
  fun prisonerSearchHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.reactiveHealthWebClient(prisonerSearchUri, healthTimeout)

  @Bean
  fun authHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(authBaseUri, timeout)

  @Bean
  fun prisonWebClientClientCredentials(
    reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(
    reactiveAuthorizedClientManager,
    registrationId = SYSTEM_USERNAME,
    url = prisonRootUri,
    timeout,
  )

  @Bean
  fun caseNoteApiClientClientCredentials(
    reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(
    reactiveAuthorizedClientManager,
    registrationId = SYSTEM_USERNAME,
    url = caseNotesApiUri,
    timeout,
  )

  @Bean
  fun locationsWebClient(
    reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(
    reactiveAuthorizedClientManager,
    registrationId = SYSTEM_USERNAME,
    url = locationsUri,
    timeout,
  )

  @Bean
  fun prisonerSearchWebClient(
    reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(
    reactiveAuthorizedClientManager,
    registrationId = SYSTEM_USERNAME,
    url = prisonerSearchUri,
    timeout,
  )

  @Bean
  fun prisonWebClient(
    reactiveClientRegistrationRepository: ReactiveClientRegistrationRepository,
    reactiveOAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(
    reactiveUsernameAwareTokenRequestOAuth2AuthorizedClientManager(
      reactiveClientRegistrationRepository,
      reactiveOAuth2AuthorizedClientService,
    ),
    registrationId = "prison-api",
    url = prisonRootUri,
    timeout,
  )
}
