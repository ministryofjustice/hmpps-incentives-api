package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

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
  fun prisonWebClient(): WebClient {
    val httpClient = HttpClient.create().responseTimeout(Duration.ofMinutes(2))
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .filter(AuthTokenFilterFunction())
      .build()
  }

  @Bean
  fun prisonHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(prisonRootUri)
      .build()
  }
}
