package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer

@Configuration
class PageableWebFluxConfig : WebFluxConfigurer {
  override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
    configurer.addCustomResolver(ReactivePageableHandlerMethodArgumentResolver())
  }
}
