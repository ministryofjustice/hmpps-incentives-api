package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
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

fun <T> Flux<T>.withToken(authToken: String): Flux<T> =
  this.contextWrite { transferAuthHeader(authToken, it) }

fun <T> Mono<T>.withToken(authToken: String): Mono<T> =
  this.contextWrite { transferAuthHeader(authToken, it) }

fun transferAuthHeader(authToken: String, context: Context) =
  context.put(HttpHeaders.AUTHORIZATION, authToken)
