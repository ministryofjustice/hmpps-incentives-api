package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorCode
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import java.time.Clock

@DisplayName("Incentive level resource")
class IncentiveLevelResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @DisplayName("read-only endpoints")
  @Nested
  inner class ReadOnlyEndpoints {
    @ParameterizedTest
    @ValueSource(
      strings = [
        "/incentive/levels",
        "/incentive/levels/STD",
        "/incentive/levels/MISSING",
      ],
    )
    fun `requires a valid token to retrieve data`(url: String) {
      webTestClient.get()
        .uri(url)
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @DisplayName("list levels")
    @Nested
    inner class ListLevels {
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
              {"code": "BAS", "name": "Basic", "active": true, "required": true},
              {"code": "STD", "name": "Standard", "active": true, "required": true},
              {"code": "ENH", "name": "Enhanced", "active": true, "required": true},
              {"code": "EN2", "name": "Enhanced 2", "active": true, "required": false},
              {"code": "EN3", "name": "Enhanced 3", "active": true, "required": false}
            ]
            """,
            JsonCompareMode.STRICT,
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
              {"code": "BAS", "name": "Basic", "active": true, "required": true},
              {"code": "STD", "name": "Standard", "active": true, "required": true},
              {"code": "ENH", "name": "Enhanced", "active": true, "required": true},
              {"code": "EN2", "name": "Enhanced 2", "active": true, "required": false},
              {"code": "EN3", "name": "Enhanced 3", "active": true, "required": false},
              {"code": "ENT", "name": "Entry", "active": false, "required": false}
            ]
            """,
            JsonCompareMode.STRICT,
          )
      }
    }

    @DisplayName("view level")
    @Nested
    inner class ViewLevel {
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
            {"code": "EN2", "name": "Enhanced 2", "active": true, "required": false}
            """,
            JsonCompareMode.STRICT,
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
            {"code": "ENT", "name": "Entry", "active": false, "required": false}
            """,
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `fails to return level details of missing level`() {
        webTestClient.get()
          .uri("/incentive/levels/bas")
          .headers(setAuthorisation())
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `bas`",
          )
      }
    }
  }

  @DisplayName("modifying endpoints")
  @Nested
  inner class ModifyingEndpoints {
    private fun <T : WebTestClient.RequestHeadersSpec<*>> T.withCentralAuthorisation(): T = apply {
      headers(
        setAuthorisation(
          user = "USER_ADM",
          roles = listOf("ROLE_MAINTAIN_INCENTIVE_LEVELS"),
          scopes = listOf("read", "write"),
        ),
      )
    }

    @DisplayName("create level")
    @Nested
    inner class CreateLevel {
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
            {"code": "EN4", "name": "Enhanced 4", "active": false}
            """,
          )
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """
            {"code": "EN4", "name": "Enhanced 4", "active": false, "required": false}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          assertThat(incentiveLevelRepository.count()).isEqualTo(7)

          val incentiveLevel = incentiveLevelRepository.findById("EN4")
          assertThat(incentiveLevel?.code).isEqualTo("EN4")
          assertThat(incentiveLevel?.name).isEqualTo("Enhanced 4")
          assertThat(incentiveLevel?.active).isFalse
          assertThat(incentiveLevel?.sequence).isGreaterThan(maxSequence)
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)

          assertThat(prisonIncentiveLevelRepository.count()).isEqualTo(0)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: EN4")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("EN4")
        }
        assertAuditMessageSentWithMap("INCENTIVE_LEVEL_ADDED").let {
          assertThat(it["code"]).isEqualTo("EN4")
        }
      }

      @Test
      fun `creates a required level and activates it in all prisons with defaults`() {
        runBlocking {
          // 2 prisons with active levels and 1 without
          makePrisonIncentiveLevel("MDI", "STD")
          makePrisonIncentiveLevel("WRI", "STD")
          makePrisonIncentiveLevel("BAI", "ENT")
        }

        webTestClient.post()
          .uri("/incentive/levels")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"code": "EN4", "name": "Enhanced 4", "required": true}
            """,
          )
          .exchange()
          .expectStatus().isCreated

        runBlocking {
          listOf("MDI", "WRI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "EN4")
            assertThat(prisonIncentiveLevel?.active).isTrue
            assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
            assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(33_00)
          }
          listOf("BAI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "EN4")
            assertThat(prisonIncentiveLevel).isNull()
          }
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedEventCount = 3
        assertThat(domainEvents).hasSize(expectedEventCount)
        assertThat(auditMessages).hasSize(expectedEventCount)

        val expectedIncentiveLevelCreateCount = 1
        assertThat(domainEvents.count { it.eventType == "incentives.level.changed" }).isEqualTo(expectedIncentiveLevelCreateCount)
        assertThat(auditMessages.count { it.what == "INCENTIVE_LEVEL_ADDED" }).isEqualTo(expectedIncentiveLevelCreateCount)

        val expectedPrisonIncentiveLevelIdsAffected = setOf("MDI", "WRI")
        assertThat(
          domainEvents.filter { it.eventType == "incentives.prison-level.changed" }
            .map { it.additionalInformation?.prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
        assertThat(
          auditMessages.filter { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }
            .map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java).prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
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
            {"code": "EN4", "name": "Enhanced 4", "active": false}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          assertThat(incentiveLevelRepository.count()).isEqualTo(6)
        }

        assertNoDomainEventSent()
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
            name: Silver
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
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
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
            {"code": "STD", "name": "Silver", "active": false}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Incentive level with code STD already exists",
            errorCode = ErrorCode.IncentiveLevelCodeNotUnique,
          )

        runBlocking {
          assertThat(incentiveLevelRepository.count()).isEqualTo(6)

          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.code).isEqualTo("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
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
          {"name": "Enhanced 4", "active": false}
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid request format",
          )

        runBlocking {
          assertThat(incentiveLevelRepository.count()).isEqualTo(6)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @ParameterizedTest
      @ValueSource(
        strings = [
          // language=json
          """
          {"code": "", "name": "Enhanced 4", "active": false}
          """,
          // language=json
          """
          {"code": "Enhanced", "name": "Enhanced Plus", "active": false}
          """,
          // language=json
          """
          {"code": "EN4", "name": "", "active": false}
          """,
          // language=json
          """
          {"code": "EN4", "name": "Enhanced 4", "active": false, "required": true}
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid parameters",
          )

        runBlocking {
          assertThat(incentiveLevelRepository.count()).isEqualTo(6)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("reorder levels")
    @Nested
    inner class ReorderLevels {
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
              {"code": "EN3", "name": "Enhanced 3", "active": true, "required": false},
              {"code": "EN2", "name": "Enhanced 2", "active": true, "required": false},
              {"code": "ENT", "name": "Entry", "active": false, "required": false},
              {"code": "ENH", "name": "Enhanced", "active": true, "required": true},
              {"code": "STD", "name": "Standard", "active": true, "required": true},
              {"code": "BAS", "name": "Basic", "active": true, "required": true}
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
          assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("EN3", "EN2", "ENT", "ENH", "STD", "BAS"))
          assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 6))
          assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
            assertThat(it).isEqualTo(now)
          }
        }

        assertDomainEventSent("incentives.levels.reordered").let {
          assertThat(it.description).isEqualTo("Incentive levels have been re-ordered")
          assertThat(it.additionalInformation).isNull()
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
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val incentiveLevelCodes = incentiveLevelRepository.findAllByOrderBySequence().map { it.code }.toList()
          assertThat(incentiveLevelCodes).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `EN4`",
          )

        runBlocking {
          val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
          assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
          assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
          assertThat(incentiveLevels.map { it.whenUpdated }).allSatisfy {
            assertThat(it).isNotEqualTo(now)
          }
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "must have size of at least 2",
          )

        runBlocking {
          val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
          assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
          assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "All incentive levels required when setting order. Missing: `ENT`",
            errorCode = ErrorCode.IncentiveLevelReorderNeedsFullSet,
          )

        runBlocking {
          val incentiveLevels = incentiveLevelRepository.findAllByOrderBySequence().toList()
          assertThat(incentiveLevels.map { it.code }).isEqualTo(listOf("BAS", "STD", "ENH", "EN2", "EN3", "ENT"))
          assertThat(incentiveLevels.map { it.sequence }).isEqualTo(listOf(1, 2, 3, 4, 5, 99))
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("update level")
    @Nested
    inner class UpdateLevel {
      @Test
      fun `updates a level`() {
        webTestClient.put()
          .uri("/incentive/levels/STD")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"code": "STD", "name": "Silver", "active": true, "required": true}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {"code": "STD", "name": "Silver", "active": true, "required": true}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Silver")
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: STD")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("STD")
        }
        assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
          assertThat(it["name"]).isEqualTo("Silver")
        }
      }

      @Test
      fun `updates a required level and activates it in all prisons with defaults`() {
        runBlocking {
          // 2 prisons with active levels and 1 without
          makePrisonIncentiveLevel("MDI", "STD")
          makePrisonIncentiveLevel("WRI", "STD")
          makePrisonIncentiveLevel("BAI", "ENT")
        }

        webTestClient.put()
          .uri("/incentive/levels/ENT")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"code": "ENT", "name": "Entry", "active": true, "required": true}
            """,
          )
          .exchange()
          .expectStatus().isOk

        runBlocking {
          listOf("MDI", "WRI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "ENT")
            assertThat(prisonIncentiveLevel?.active).isTrue
            // NB: Entry is not in current policy so is assumed to be equivalent to Enhanced
            assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
            assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(33_00)
          }
          listOf("BAI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "ENT")
            assertThat(prisonIncentiveLevel?.active).isFalse
          }
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedEventCount = 3
        assertThat(domainEvents).hasSize(expectedEventCount)
        assertThat(auditMessages).hasSize(expectedEventCount)

        val expectedIncentiveLevelCreateCount = 1
        assertThat(domainEvents.count { it.eventType == "incentives.level.changed" }).isEqualTo(expectedIncentiveLevelCreateCount)
        assertThat(auditMessages.count { it.what == "INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedIncentiveLevelCreateCount)

        val expectedPrisonIncentiveLevelIdsAffected = setOf("MDI", "WRI")
        assertThat(
          domainEvents.filter { it.eventType == "incentives.prison-level.changed" }
            .map { it.additionalInformation?.prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
        assertThat(
          auditMessages.filter { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }
            .map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java).prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
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
            {"code": "STD", "name": "Silver", "active": true}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isNotEqualTo("Silver")
        }

        assertNoDomainEventSent()
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
            {"code": "std", "name": "Silver", "active": true}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `std`",
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("std")
          assertThat(incentiveLevel).isNull()
          incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
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
            {"code": "STD", "name": "Silver", "active": false}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Incentive level codes must match in URL and payload",
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
          incentiveLevel = incentiveLevelRepository.findById("ENH")
          assertThat(incentiveLevel?.name).isEqualTo("Enhanced")
          assertThat(incentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
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
          {"name": "Silver", "active": false}
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid request format",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @ParameterizedTest
      @ValueSource(
        strings = [
          // language=json
          """
          {"code": "STD", "name": "", "active": false}
          """,
          // language=json
          """
          {"code": "STD", "name": "Standard", "active": false, "required": true}
          """,
        ],
      )
      fun `fails to update a level with invalid fields`(body: String) {
        webTestClient.put()
          .uri("/incentive/levels/STD")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(body)
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid parameters",
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
          incentiveLevel = incentiveLevelRepository.findById("")
          assertThat(incentiveLevel).isNull()
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("partially update level")
    @Nested
    inner class PartiallyUpdateLevel {
      @Test
      fun `partially updates a level`() {
        webTestClient.patch()
          .uri("/incentive/levels/STD")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"name": "Silver"}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {"code": "STD", "name": "Silver", "active": true, "required": true}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Silver")
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: STD")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("STD")
        }
        assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
          assertThat(it["name"]).isEqualTo("Silver")
        }
      }

      @Test
      fun `partially updates a required level and activates it in all prisons with defaults`() {
        runBlocking {
          // 2 prisons with active levels and 1 without
          makePrisonIncentiveLevel("MDI", "STD")
          makePrisonIncentiveLevel("WRI", "STD")
          makePrisonIncentiveLevel("BAI", "ENT")
        }

        webTestClient.patch()
          .uri("/incentive/levels/ENT")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"active": true, "required": true}
            """,
          )
          .exchange()
          .expectStatus().isOk

        runBlocking {
          listOf("MDI", "WRI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "ENT")
            assertThat(prisonIncentiveLevel?.active).isTrue
            // NB: Entry is not in current policy so is assumed to be equivalent to Enhanced
            assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
            assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(33_00)
          }
          listOf("BAI").forEach { prisonId ->
            val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode(prisonId, "ENT")
            assertThat(prisonIncentiveLevel?.active).isFalse
          }
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedEventCount = 3
        assertThat(domainEvents).hasSize(expectedEventCount)
        assertThat(auditMessages).hasSize(expectedEventCount)

        val expectedIncentiveLevelCreateCount = 1
        assertThat(domainEvents.count { it.eventType == "incentives.level.changed" }).isEqualTo(expectedIncentiveLevelCreateCount)
        assertThat(auditMessages.count { it.what == "INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedIncentiveLevelCreateCount)

        val expectedPrisonIncentiveLevelIdsAffected = setOf("MDI", "WRI")
        assertThat(
          domainEvents.filter { it.eventType == "incentives.prison-level.changed" }
            .map { it.additionalInformation?.prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
        assertThat(
          auditMessages.filter { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }
            .map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java).prisonId }
            .toSet(),
        ).isEqualTo(expectedPrisonIncentiveLevelIdsAffected)
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
            {"name": "Silver"}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isNotEqualTo("Silver")
        }

        assertNoDomainEventSent()
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
            {"name": "Silver"}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `std`",
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("std")
          assertThat(incentiveLevel).isNull()
          incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
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
            {"code": "STD", "name": "Gold", "active": false, "required": false}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {"code": "ENH", "name": "Gold", "active": false, "required": false}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("ENH")
          assertThat(incentiveLevel?.name).isEqualTo("Gold")
          assertThat(incentiveLevel?.active).isFalse
          assertThat(incentiveLevel?.required).isFalse
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
          incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
          assertThat(incentiveLevel?.required).isTrue
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: ENH")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("ENH")
        }
        assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
          assertThat(it["code"]).isEqualTo("ENH")
          assertThat(it["name"]).isEqualTo("Gold")
        }
      }

      @ParameterizedTest
      @ValueSource(
        strings = [
          // language=json
          """
          {"name": "", "active": false}
          """,
          // language=json
          """
          {"active": false, "required": true}
          """,
        ],
      )
      fun `fails to partially update a level with invalid fields`(body: String) {
        webTestClient.patch()
          .uri("/incentive/levels/STD")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(body)
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid parameters",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.name).isEqualTo("Standard")
          assertThat(incentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a level when inactive level is made required`() {
        webTestClient.patch()
          .uri("/incentive/levels/ENT")
          .withCentralAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {"required": true}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must be active if it is required",
            errorCode = ErrorCode.IncentiveLevelActiveIfRequired,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("ENT")
          assertThat(incentiveLevel?.active).isFalse
          assertThat(incentiveLevel?.required).isFalse
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("deactivate level")
    @Nested
    inner class DeactivateLevel {
      @Test
      fun `deactivates a level`() {
        webTestClient.delete()
          .uri("/incentive/levels/EN2")
          .withCentralAuthorisation()
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {"code": "EN2", "name": "Enhanced 2", "active": false, "required": false}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("EN2")
          assertThat(incentiveLevel?.active).isFalse
          assertThat(incentiveLevel?.required).isFalse
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: EN2")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("EN2")
        }
        assertAuditMessageSentWithMap("INCENTIVE_LEVEL_UPDATED").let {
          assertThat(it["code"]).isEqualTo("EN2")
          assertThat(it["active"]).isEqualTo(false)
          assertThat(it["required"]).isEqualTo(false)
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
            {"code": "ENT", "name": "Entry", "active": false, "required": false}
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("ENT")
          assertThat(incentiveLevel?.active).isFalse
          assertThat(incentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.level.changed").let {
          assertThat(it.description).isEqualTo("An incentive level has been changed: ENT")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("ENT")
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
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a missing level`() {
        webTestClient.delete()
          .uri("/incentive/levels/std")
          .withCentralAuthorisation()
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `std`",
          )

        runBlocking {
          var incentiveLevel = incentiveLevelRepository.findById("std")
          assertThat(incentiveLevel).isNull()
          incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.active).isTrue
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a required level`() {
        webTestClient.delete()
          .uri("/incentive/levels/STD")
          .withCentralAuthorisation()
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must be active if it is required",
            errorCode = ErrorCode.IncentiveLevelActiveIfRequired,
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("STD")
          assertThat(incentiveLevel?.active).isTrue
          assertThat(incentiveLevel?.required).isTrue
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a level when it is active in some prison`() {
        runBlocking {
          // 2 prisons with active EN2 level and 1 active prison without
          listOf("BAI", "MDI", "WRI").forEach { prisonId ->
            listOf("BAS", "STD", "ENH", "EN2").forEach { levelCode ->
              makePrisonIncentiveLevel(prisonId, levelCode)
            }
          }
          prisonIncentiveLevelRepository.save(
            prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "EN2")!!.copy(active = false),
          )
        }

        webTestClient.delete()
          .uri("/incentive/levels/EN2")
          .withCentralAuthorisation()
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must remain active if it is active in some prison",
            errorCode = ErrorCode.IncentiveLevelActiveIfActiveInPrison,
            moreInfo = "BAI,MDI",
          )

        runBlocking {
          val incentiveLevel = incentiveLevelRepository.findById("EN2")
          assertThat(incentiveLevel?.active).isTrue
          assertThat(incentiveLevel?.required).isFalse
          assertThat(incentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }
  }
}
