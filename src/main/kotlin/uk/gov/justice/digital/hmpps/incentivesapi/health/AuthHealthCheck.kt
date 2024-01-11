package uk.gov.justice.digital.hmpps.incentivesapi.health

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class AuthHealthCheck
  @Autowired
  constructor(
    @Qualifier("authWebClient") authWebClient: WebClient,
    @Value("\${api.health-timeout-ms}") timeout: Duration,
  ) : HealthCheck(authWebClient, timeout)
