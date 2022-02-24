package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableR2dbcRepositories
class ResourceServerConfiguration {

  @Bean
  fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
    return http
      .csrf { it.disable() } // crst not needed an rest api
      .authorizeExchange {
        it.pathMatchers(
          "/favicon.ico", "/csrf",
          "/health/**", "/info",
          "/v3/api-docs/**", "/webjars/swagger-ui/**", "/swagger-ui.html"
        ).permitAll()
          .anyExchange().authenticated()
      }
      .oauth2ResourceServer { it.jwt().jwtAuthenticationConverter(AuthAwareTokenConverter()) }
      .build()
  }
}
