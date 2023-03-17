package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class IncentiveLevelResourceTest : SqsIntegrationTestBase() {
  companion object {
    val clock: Clock = Clock.fixed(
      Instant.parse("2022-03-15T12:34:56+00:00"),
      ZoneId.of("Europe/London"),
    )
    val now: LocalDateTime = LocalDateTime.now(clock)
  }

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

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
        assertThat(it).contains("No incentive level found for code `bas`")
      }
  }

  @Test
  fun `creates a level`() {
    val maxSequence = runBlocking {
      incentiveLevelRepository.findMaxSequence()!!
    }

    webTestClient.post()
      .uri("/incentive/levels")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        {"code": "EN4", "description": "Enhanced 4", "active": false}
        """
      )
      .exchange()
      .expectStatus().isCreated
      .expectBody().json(
        // language=json
        """
        {"code": "EN4", "description": "Enhanced 4", "active": false}
        """,
        true
      )

    runBlocking {
      assertThat(incentiveLevelRepository.count()).isEqualTo(7)

      val incentiveLevel = incentiveLevelRepository.findById("EN4")
      assertThat(incentiveLevel?.code).isEqualTo("EN4")
      assertThat(incentiveLevel?.description).isEqualTo("Enhanced 4")
      assertThat(incentiveLevel?.active).isFalse
      assertThat(incentiveLevel?.sequence).isGreaterThan(maxSequence)
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
    }
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to create a level`() {}

  @Test
  fun `fails to create a level when media type is not supported`() {
    webTestClient.post()
      .uri("/incentive/levels")
      .headers(setAuthorisation())
      .header("Content-Type", "application/yaml")
      .bodyValue(
        // language=yaml
        """
        code: STD
        description: Silver
        active: false
        """.trimIndent()
      )
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Unsupported media type")
        assertThat(it).contains("accepted types: application/json")
      }

    runBlocking {
      assertThat(incentiveLevelRepository.count()).isEqualTo(6)

      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.code).isEqualTo("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
    }
  }

  @Test
  fun `fails to create a level when code already exists`() {
    webTestClient.post()
      .uri("/incentive/levels")
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
        assertThat(it).contains("Incentive level with code STD already exists")
      }

    runBlocking {
      assertThat(incentiveLevelRepository.count()).isEqualTo(6)

      val incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.code).isEqualTo("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
      assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      // language=json
      """
      {"code": "EN4", "active": false}
      """,
      // language=json
      """
      {"description": "Enhanced 4", "active": false}
      """,
    ]
  )
  fun `fails to create a level with missing fields`(body: String) {
    webTestClient.post()
      .uri("/incentive/levels")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Parameter conversion failure")
      }

    runBlocking {
      assertThat(incentiveLevelRepository.count()).isEqualTo(6)
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      // language=json
      """
      {"code": "", "description": "Enhanced 4", "active": false}
      """,
      // language=json
      """
      {"code": "Enhanced", "description": "Enhanced Plus", "active": false}
      """,
      // language=json
      """
      {"code": "EN4", "description": "", "active": false}
      """,
    ]
  )
  fun `fails to create a level with invalid fields`(body: String) {
    webTestClient.post()
      .uri("/incentive/levels")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("Invalid parameters")
      }

    runBlocking {
      assertThat(incentiveLevelRepository.count()).isEqualTo(6)
    }
  }

  @Test
  fun `reorders complete set of incentive levels`() {
    webTestClient.patch()
      .uri("/incentive/level-order")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        ["EN3", "EN2", "ENT", "ENH", "STD", "BAS"]
        """
      )
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        [
          {"code": "EN3", "description": "Enhanced 3", "active": true},
          {"code": "EN2", "description": "Enhanced 2", "active": true},
          {"code": "ENT", "description": "Entry", "active": false},
          {"code": "ENH", "description": "Enhanced", "active": true},
          {"code": "STD", "description": "Standard", "active": true},
          {"code": "BAS", "description": "Basic", "active": true}
        ]
        """,
        true
      )

    runBlocking {
      val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
      assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("EN3", "EN2", "ENT", "ENH", "STD", "BAS"))
      assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 6))
      assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
        assertThat(it).isEqualTo(now)
      }
    }
  }

  // TODO: add once roles have been determined
  // fun `requires correct role to reorder levels`() {}

  @Test
  fun `fails to reorder incentive levels when some are missing`() {
    webTestClient.patch()
      .uri("/incentive/level-order")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        ["EN3", "EN2", "EN4", "ENH", "STD", "BAS"]
        """
      )
      .exchange()
      .expectStatus().isNotFound
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("No incentive level found for code `EN4`")
      }

    runBlocking {
      val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
      assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
      assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
      assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
        assertThat(it).isNotEqualTo(now)
      }
    }
  }

  @Test
  fun `fails to reorder incentive levels when not enough provided`() {
    webTestClient.patch()
      .uri("/incentive/level-order")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        ["BAS"]
        """
      )
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("must have size of at least 2")
      }

    runBlocking {
      val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
      assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
      assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
    }
  }

  @Test
  fun `fails to reorder incomplete set of incentive levels`() {
    webTestClient.patch()
      .uri("/incentive/level-order")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .bodyValue(
        // language=json
        """
        ["EN3", "EN2", "ENH", "STD", "BAS"]
        """
      )
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.userMessage").value<String> {
        assertThat(it).contains("All incentive levels required when setting order. Missing: `ENT`")
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
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
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
        assertThat(it).contains("No incentive level found for code `std`")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
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
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
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
        assertThat(it).contains("No incentive level found for code `std`")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
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
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.description).isEqualTo("Standard")
      assertThat(incentiveLevel?.active).isTrue
      assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
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
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
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
      assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
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
        assertThat(it).contains("No incentive level found for code `std`")
      }

    runBlocking {
      var incentiveLevel = incentiveLevelRepository.findById("std")
      assertThat(incentiveLevel).isNull()
      incentiveLevel = incentiveLevelRepository.findById("STD")
      assertThat(incentiveLevel?.active).isTrue
      assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
    }
  }
}
