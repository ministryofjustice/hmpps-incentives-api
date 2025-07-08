package uk.gov.justice.digital.hmpps.incentivesapi.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase

class HealthCheckTest : SqsIntegrationTestBase() {
  @Test
  fun `health page reports ok`() {
    stubPingWithResponse(200)
    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  private fun stubPingWithResponse(@Suppress("SameParameterValue") status: Int) {
    hmppsAuthMockServer.stubHealthPing(status)
    prisonApiMockServer.stubHealthPing(status)
    offenderSearchMockServer.stubHealthPing(status)
    locationsMockServer.stubHealthPing(status)
  }
}
