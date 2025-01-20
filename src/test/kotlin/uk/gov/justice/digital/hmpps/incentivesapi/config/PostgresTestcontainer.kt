package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

object PostgresTestcontainer : Testcontainer<PostgreSQLContainer<Nothing>>("postgres", 5432) {
  override fun start(): PostgreSQLContainer<Nothing> {
    log.info("Creating a Postgres database")
    return PostgreSQLContainer<Nothing>(
      DockerImageName.parse("postgres").withTag("16"),
    ).apply {
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      withDatabaseName("incentives")
      withUsername("incentives")
      withPassword("incentives")
      setWaitStrategy(Wait.forListeningPort())
      withReuse(true)

      start()
    }
  }

  override fun setupProperties(instance: PostgreSQLContainer<Nothing>, registry: DynamicPropertyRegistry) {
    registry.add("spring.r2dbc.url") { instance.jdbcUrl.replace("jdbc:", "r2dbc:") }
    registry.add("spring.r2dbc.username", instance::getUsername)
    registry.add("spring.r2dbc.password", instance::getPassword)
    registry.add("spring.flyway.url", instance::getJdbcUrl)
    registry.add("spring.flyway.user", instance::getUsername)
    registry.add("spring.flyway.password", instance::getPassword)
  }
}
