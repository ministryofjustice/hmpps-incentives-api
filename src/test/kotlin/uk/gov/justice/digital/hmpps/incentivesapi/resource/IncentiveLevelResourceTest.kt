package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonIncentiveLevelRepository
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
  @Autowired
  private lateinit var prisonIncentiveLevelRepository: PrisonIncentiveLevelRepository

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonIncentiveLevelRepository.deleteAll()
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

  @Nested
  inner class `incentive levels` {
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
      ]
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
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `bas`")
    }
  }

  @Nested
  inner class `modifying incentive levels` {
    private fun <T : WebTestClient.RequestHeadersSpec<*>> T.withCentralAuthorisation(): T = apply {
      headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_INCENTIVE_LEVELS"), scopes = listOf("read", "write")))
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
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)
      }
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
          """.trimIndent()
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
          """
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
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(body)
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Parameter conversion failure")

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
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(body)
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        assertThat(incentiveLevelRepository.count()).isEqualTo(6)
      }
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
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevelCodes = incentiveLevelRepository.findAllByOrderBySequence().map { it.code }.toList()
        assertThat(incentiveLevelCodes).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
      }
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
          """
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
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "must have size of at least 2")

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
        .withCentralAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          ["EN3", "EN2", "ENH", "STD", "BAS"]
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "All incentive levels required when setting order. Missing: `ENT`")
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
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isNotEqualTo("Silver")
      }
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
          """
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
          """
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
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val incentiveLevel = incentiveLevelRepository.findById("STD")
        assertThat(incentiveLevel?.description).isNotEqualTo("Silver")
      }
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
          """
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
        .withCentralAuthorisation()
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
    }
  }

  @Nested
  inner class `retrieving prison incentive levels` {
    @BeforeEach
    fun setUp() = runBlocking {
      prisonIncentiveLevelRepository.deleteAll()
      listOf("BAS", "STD", "ENH", "ENT").forEach { levelCode ->
        listOf("BAI", "MDI", "WRI").forEach { prisonId ->
          prisonIncentiveLevelRepository.save(
            PrisonIncentiveLevel(
              levelCode = levelCode,
              prisonId = prisonId,
              active = levelCode != "ENT",
              defaultOnAdmission = levelCode == "STD",

              remandTransferLimitInPence = 5500,
              remandSpendLimitInPence = 55000,
              convictedTransferLimitInPence = 1800,
              convictedSpendLimitInPence = 18000,

              visitOrders = 2,
              privilegedVisitOrders = 1,

              new = true,
            )
          )
        }
      }
    }

    @Test
    fun `lists prison levels without any role`() {
      webTestClient.get()
        .uri("/incentive/prison-levels/MDI")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          [
            {
              "levelCode": "BAS", "levelDescription": "Basic", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            },
            {
              "levelCode": "STD", "levelDescription": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            },
            {
              "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
          ]
          """,
          true
        )
    }

    @Test
    fun `returns prison level details of active level`() {
      webTestClient.get()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": false,
            "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true
        )
    }

    @Test
    fun `fails to return prison level details of inactive level`() {
      webTestClient.get()
        .uri("/incentive/prison-levels/MDI/level/ENT")
        .headers(setAuthorisation())
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No active prison incentive level found for code `ENT`")
    }

    @Test
    fun `fails to return prison level details of missing level`() {
      webTestClient.get()
        .uri("/incentive/prison-levels/BAI/level/EN4")
        .headers(setAuthorisation())
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No active prison incentive level found for code `EN4`")
    }
  }

  @Nested
  inner class `modifying prison incentive levels` {
    private fun <T : WebTestClient.RequestHeadersSpec<*>> T.withLocalAuthorisation(): T = apply {
      headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_PRISON_IEP_LEVELS"), scopes = listOf("read", "write")))
    }

    private fun saveLevel(prisonId: String, levelCode: String) = runBlocking {
      prisonIncentiveLevelRepository.save(
        PrisonIncentiveLevel(
          levelCode = levelCode,
          prisonId = prisonId,
          active = levelCode != "ENT",
          defaultOnAdmission = levelCode == "STD",

          remandTransferLimitInPence = 5500,
          remandSpendLimitInPence = 55000,
          convictedTransferLimitInPence = 1800,
          convictedSpendLimitInPence = 18000,

          visitOrders = 2,
          privilegedVisitOrders = 1,

          new = true,
          whenUpdated = now.minusDays(3),
        )
      )
    }

    @Test
    fun `updates a prison incentive level when one exists`() {
      saveLevel("MDI", "STD")
      `updates a prison incentive level when per-prison information does not exist`()
    }

    @Test
    fun `updates a prison incentive level when per-prison information does not exist`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "STD", "prisonId": "MDI", "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "STD", "levelDescription": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
          true
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5600)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }
    }

    @Test
    fun `updates the default prison incentive level for admission`() {
      saveLevel("WRI", "STD") // Standard is the default for admission

      webTestClient.put()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "ENH", "prisonId": "WRI", "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true
        )

      runBlocking {
        var prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse

        prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue

        val defaultLevelCodes =
          prisonIncentiveLevelRepository.findAll().filter { it.defaultOnAdmission && it.prisonId == "WRI" }
            .map { it.prisonId to it.levelCode }.toList()
        assertThat(defaultLevelCodes).isEqualTo(listOf("WRI" to "ENH"))
      }
    }

    @Test
    fun `requires correct role to update a prison incentive level`() {
      saveLevel("MDI", "STD")

      webTestClient.put()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "STD", "prisonId": "MDI", "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to update a prison incentive level when incentive level does not exist`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/MDI/level/std")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "std", "prisonId": "MDI",
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level with mismatched codes`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "ENH", "prisonId": "MDI",
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Incentive level codes must match in URL and payload")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level with mismatched prison id`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "STD", "prisonId": "WRI",
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Prison ids must match in URL and payload")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level with missing fields`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/WRI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "STD", "prisonId": "WRI", "active": true, "defaultOnAdmission": true
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Parameter conversion failure")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level with invalid fields`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "ENH", "prisonId": "WRI",
            "remandTransferLimitInPence": -5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level when making inactive level the default`() {
      webTestClient.put()
        .uri("/incentive/prison-levels/BAI/level/BAS")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "BAS", "prisonId": "BAI", "active": false, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(
          HttpStatus.BAD_REQUEST,
          "A level cannot be made inactive and still be the default for admission",
        )

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to update a prison incentive level when no default for admission would remain`() {
      saveLevel("BAI", "STD")

      webTestClient.put()
        .uri("/incentive/prison-levels/BAI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "STD", "prisonId": "BAI", "active": true, "defaultOnAdmission": false,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to update a non-default prison incentive level when there is no other default level for admission`() {
      // data integrity problem: there is no default level for admission
      saveLevel("BAI", "BAS")
      saveLevel("BAI", "ENH")

      webTestClient.put()
        .uri("/incentive/prison-levels/BAI/level/BAS")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "levelCode": "BAS", "prisonId": "BAI", "active": true, "defaultOnAdmission": false,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1900, "convictedSpendLimitInPence": 19000,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `partially updates a prison incentive level when one exists`() {
      saveLevel("BAI", "BAS")
      `partially updates a prison incentive level when per-prison information does not exist`()
    }

    @Test
    fun `partially updates a prison incentive level when per-prison information does not exist`() {
      webTestClient.patch()
        .uri("/incentive/prison-levels/BAI/level/BAS")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000,
            "visitOrders": 3
          }
          """
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "BAS", "levelDescription": "Basic", "prisonId": "BAI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 3, "privilegedVisitOrders": 1
          }
          """,
          true
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5600)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }
    }

    @Test
    fun `partially updates the default prison incentive level for admission`() {
      saveLevel("WRI", "STD") // Standard is the default for admission

      webTestClient.patch()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "defaultOnAdmission": true
          }
          """
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true
        )

      runBlocking {
        var prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse

        prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue

        val defaultLevelCodes =
          prisonIncentiveLevelRepository.findAll().filter { it.defaultOnAdmission && it.prisonId == "WRI" }
            .map { it.prisonId to it.levelCode }.toList()
        assertThat(defaultLevelCodes).isEqualTo(listOf("WRI" to "ENH"))
      }
    }

    @Test
    fun `requires correct role to partially update a prison incentive level`() {
      saveLevel("BAI", "BAS")

      webTestClient.patch()
        .uri("/incentive/prison-levels/BAI/level/BAS")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "remandTransferLimitInPence": 5600, "remandSpendLimitInPence": 56000
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.remandSpendLimitInPence).isEqualTo(55000)
      }
    }

    @Test
    fun `fails to partially update a prison incentive level when incentive level does not exist`() {
      webTestClient.patch()
        .uri("/incentive/prison-levels/MDI/level/std")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to partially update a prison incentive level with invalid fields`() {
      saveLevel("WRI", "ENH")

      webTestClient.patch()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "remandTransferLimitInPence": -5600, "visitOrders": -1
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to partially update a prison incentive level when making inactive level the default`() {
      saveLevel("MDI", "ENT") // Entry is inactive

      webTestClient.patch()
        .uri("/incentive/prison-levels/MDI/level/ENT")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "defaultOnAdmission": true
          }
          """
        )
        .exchange()
        .expectErrorResponse(
          HttpStatus.BAD_REQUEST,
          "A level cannot be made inactive and still be the default for admission",
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "ENT")
        assertThat(prisonIncentiveLevel?.active).isFalse
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to partially update a prison incentive level when deactivating default level`() {
      saveLevel("MDI", "STD") // Standard is the default for admission

      webTestClient.patch()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "active": false
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "A level cannot be made inactive and still be the default for admission")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to partially update a prison incentive level when no default for admission would remain`() {
      saveLevel("BAI", "STD") // Standard is the default for admission

      webTestClient.patch()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "defaultOnAdmission": false
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `fails to partially update a non-default prison incentive level when there is no other default level for admission`() {
      // data integrity problem: there is no default level for admission
      saveLevel("BAI", "BAS")
      saveLevel("BAI", "ENH")

      webTestClient.patch()
        .uri("/incentive/prison-levels/BAI/level/BAS")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """
          {
            "defaultOnAdmission": false, "remandTransferLimitInPence": 5600
          }
          """
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }

    @Test
    fun `deactivates a prison incentive level when one exists`() {
      saveLevel("WRI", "EN2")
      `deactivates a prison incentive level when per-prison information does not exist`()
    }

    @Test
    fun `deactivates a prison incentive level when per-prison information does not exist`() {
      saveLevel("WRI", "STD") // Standard is the default for admission, needed to allow deactivation of EN2

      webTestClient.delete()
        .uri("/incentive/prison-levels/WRI/level/EN2")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "EN2", "levelDescription": "Enhanced 2", "prisonId": "WRI", "active": false, "defaultOnAdmission": false,
            "remandTransferLimitInPence": 5500, "remandSpendLimitInPence": 55000, "convictedTransferLimitInPence": 1800, "convictedSpendLimitInPence": 18000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "EN2")
        assertThat(prisonIncentiveLevel?.active).isFalse
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(5500)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }
    }

    @Test
    fun `requires correct role to deactivate a prison incentive level`() {
      saveLevel("WRI", "ENH")

      webTestClient.delete()
        .uri("/incentive/prison-levels/WRI/level/ENH")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.active).isTrue
      }
    }

    @Test
    fun `fails to deactivate a prison incentive level when incentive level does not exist`() {
      webTestClient.delete()
        .uri("/incentive/prison-levels/WRI/level/bas")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `bas`")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }
    }

    @Test
    fun `fails to deactivate a prison incentive level which is default for admission`() {
      saveLevel("MDI", "STD") // Standard is the default for admission

      webTestClient.delete()
        .uri("/incentive/prison-levels/MDI/level/STD")
        .withLocalAuthorisation()
        .header("Content-Type", "application/json")
        .exchange()
        .expectErrorResponse(
          HttpStatus.BAD_REQUEST,
          "A level cannot be made inactive and still be the default for admission",
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }
    }
  }
}
