package uk.gov.justice.digital.hmpps.incentivesapi.config

import io.r2dbc.spi.ConnectionFactory
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

const val defaultLockAtLeastFor = "PT5M"
const val defaultLockAtMostFor = "PT10M"

@Configuration
@EnableScheduling
@Profile("!test") // prevent scheduler running during integration tests
@EnableSchedulerLock(
  defaultLockAtLeastFor = defaultLockAtLeastFor,
  defaultLockAtMostFor = defaultLockAtMostFor,
)
class SchedulerConfiguration {

  @Bean
  fun lockProvider(connectionFactory: ConnectionFactory): LockProvider {
    return R2dbcLockProvider(connectionFactory)
  }
}
