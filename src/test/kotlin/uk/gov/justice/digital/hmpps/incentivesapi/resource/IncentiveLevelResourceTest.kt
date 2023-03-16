package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository

class IncentiveLevelResourceTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var incentiveLevelRepository: IncentiveLevelRepository

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    incentiveLevelRepository.deleteAll()
    incentiveLevelRepository.saveAll(
      listOf(
        IncentiveLevel(
          code = "BAS", description = "Basic", sequence = 1, new = true
        ),
        IncentiveLevel(
          code = "STD", description = "Standard", sequence = 2, new = true
        ),
        IncentiveLevel(
          code = "ENH", description = "Enhanced", sequence = 3, new = true
        ),
        IncentiveLevel(
          code = "EN2", description = "Enhanced 2", sequence = 4, new = true
        ),
        IncentiveLevel(
          code = "EN3", description = "Enhanced 3", sequence = 5, new = true
        ),
        IncentiveLevel(
          code = "ENT", description = "Entry", active = false, sequence = 99, new = true
        ),
      )
    ).collect()
  }

  @ParameterizedTest
  @ValueSource(strings = ["/incentive/levels", "/incentive/levels/STD", "/incentive/levels/MISSING"])
  fun `requires a valid token to retrieve data`(url: String) {
    webTestClient.get()
      .uri(url)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

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

  @Test
  fun `updates a level`() {
    webTestClient.put()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "STD", "description": "Silver", "active": true}
        """
      )
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "STD", "description": "Silver", "active": true}
        """,
        true
      )

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Silver")
    }
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to update a level`() {}

  @Test
  fun `fails to update a missing level`() {
    webTestClient.put()
      .uri("/incentive/levels/std")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "std", "description": "Silver", "active": true}
        """
      )
      .exchange()
      .expectStatus().isNotFound
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("No incentive level found for code std")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
    }
  }

  @Test
  fun `fails to update a level with mismatched codes`() {
    webTestClient.put()
      .uri("/incentive/levels/ENH")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "STD", "description": "Silver", "active": false}
        """
      )
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Incentive level codes must match in URL and payload")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
      incentiveLevel = incentiveLevelRepository.findById("ENH")
      assertThat(incentiveLevel?.description).isEqualTo("Enhanced")
      assertThat(incentiveLevel?.active).isTrue
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      // language=json
      """
      {"code": "STD", "active": false}
      """,
      // language=json
      """
      {"description": "Silver", "active": false}
      """,
      // language=json
      """
      {"active": false}
      """,
    ]
  )
  fun `fails to update a level with missing fields`(body: String) {
    webTestClient.put()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Parameter conversion failure")
      }

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
    }
  }

  @Test
  fun `fails to update a level with invalid fields`() {
    webTestClient.put()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "STD", "description": "", "active": false}
        """,
      )
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Invalid parameters")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
      incentiveLevel = incentiveLevelRepository.findById("")
      assertThat(incentiveLevel).isNull()
    }
  }

  @Test
  fun `partially updates a level`() {
    webTestClient.patch()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"description": "Silver"}
        """
      )
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "STD", "description": "Silver", "active": true}
        """,
        true
      )

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Silver")
    }
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to partially update a level`() {}

  @Test
  fun `fails to partially update a missing level`() {
    webTestClient.patch()
      .uri("/incentive/levels/std")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"description": "Silver"}
        """
      )
      .exchange()
      .expectStatus().isNotFound
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("No incentive level found for code std")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
    }
  }

  @Test
  fun `partially updates a level but does not change the code`() {
    webTestClient.patch()
      .uri("/incentive/levels/ENH")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "STD", "description": "Gold", "active": false}
        """
      )
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "ENH", "description": "Gold", "active": false}
        """,
        true
      )

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("ENH")
      assertThat(incentiveLevel?.description).isEqualTo("Gold")
      assertThat(incentiveLevel?.active).isFalse
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
    }
  }

  @Test
  fun `fails to partially update a level with invalid fields`() {
    webTestClient.patch()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"description": "", "active": false}
        """,
      )
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Invalid parameters")
      }

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
    }
  }

  @Test
  fun `deactivates a level`() {
    webTestClient.delete()
      .uri("/incentive/levels/STD")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {"code": "STD", "description": "Standard", "active": false}
        """,
        true
      )

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.active).isFalse
    }
  }

  @Test
  fun `deactivates an inactive level`() {
    webTestClient.delete()
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

    runBlocking {
      val incentiveLevel = incentiveLevelRepository.findById("ENT")
      assertThat(incentiveLevel?.active).isFalse
    }
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to deactivate a level`() {}

  @Test
  fun `fails to deactivate a missing level`() {
    webTestClient.delete()
      .uri("/incentive/levels/std")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isNotFound
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("No incentive level found for code std")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.active).isTrue
    }
  }
}
