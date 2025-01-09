package uk.gov.justice.digital.hmpps.incentivesapi.helper

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import uk.gov.justice.digital.hmpps.incentivesapi.config.PostgresTestcontainer

@ActiveProfiles("test")
abstract class TestBase {

  companion object {
    private val postgresInstance = PostgresTestcontainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun postgresProperties(registry: DynamicPropertyRegistry) {
      postgresInstance?.let { PostgresTestcontainer.setupProperties(postgresInstance, registry) }
    }
  }
}
