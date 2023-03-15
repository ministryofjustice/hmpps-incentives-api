package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase

class IncentiveLevelResourceTest : SqsIntegrationTestBase() {
  @ParameterizedTest
  @ValueSource(strings = ["/incentive/levels", "/incentive/levels/STD", "/incentive/levels/MISSING"])
  fun `requires a valid token to retrieve data`(url: String) {
    webTestClient.get()
      .uri(url)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to modify data`() {}

  @Test
  fun `lists levels without any role`() {
    webTestClient.get()
      .uri("/incentive/levels")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        [
          {"code": "BAS", "description": "Basic", "active": true},
          {"code": "STD", "description": "Standard", "active": true},
          {"code": "ENH", "description": "Enhanced", "active": true},
          {"code": "EN2", "description": "Enhanced 2", "active": true},
          {"code": "EN3", "description": "Enhanced 3", "active": true}
        ]
        """,
        true
      )
  }

  @Test
  fun `lists all levels without any role`() {
    webTestClient.get()
      .uri("/incentive/levels?with-inactive=true")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        [
          {"code": "BAS", "description": "Basic", "active": true},
          {"code": "STD", "description": "Standard", "active": true},
          {"code": "ENH", "description": "Enhanced", "active": true},
          {"code": "EN2", "description": "Enhanced 2", "active": true},
          {"code": "EN3", "description": "Enhanced 3", "active": true},
          {"code": "ENT", "description": "Entry", "active": false}
        ]
        """,
        true
      )
  }

  @Test
  fun `returns level details of active level`() {
    webTestClient.get()
      .uri("/incentive/levels/EN2")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "EN2", "description": "Enhanced 2", "active": true}
        """,
        true
      )
  }

  @Test
  fun `returns level details of inactive level`() {
    webTestClient.get()
      .uri("/incentive/levels/ENT")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "ENT", "description": "Entry", "active": false}
        """,
        true
      )
  }

  @Test
  fun `fails to return level details of missing level`() {
    webTestClient.get()
      .uri("/incentive/levels/bas")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isNotFound
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("No incentive level found for code bas")
      }
  }
}
