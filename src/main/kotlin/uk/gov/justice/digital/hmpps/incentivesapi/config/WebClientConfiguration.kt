package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.incentivesapi.utils.UserContext

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.oauth}") val authBaseUri: String,
  @Value("\${api.base.url.prison}") private val prisonRootUri: String
) {

  @Bean
  fun authWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(authBaseUri)
      .build()
  }

  @Bean
  fun prisonWebAsyncClient(): WebClient {
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .filter(AuthTokenFilterFunction())
      .build()
  }

  @Bean
  fun prisonWebSyncClient(): WebClient {
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  @Bean
  fun prisonHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .build()
  }

  private fun addAuthHeaderFilterFunction(): ExchangeFilterFunction {
    return ExchangeFilterFunction { request: ClientRequest?, next: ExchangeFunction ->
      val filtered = ClientRequest.from(request)
        .header(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken())
        .build()
      next.exchange(filtered)
    }
  }
}
