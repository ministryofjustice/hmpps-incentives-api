package uk.gov.justice.digital.hmpps.incentivesapi.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@Import(JwtAuthorisationHelper::class, ClientTrackingWebFilter::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingWebFilterTest {

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingWebFilter: ClientTrackingWebFilter

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthorisationHelper: JwtAuthorisationHelper

  private val tracer: Tracer = otelTesting.openTelemetry.getTracer("test")
  private val filterChain = WebFilterChain { Mono.empty() }

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetry() {
    // Given
    val token = jwtAuthorisationHelper.createJwtAccessToken(clientId = "hmpps-incentives-api", username = "bob")
    val exchange = MockServerWebExchange.builder(
      MockServerHttpRequest.get("http://incentives")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token").build(),
    ).build()

    // When
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingWebFilter.filter(exchange, filterChain) }
      end()
    }

    // Then
    otelTesting.assertTraces().hasTracesSatisfyingExactly(
      { t ->
        t.hasSpansSatisfyingExactly(
          {
            it.hasAttribute(AttributeKey.stringKey("username"), "bob")
            it.hasAttribute(AttributeKey.stringKey("enduser.id"), "bob")
            it.hasAttribute(AttributeKey.stringKey("clientId"), "hmpps-incentives-api")
          },
        )
      },
    )
  }

  @Test
  fun shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {
    // Given
    val token = jwtAuthorisationHelper.createJwtAccessToken(clientId = "hmpps-incentives-api", username = null)
    val exchange = MockServerWebExchange.builder(
      MockServerHttpRequest.get("http://incentives")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token").build(),
    ).build()

    // When
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingWebFilter.filter(exchange, filterChain) }
      end()
    }

    // Then
    otelTesting.assertTraces().hasTracesSatisfyingExactly(
      { t ->
        t.hasSpansSatisfyingExactly(
          {
            it.hasAttribute(AttributeKey.stringKey("clientId"), "hmpps-incentives-api")
          },
        )
      },
    )
  }

  private companion object {
    @JvmStatic
    @RegisterExtension
    private val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
  }
}
