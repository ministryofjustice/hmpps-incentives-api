package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonIncentiveLevel
import java.time.Clock

class PrisonIncentiveLevelResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
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

              remandTransferLimitInPence = 60_50,
              remandSpendLimitInPence = 605_00,
              convictedTransferLimitInPence = 19_80,
              convictedSpendLimitInPence = 198_00,

              visitOrders = 2,
              privilegedVisitOrders = 1,

              new = true,
            ),
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
              "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
              "visitOrders": 2, "privilegedVisitOrders": 1
            },
            {
              "levelCode": "STD", "levelDescription": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
              "visitOrders": 2, "privilegedVisitOrders": 1
            },
            {
              "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
          ]
          """,
          true,
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
            "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true,
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
      headers(
        setAuthorisation(
          user = "USER_ADM",
          roles = listOf("ROLE_MAINTAIN_PRISON_IEP_LEVELS"),
          scopes = listOf("read", "write"),
        ),
      )
    }

    private fun saveLevel(prisonId: String, levelCode: String) = runBlocking {
      prisonIncentiveLevelRepository.save(
        PrisonIncentiveLevel(
          levelCode = levelCode,
          prisonId = prisonId,
          active = levelCode != "ENT",
          defaultOnAdmission = levelCode == "STD",

          remandTransferLimitInPence = when (levelCode) {
            "BAS" -> 27_50
            "STD" -> 60_50
            else -> 66_00
          },
          remandSpendLimitInPence = when (levelCode) {
            "BAS" -> 275_00
            "STD" -> 605_00
            else -> 660_00
          },
          convictedTransferLimitInPence = when (levelCode) {
            "BAS" -> 5_50
            "STD" -> 19_80
            else -> 33_00
          },
          convictedSpendLimitInPence = when (levelCode) {
            "BAS" -> 55_00
            "STD" -> 198_00
            else -> 330_00
          },

          visitOrders = 2,
          privilegedVisitOrders = 1,

          new = true,
          whenUpdated = now.minusDays(3),
        ),
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "STD", "levelDescription": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
          true,
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(61_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("PRISON_INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it).containsAllEntriesOf(
          mapOf(
            "prisonId" to "MDI",
            "levelCode" to "STD",
            "remandTransferLimitInPence" to 61_50,
            "visitOrders" to 3,
          ),
        )
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
            "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true,
        )

      runBlocking {
        var prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse

        prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue

        val defaultLevelCodes =
          prisonIncentiveLevelRepository.findAll().filter { it.defaultOnAdmission && it.prisonId == "WRI" }
            .map { it.prisonId to it.levelCode }.toList()
        assertThat(defaultLevelCodes).isEqualTo(listOf("WRI" to "ENH"))
      }

      assertAuditMessageSentWithMap("PRISON_INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it).containsAllEntriesOf(
          mapOf(
            "prisonId" to "WRI",
            "levelCode" to "ENH",
            "active" to true,
            "defaultOnAdmission" to true,
          ),
        )
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Incentive level codes must match in URL and payload")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Prison ids must match in URL and payload")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Parameter conversion failure")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": -6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(
          HttpStatus.BAD_REQUEST,
          "A level cannot be made inactive and still be the default for admission",
        )

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
            "visitOrders": 3, "privilegedVisitOrders": 2
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 2850, "remandSpendLimitInPence": 28500,
            "visitOrders": 3
          }
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "BAS", "levelDescription": "Basic", "prisonId": "BAI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 2850, "remandSpendLimitInPence": 28500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
            "visitOrders": 3, "privilegedVisitOrders": 1
          }
          """,
          true,
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(28_50)
        assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(5_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
        assertThat(prisonIncentiveLevel?.privilegedVisitOrders).isEqualTo(1)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("PRISON_INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it).containsAllEntriesOf(
          mapOf(
            "prisonId" to "BAI",
            "levelCode" to "BAS",
            "remandTransferLimitInPence" to 28_50,
            "convictedTransferLimitInPence" to 5_50,
            "visitOrders" to 3,
          ),
        )
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
          """,
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "levelCode": "ENH", "levelDescription": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
            "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true,
        )

      runBlocking {
        var prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "STD")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse

        prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue

        val defaultLevelCodes =
          prisonIncentiveLevelRepository.findAll().filter { it.defaultOnAdmission && it.prisonId == "WRI" }
            .map { it.prisonId to it.levelCode }.toList()
        assertThat(defaultLevelCodes).isEqualTo(listOf("WRI" to "ENH"))
      }

      assertAuditMessageSentWithMap("PRISON_INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it).containsAllEntriesOf(
          mapOf(
            "prisonId" to "WRI",
            "levelCode" to "ENH",
            "remandTransferLimitInPence" to 66_00,
            "visitOrders" to 2,
          ),
        )
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
            "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.FORBIDDEN, "Forbidden")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
        assertThat(prisonIncentiveLevel?.remandSpendLimitInPence).isEqualTo(275_00)
      }

      assertNoAuditMessageSent()
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
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.NOT_FOUND, "No incentive level found for code `std`")

      runBlocking {
        assertThat(prisonIncentiveLevelRepository.count()).isZero
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": -6150, "visitOrders": -1
          }
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
          """,
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

      assertNoAuditMessageSent()
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
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "A level cannot be made inactive and still be the default for admission")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
          """,
        )
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "There must be an active default level for admission in a prison")

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
        assertThat(prisonIncentiveLevel?.active).isTrue
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
        assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
      }

      assertNoAuditMessageSent()
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
            "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
            "visitOrders": 2, "privilegedVisitOrders": 1
          }
          """,
          true,
        )

      runBlocking {
        val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "EN2")
        assertThat(prisonIncentiveLevel?.active).isFalse
        assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
        assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
        assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(33_00)
        assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
        assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
      }

      assertAuditMessageSentWithMap("PRISON_INCENTIVE_LEVEL_UPDATED").let {
        assertThat(it).containsAllEntriesOf(
          mapOf(
            "prisonId" to "WRI",
            "levelCode" to "EN2",
            "active" to false,
            "defaultOnAdmission" to false,
          ),
        )
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

      assertNoAuditMessageSent()
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

      assertNoAuditMessageSent()
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

      assertNoAuditMessageSent()
    }
  }
}
