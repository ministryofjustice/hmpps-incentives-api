package uk.gov.justice.digital.hmpps.incentivesapi.integration

import org.junit.jupiter.api.Test

/**
 * Builds the schema so the SchemaSpy report can be generated against it.
 *
 * Excluded from normal test runs; run with `./gradlew -Pinit-db=true test` (see build.gradle.kts).
 * Starting the application context is enough - this service reads through R2DBC at runtime, but Flyway
 * still migrates over JDBC on startup.
 *
 * Extends [SqsIntegrationTestBase] rather than [IntegrationTestBase] even though it needs nothing from
 * SQS: only that base starts LocalStack, and the application context wires HmppsQueueService.
 */
class InitialiseDatabase : SqsIntegrationTestBase() {

  @Test
  fun `initialises database`() {
    println("Database has been initialised by SqsIntegrationTestBase")
  }
}
