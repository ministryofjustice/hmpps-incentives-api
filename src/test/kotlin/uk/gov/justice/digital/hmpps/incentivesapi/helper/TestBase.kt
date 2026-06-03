package uk.gov.justice.digital.hmpps.incentivesapi.helper

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import uk.gov.justice.digital.hmpps.incentivesapi.config.PostgresContainer
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

const val FIXED_TIME = "2022-03-15T12:34:56.123456+00:00"

@ActiveProfiles("test")
abstract class TestBase {

  companion object {
    val zoneId: ZoneId = ZoneId.of("Europe/London")
    val clock: Clock = Clock.fixed(
      Instant.parse(FIXED_TIME),
      zoneId,
    )
    val now: LocalDateTime = LocalDateTime.now(clock)

    private val pgContainer = PostgresContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.r2dbc.url") { pgContainer.jdbcUrl.replace("jdbc:", "r2dbc:") }
        registry.add("spring.r2dbc.username", pgContainer::getUsername)
        registry.add("spring.r2dbc.password", pgContainer::getPassword)
        registry.add("spring.flyway.url", pgContainer::getJdbcUrl)
        registry.add("spring.flyway.user", pgContainer::getUsername)
        registry.add("spring.flyway.password", pgContainer::getPassword)
      }
    }
  }
}
