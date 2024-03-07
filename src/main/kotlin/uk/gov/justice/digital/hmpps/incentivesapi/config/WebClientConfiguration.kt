package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.oauth}") val authBaseUri: String,
  @Value("\${api.base.url.prison}") private val prisonRootUri: String,
  @Value("\${api.base.url.offender-search}") private val offenderSearchUri: String,
  @Value("\${api.timeout:2m}") val healthTimeout: Duration,
) {

  @Bean
  fun authWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(authBaseUri, healthTimeout)

  @Bean
  fun prisonWebClient(): WebClient {
    val httpClient = HttpClient.create().responseTimeout(healthTimeout)
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .filter(AuthTokenFilterFunction())
      .build()
  }

  @Bean
  fun prisonWebClientClientCredentials(reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(reactiveAuthorizedClientManager, registrationId = SYSTEM_USERNAME, url = prisonRootUri, healthTimeout)

  @Bean
  fun prisonHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(prisonRootUri, healthTimeout)

  @Bean
  fun offenderSearchHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(offenderSearchUri, healthTimeout)

  @Bean
  fun offenderSearchWebClient(reactiveAuthorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(reactiveAuthorizedClientManager, registrationId = SYSTEM_USERNAME, url = offenderSearchUri, healthTimeout)

}
