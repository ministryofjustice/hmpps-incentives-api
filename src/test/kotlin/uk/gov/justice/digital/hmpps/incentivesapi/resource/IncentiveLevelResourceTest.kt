package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import java.time.Clock

class IncentiveLevelResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @Nested
  inner class `retrieving incentive levels` {
    @ParameterizedTest
    @ValueSource(
      strings = [
        "/incentive/levels",
        "/incentive/levels/STD",
        "/incentive/levels/MISSING",
        "/incentive/prison-levels/MDI",
        "/incentive/prison-levels/MDI/level/STD",
        "/incentive/prison-levels/MISSING/level/STD",
        "/incentive/prison-levels/MDI/level/MISSING",
      ],
    )
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
          true,
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
          true,
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
          true,
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
          true,
        )
    }

    @Test
    fun `fails to return level details of missing level`() {
      webTestClient.get()
        .uri("/incentive/levels/bas")
        .headers(setAuthorisation())
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `bas`")
    }
  }

  @Nested
  inner class `modifying incentive levels` {
    private fun <T : WebTestClient.RequestHeadersSpec<*>> T.withCentralAuthorisation(): T = apply {
      headers(
        setAuthorisation(
          user = "USER_ADM",
          roles = listOf("ROLE_MAINTAIN_INCENTIVE_LEVELS"),
          scopes = listOf("read", "write"),
        ),
      )
    }

    @Test
    fun `creates a level`() {
      val maxSequence = runBlocking {
        incentiveLevelRepository.findMaxSequence()!!
      }

      webTestClient.post()
        .uri("/incentive/levels")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "EN4", "description": "Enhanced 4", "active": false}
          """,
        )
        .exchange()
        .expectStatus().isCreated
        .expectBody().json(
          // language=json
          """
          {"code": "EN4", "description": "Enhanced 4", "active": false}
          """,
          true,
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

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_ADDED").let {
        assertThat(it["code"]).isEqualTo("EN4")
      }
    }

    @Test
    fun `requires correct role to create a level`() {
      webTestClient.post()
        .uri("/incentive/levels")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "EN4", "description": "Enhanced 4", "active": false}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to create a level when media type is not supported`() {
      webTestClient.post()
        .uri("/incentive/levels")
        .withCentralAuthorisation()
        .header("Content-Type", "application/yaml")
        .bodyValue(
          // language=yaml
          """
          code: STD
          description: Silver
          active: false
          """.trimIndent(),
        )
        .exchange()
        .expectErrorResponse(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "Unsupported media type",
          "accepted types: application/json",
        )

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)

        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.code).isEqualTo("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to create a level when code already exists`() {
      webTestClient.post()
        .uri("/incentive/levels")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": false}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Incentive level with code STD already exists")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)

        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.code).isEqualTo("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
        assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
      ],
    )
    fun `fails to create a level with missing fields`(body: String) {
      webTestClient.post()
        .uri("/incentive/levels")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(body)
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Parameter conversion failure")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)
      }

      assertNoAuditMessageSent()
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
      ],
    )
    fun `fails to create a level with invalid fields`(body: String) {
      webTestClient.post()
        .uri("/incentive/levels")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(body)
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `reorders complete set of incentive levels`() {
      webTestClient.patch()
        .uri("/incentive/level-order")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["EN3", "EN2", "ENT", "ENH", "STD", "BAS"]
          """,
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
          true,
        )

      runBlocking {
        val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
        assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("EN3", "EN2", "ENT", "ENH", "STD", "BAS"))
        assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 6))
        assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
          assertThat(it).isEqualTo(now)
        }
      }

      assertAuditMessageSentWithList<String>("INCENTIVE_LEVELS_REORDERED").let {
        assertThat(it).isEqualTo(listOf("EN3", "EN2", "ENT", "ENH", "STD", "BAS"))
      }
    }

    @Test
    fun `requires correct role to reorder levels`() {
      webTestClient.patch()
        .uri("/incentive/level-order")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["EN3", "EN2", "ENT", "ENH", "STD", "BAS"]
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevelCodes = incentiveLevelRepository.findAllByOrderBySequence().map { it.code }.toList()
        assertThat(incentiveLevelCodes).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to reorder incentive levels when some are missing`() {
      webTestClient.patch()
        .uri("/incentive/level-order")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["EN3", "EN2", "EN4", "ENH", "STD", "BAS"]
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `EN4`")

      runBlocking {
        val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
        assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
        assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
        assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
          assertThat(it).isNotEqualTo(now)
        }
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to reorder incentive levels when not enough provided`() {
      webTestClient.patch()
        .uri("/incentive/level-order")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["BAS"]
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "must have size of at least 2")

      runBlocking {
        val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
        assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
        assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to reorder incomplete set of incentive levels`() {
      webTestClient.patch()
        .uri("/incentive/level-order")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["EN3", "EN2", "ENH", "STD", "BAS"]
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "All incentive levels required when setting order. Missing: `ENT`")

      runBlocking {
        val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
        assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
        assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `updates a level`() {
      webTestClient.put()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": true}
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": true}
          """,
          true,
        )

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Silver")
        assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it["description"]).isEqualTo("Silver")
      }
    }

    @Test
    fun `requires correct role to update a level`() {
      webTestClient.put()
        .uri("/incentive/levels/STD")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": true}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isNotEqualTo("Silver")
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to update a missing level`() {
      webTestClient.put()
        .uri("/incentive/levels/std")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "std", "description": "Silver", "active": true}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        var incentiveLevel = incentiveLevelRepository.findById("std")
        assertThat(incentiveLevel).isNull()
        incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to update a level with mismatched codes`() {
      webTestClient.put()
        .uri("/incentive/levels/ENH")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": false}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Incentive level codes must match in URL and payload")

      runBlocking {
        var incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
        incentiveLevel = incentiveLevelRepository.findById("ENH")
        assertThat(incentiveLevel?.description).isEqualTo("Enhanced")
        assertThat(incentiveLevel?.active).isTrue
      }

      assertNoAuditMessageSent()
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
      ],
    )
    fun `fails to update a level with missing fields`(body: String) {
      webTestClient.put()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(body)
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Parameter conversion failure")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to update a level with invalid fields`() {
      webTestClient.put()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "", "active": false}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        var incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
        incentiveLevel = incentiveLevelRepository.findById("")
        assertThat(incentiveLevel).isNull()
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `partially updates a level`() {
      webTestClient.patch()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"description": "Silver"}
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {"code": "STD", "description": "Silver", "active": true}
          """,
          true,
        )

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Silver")
        assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it["description"]).isEqualTo("Silver")
      }
    }

    @Test
    fun `requires correct role to partially update a level`() {
      webTestClient.patch()
        .uri("/incentive/levels/STD")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"description": "Silver"}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isNotEqualTo("Silver")
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to partially update a missing level`() {
      webTestClient.patch()
        .uri("/incentive/levels/std")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"description": "Silver"}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        var incentiveLevel = incentiveLevelRepository.findById("std")
        assertThat(incentiveLevel).isNull()
        incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `partially updates a level but does not change the code`() {
      webTestClient.patch()
        .uri("/incentive/levels/ENH")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"code": "STD", "description": "Gold", "active": false}
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {"code": "ENH", "description": "Gold", "active": false}
          """,
          true,
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

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it["code"]).isEqualTo("ENH")
        assertThat(it["description"]).isEqualTo("Gold")
      }
    }

    @Test
    fun `fails to partially update a level with invalid fields`() {
      webTestClient.patch()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {"description": "", "active": false}
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isEqualTo("Standard")
        assertThat(incentiveLevel?.active).isTrue
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `deactivates a level`() {
      webTestClient.delete()
        .uri("/incentive/levels/STD")
        .withCentralAuthorisation()
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {"code": "STD", "description": "Standard", "active": false}
          """,
          true,
        )

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.active).isFalse
        assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it["code"]).isEqualTo("STD")
        assertThat(it["active"]).isEqualTo(false)
      }
    }

    @Test
    fun `deactivates an inactive level`() {
      webTestClient.delete()
        .uri("/incentive/levels/ENT")
        .withCentralAuthorisation()
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {"code": "ENT", "description": "Entry", "active": false}
          """,
          true,
        )

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("ENT")
        assertThat(incentiveLevel?.active).isFalse
        assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it["code"]).isEqualTo("ENT")
        assertThat(it["active"]).isEqualTo(false)
      }
    }

    @Test
    fun `requires correct role to deactivate a level`() {
      webTestClient.delete()
        .uri("/incentive/levels/STD")
        .headers(setAuthorisation())
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.active).isTrue
      }

      assertNoAuditMessageSent()
    }

    @Test
    fun `fails to deactivate a missing level`() {
      webTestClient.delete()
        .uri("/incentive/levels/std")
        .withCentralAuthorisation()
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        var incentiveLevel = incentiveLevelRepository.findById("std")
        assertThat(incentiveLevel).isNull()
        incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.active).isTrue
        assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
    }
  }
}
