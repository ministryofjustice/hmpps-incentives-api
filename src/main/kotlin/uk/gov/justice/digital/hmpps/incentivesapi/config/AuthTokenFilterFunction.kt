package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.CoreSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context


class AuthTokenFilterFunction : ExchangeFilterFunction {

  override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
    return AuthTokenResponseMono(request, next)
  }
}

class AuthTokenResponseMono(
  private val request: ClientRequest,
  private val next: ExchangeFunction
) : Mono<ClientResponse>() {

  override fun subscribe(subscriber: CoreSubscriber<in ClientResponse>) {
    val context = subscriber.currentContext()
    val requestBuilder = ClientRequest.from(request)
    requestBuilder.header(HttpHeaders.AUTHORIZATION, context.get(HttpHeaders.AUTHORIZATION))
    val mutatedRequest = requestBuilder.build()
    next.exchange(mutatedRequest).subscribe(subscriber)
  }
}

@Component
@Order(1)
class UserContextFilter : WebFilter {
  override fun filter(serverWebExchange: ServerWebExchange, webFilterChain: WebFilterChain): Mono<Void> {
    val authHeader = serverWebExchange.request.headers[HttpHeaders.AUTHORIZATION]?.firstOrNull()?:"none"

    return webFilterChain.filter(serverWebExchange).contextWrite {
      transferAuthHeader(authHeader, it)
    }
  }

}

fun <T> Flux<T>.withToken(authToken: String): Flux<T> =
  this.contextWrite { transferAuthHeader(authToken, it) }

fun <T> Mono<T>.withToken(authToken: String): Mono<T> =
  this.contextWrite { transferAuthHeader(authToken, it) }

private fun transferAuthHeader(authToken: String, context: Context) =
  context.put(HttpHeaders.AUTHORIZATION, authToken)
