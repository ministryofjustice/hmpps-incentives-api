package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
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
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.Clock
import java.time.LocalDateTime
import kotlin.text.Regex

@DisplayName("Prison incentive level resource")
class PrisonIncentiveLevelResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonIncentiveLevelRepository.deleteAll()
    incentiveReviewRepository.deleteAll()

    offenderSearchMockServer.stubFindOffenders("BAI")
    offenderSearchMockServer.stubFindOffenders("MDI")
    offenderSearchMockServer.stubFindOffenders("WRI")
  }

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    incentiveReviewRepository.deleteAll()
    super.tearDown()
  }

  @DisplayName("read-only endpoints")
  @Nested
  inner class ReadOnlyEndpoints {
    @BeforeEach
    fun setUp() = runBlocking {
      listOf("BAS", "STD", "ENH", "ENT").forEach { levelCode ->
        listOf("BAI", "MDI", "WRI").forEach { prisonId ->
          makePrisonIncentiveLevel(prisonId, levelCode)
        }
      }
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
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

    @DisplayName("list levels")
    @Nested
    inner class ListLevels {
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
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `lists all prison levels without any role`() {
        webTestClient.get()
          .uri("/incentive/prison-levels/MDI?with-inactive=true")
          .headers(setAuthorisation())
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "MDI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENT", "levelName": "Entry", "prisonId": "MDI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
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
              "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `returns prison level details of inactive level`() {
        webTestClient.get()
          .uri("/incentive/prison-levels/MDI/level/ENT")
          .headers(setAuthorisation())
          .exchange()
          .expectBody().json(
            // language=json
            """
            {
              "levelCode": "ENT", "levelName": "Entry", "prisonId": "MDI", "active": false, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `fails to return prison level details of missing level`() {
        webTestClient.get()
          .uri("/incentive/prison-levels/BAI/level/EN4")
          .headers(setAuthorisation())
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No prison incentive level found for code `EN4`",
          )
      }
    }
  }

  @DisplayName("modifying endpoints")
  @Nested
  inner class ModifyingEndpoints {
    private fun <T : WebTestClient.RequestHeadersSpec<*>> T.withLocalAuthorisation(): T = apply {
      headers(
        setAuthorisation(
          user = "USER_ADM",
          roles = listOf("ROLE_MAINTAIN_PRISON_IEP_LEVELS"),
          scopes = listOf("read", "write"),
        ),
      )
    }

    @DisplayName("update level")
    @Nested
    inner class UpdateLevel {
      @Test
      fun `updates a prison incentive level when one exists`() {
        makePrisonIncentiveLevel("MDI", "STD")
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
              "levelCode": "STD", "levelName": "Standard", "prisonId": "MDI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 6150, "remandSpendLimitInPence": 61500, "convictedTransferLimitInPence": 2080, "convictedSpendLimitInPence": 20800,
              "visitOrders": 3, "privilegedVisitOrders": 2
            }
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(61_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
          assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.prison-level.changed").let {
          assertThat(it.description).isEqualTo("Incentive level (STD) in prison MDI has been updated")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("STD")
          assertThat(it.additionalInformation?.prisonId).isEqualTo("MDI")
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
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission

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
              "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
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

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 2
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "WRI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison WRI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "WRI" }
        assertThat(auditMessageDetails).allMatch { it.active }
        val auditMessageDefaultLevels = auditMessageDetails.associate {
          it.levelCode to it.defaultOnAdmission
        }
        assertThat(auditMessageDefaultLevels).isEqualTo(
          mapOf(
            "ENH" to true,
            "STD" to false,
          ),
        )
      }

      @Test
      fun `requires correct role to update a prison incentive level`() {
        makePrisonIncentiveLevel("MDI", "STD")

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
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `std`",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Incentive level codes must match in URL and payload",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Prison ids must match in URL and payload",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid request format",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid parameters",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
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

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to update a prison incentive level when no default for admission would remain`() {
        makePrisonIncentiveLevel("BAI", "STD")

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "There must be an active default level for admission in a prison",
            errorCode = ErrorCode.PrisonIncentiveLevelDefaultRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to update a non-default prison incentive level when there is no other default level for admission`() {
        // data integrity problem: there is no default level for admission
        makePrisonIncentiveLevel("BAI", "BAS")
        makePrisonIncentiveLevel("BAI", "ENH")

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "There must be an active default level for admission in a prison",
            errorCode = ErrorCode.PrisonIncentiveLevelDefaultRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to update a prison incentive level if it would become active despite being globally inactive`() {
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission, needed to manipulate other levels

        webTestClient.put()
          .uri("/incentive/prison-levels/WRI/level/ENT")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {
              "levelCode": "ENT", "prisonId": "WRI", "active": true,
              "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
              "visitOrders": 1, "privilegedVisitOrders": 1
            }
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made active and when it is globally inactive",
            errorCode = ErrorCode.PrisonIncentiveLevelNotGloballyActive,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "ENT")
          assertThat(prisonIncentiveLevel).isNull()
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to update a prison incentive level if it would become inactive despite being globally required`() {
        webTestClient.put()
          .uri("/incentive/prison-levels/WRI/level/BAS")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {
              "levelCode": "BAS", "prisonId": "WRI", "active": false,
              "remandTransferLimitInPence": 2850, "remandSpendLimitInPence": 28500, "convictedTransferLimitInPence": 650, "convictedSpendLimitInPence": 6500,
              "visitOrders": 1, "privilegedVisitOrders": 1
            }
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made inactive and when it is globally required",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfRequired,
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to update a prison incentive level if it would become inactive despite having prisoners on it`() {
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission, needed to manipulate other levels
        makePrisonIncentiveLevel("WRI", "EN2")
        makeIncentiveReviews("WRI") // EN2 has A1234AB/1234135 & A1234AD/1234137 on it

        webTestClient.put()
          .uri("/incentive/prison-levels/WRI/level/EN2")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {
              "levelCode": "EN2", "prisonId": "WRI", "active": false,
              "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must remain active if there are prisoners on it currently",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "EN2")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("partially update level")
    @Nested
    inner class PartiallyUpdateLevel {
      @Test
      fun `partially updates a prison incentive level when one exists`() {
        makePrisonIncentiveLevel("BAI", "BAS")
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
              "levelCode": "BAS", "levelName": "Basic", "prisonId": "BAI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 2850, "remandSpendLimitInPence": 28500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
              "visitOrders": 3, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(28_50)
          assertThat(prisonIncentiveLevel?.convictedTransferLimitInPence).isEqualTo(5_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(3)
          assertThat(prisonIncentiveLevel?.privilegedVisitOrders).isEqualTo(1)
          assertThat(prisonIncentiveLevel?.whenUpdated).isEqualTo(now)
        }

        assertDomainEventSent("incentives.prison-level.changed").let {
          assertThat(it.description).isEqualTo("Incentive level (BAS) in prison BAI has been updated")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("BAS")
          assertThat(it.additionalInformation?.prisonId).isEqualTo("BAI")
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
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission

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
              "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "WRI", "active": true, "defaultOnAdmission": true,
              "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
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

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 2
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "WRI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison WRI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "WRI" }
        assertThat(auditMessageDetails).allMatch { it.active }
        val auditMessageDefaultLevels = auditMessageDetails.associate {
          it.levelCode to it.defaultOnAdmission
        }
        assertThat(auditMessageDefaultLevels).isEqualTo(
          mapOf(
            "ENH" to true,
            "STD" to false,
          ),
        )
      }

      @Test
      fun `partially updates prison incentive levels if all being activated one-by-one`() {
        makePrisonIncentiveLevel("MDI", "BAS")
        makePrisonIncentiveLevel("MDI", "STD") // Standard was the default for admission before deactivation
        makePrisonIncentiveLevel("MDI", "ENH")
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("MDI").map {
              it.copy(active = false)
            }.toList(),
          ).collect()
        }

        // NB: the previously-default level must be activated first to conform to business rules
        for (levelCode in listOf("STD", "BAS", "ENH")) {
          webTestClient.patch()
            .uri("/incentive/prison-levels/MDI/level/$levelCode")
            .withLocalAuthorisation()
            .header("Content-Type", "application/json")
            .bodyValue(
              // language=json
              """
              {
                "active": true
              }
              """,
            )
            .exchange()
            .expectStatus().isOk
        }

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAll().toList()
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          val defaultLevelCodes = prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }
          assertThat(defaultLevelCodes).isEqualTo(listOf("STD"))
        }
      }

      @Test
      fun `partially updates prison incentive levels if all being activated one-by-one when the previous default was not Standard`() {
        makePrisonIncentiveLevel("MDI", "BAS")
        makePrisonIncentiveLevel("MDI", "STD")
        makePrisonIncentiveLevel("MDI", "ENH") // Enhanced was the default for admission before deactivation
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("MDI").map {
              it.copy(active = false, defaultOnAdmission = it.levelCode == "ENH")
            }.toList(),
          ).collect()
        }

        // NB: the previously-default level must be activated first to conform to business rules
        for (levelCode in listOf("ENH", "BAS", "STD")) {
          webTestClient.patch()
            .uri("/incentive/prison-levels/MDI/level/$levelCode")
            .withLocalAuthorisation()
            .header("Content-Type", "application/json")
            .bodyValue(
              // language=json
              """
              {
                "active": true
              }
              """,
            )
            .exchange()
            .expectStatus().isOk
        }

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAll().toList()
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          val defaultLevelCodes = prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }
          assertThat(defaultLevelCodes).isEqualTo(listOf("ENH"))
        }
      }

      @Test
      fun `requires correct role to partially update a prison incentive level`() {
        makePrisonIncentiveLevel("BAI", "BAS")

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
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
          assertThat(prisonIncentiveLevel?.remandSpendLimitInPence).isEqualTo(275_00)
        }

        assertNoDomainEventSent()
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
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `std`",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level with invalid fields`() {
        makePrisonIncentiveLevel("WRI", "ENH")

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid parameters",
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(66_00)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level when making inactive level the default`() {
        makePrisonIncentiveLevel("MDI", "ENT") // Entry is inactive

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
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfDefault,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "ENT")
          assertThat(prisonIncentiveLevel?.active).isFalse
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level when deactivating default level`() {
        runBlocking {
          // Pretend that Standard is not required
          val standard = incentiveLevelRepository.findById("STD")!!.copy(required = false)
          incentiveLevelRepository.save(standard)
        }
        makePrisonIncentiveLevel("MDI", "STD") // Standard is the default for admission

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made inactive and still be the default for admission",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfDefault,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level when no default for admission would remain`() {
        makePrisonIncentiveLevel("BAI", "STD") // Standard is the default for admission

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "There must be an active default level for admission in a prison",
            errorCode = ErrorCode.PrisonIncentiveLevelDefaultRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "STD")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(60_50)
          assertThat(prisonIncentiveLevel?.visitOrders).isEqualTo(2)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a non-default prison incentive level when there is no other default level for admission`() {
        // data integrity problem: there is no default level for admission
        makePrisonIncentiveLevel("BAI", "BAS")
        makePrisonIncentiveLevel("BAI", "ENH")

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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "There must be an active default level for admission in a prison",
            errorCode = ErrorCode.PrisonIncentiveLevelDefaultRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isFalse
          assertThat(prisonIncentiveLevel?.remandTransferLimitInPence).isEqualTo(27_50)
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level if it would become active despite being globally inactive`() {
        makePrisonIncentiveLevel("BAI", "STD") // Standard is the default for admission, needed to manipulate other levels
        makePrisonIncentiveLevel("BAI", "ENT") // Entry is inactive globally and in BAI

        webTestClient.patch()
          .uri("/incentive/prison-levels/BAI/level/ENT")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {
              "active": true
            }
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made active and when it is globally inactive",
            errorCode = ErrorCode.PrisonIncentiveLevelNotGloballyActive,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "ENT")
          assertThat(prisonIncentiveLevel?.active).isFalse
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level if it would become inactive despite being globally required`() {
        makePrisonIncentiveLevel("BAI", "BAS") // Basic is required in all prisons

        webTestClient.patch()
          .uri("/incentive/prison-levels/BAI/level/BAS")
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made inactive and when it is globally required",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("BAI", "BAS")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to partially update a prison incentive level if it would become inactive despite having prisoners on it`() {
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission, needed to manipulate other levels
        makePrisonIncentiveLevel("WRI", "EN2")
        makeIncentiveReviews("WRI") // EN2 has A1234AB/1234135 & A1234AD/1234137 on it

        webTestClient.patch()
          .uri("/incentive/prison-levels/WRI/level/EN2")
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
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must remain active if there are prisoners on it currently",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "EN2")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("reset levels")
    @Nested
    inner class ResetLevels {
      @Test
      fun `activates required set of levels when none exist`() {
        webTestClient.put()
          .uri("/incentive/prison-levels/FEI/reset")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "FEI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("FEI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          assertThat(prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }).isEqualTo(listOf("STD"))
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 3
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "FEI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison FEI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("BAS", "STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "FEI" }
        assertThat(auditMessageDetails).allMatch { it.active }
        val auditMessageDefaultLevels = auditMessageDetails.associate {
          it.levelCode to it.defaultOnAdmission
        }
        assertThat(auditMessageDefaultLevels).isEqualTo(
          mapOf(
            "BAS" to false,
            "STD" to true,
            "ENH" to false,
          ),
        )
      }

      @Test
      fun `reports no changes when required set of levels are already active`() {
        makePrisonIncentiveLevel("FEI", "BAS")
        makePrisonIncentiveLevel("FEI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("FEI", "ENH")
        makePrisonIncentiveLevel("FEI", "EN2")

        webTestClient.put()
          .uri("/incentive/prison-levels/FEI/reset")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "FEI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "EN2", "levelName": "Enhanced 2", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("FEI").toList()
          assertThat(prisonIncentiveLevels).hasSize(4)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          assertThat(prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }).isEqualTo(listOf("STD"))
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `sets Standard as the default level for admission even when all required levels are active`() {
        makePrisonIncentiveLevel("FEI", "BAS")
        makePrisonIncentiveLevel("FEI", "STD")
        makePrisonIncentiveLevel("FEI", "ENH") // Enhanced is the default for admission
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("FEI").map {
              it.copy(defaultOnAdmission = it.levelCode == "ENH")
            }.toList(),
          ).collect()
        }

        webTestClient.put()
          .uri("/incentive/prison-levels/FEI/reset")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "FEI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("FEI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          assertThat(prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }).isEqualTo(listOf("STD"))
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 2
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "FEI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison FEI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "FEI" }
        assertThat(auditMessageDetails).allMatch { it.active }
        val auditMessageDefaultLevels = auditMessageDetails.associate {
          it.levelCode to it.defaultOnAdmission
        }
        assertThat(auditMessageDefaultLevels).isEqualTo(
          mapOf(
            "STD" to true,
            "ENH" to false,
          ),
        )
      }

      @Test
      fun `preserves associated information for levels that were previously active`() {
        makePrisonIncentiveLevel("FEI", "BAS")
        makePrisonIncentiveLevel("FEI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("FEI", "ENH")
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("FEI").map {
              it.copy(
                active = false,
                remandTransferLimitInPence = 25_50,
                remandSpendLimitInPence = 255_00,
                convictedTransferLimitInPence = 4_30,
                convictedSpendLimitInPence = 43_00,
                visitOrders = 1,
                privilegedVisitOrders = 0,
              )
            }.toList(),
          ).collect()
        }

        webTestClient.put()
          .uri("/incentive/prison-levels/FEI/reset")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {}
            """,
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2550, "remandSpendLimitInPence": 25500, "convictedTransferLimitInPence": 430, "convictedSpendLimitInPence": 4300,
                "visitOrders": 1, "privilegedVisitOrders": 0
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "FEI", "active": true, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 2550, "remandSpendLimitInPence": 25500, "convictedTransferLimitInPence": 430, "convictedSpendLimitInPence": 4300,
                "visitOrders": 1, "privilegedVisitOrders": 0
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "FEI", "active": true, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2550, "remandSpendLimitInPence": 25500, "convictedTransferLimitInPence": 430, "convictedSpendLimitInPence": 4300,
                "visitOrders": 1, "privilegedVisitOrders": 0
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("FEI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
          assertThat(prisonIncentiveLevels.filter { it.defaultOnAdmission }.map { it.levelCode }).isEqualTo(listOf("STD"))
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 3
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "FEI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison FEI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("BAS", "STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "FEI" }
        assertThat(auditMessageDetails).allMatch { it.active }
        val auditMessageDefaultLevels = auditMessageDetails.associate {
          it.levelCode to it.defaultOnAdmission
        }
        assertThat(auditMessageDefaultLevels).isEqualTo(
          mapOf(
            "BAS" to false,
            "STD" to true,
            "ENH" to false,
          ),
        )
      }

      @Test
      fun `requires correct role to reset prison incentive levels`() {
        makePrisonIncentiveLevel("FEI", "BAS")
        makePrisonIncentiveLevel("FEI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("FEI", "ENH")
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("FEI").map {
              it.copy(active = false)
            }.toList(),
          ).collect()
        }

        webTestClient.put()
          .uri("/incentive/prison-levels/FEI/reset")
          .headers(setAuthorisation())
          .header("Content-Type", "application/json")
          .bodyValue(
            // language=json
            """
            {}
            """,
          )
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("FEI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { !it.active }
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("deactivate all levels")
    @Nested
    inner class DeactivateAllLevels {
      @Test
      fun `does nothing when trying to deactivate all prison incentive levels if none exist`() {
        webTestClient.delete()
          .uri("/incentive/prison-levels/BAI")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            []
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `deactivates all prison incentive levels when all are already inactive`() {
        makePrisonIncentiveLevel("BAI", "BAS")
        makePrisonIncentiveLevel("BAI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("BAI", "ENH")
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("BAI").map {
              it.copy(active = false)
            }.toList(),
          ).collect()
        }

        webTestClient.delete()
          .uri("/incentive/prison-levels/BAI")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "BAI", "active": false, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("BAI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { !it.active }
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `deactivates all prison incentive levels when only non-default for admission are active`() {
        makePrisonIncentiveLevel("BAI", "BAS")
        makePrisonIncentiveLevel("BAI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("BAI", "ENH")
        runBlocking {
          prisonIncentiveLevelRepository.saveAll(
            prisonIncentiveLevelRepository.findAllByPrisonId("BAI").map {
              it.copy(active = it.levelCode != "STD")
            }.toList(),
          ).collect()
        }

        webTestClient.delete()
          .uri("/incentive/prison-levels/BAI")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "BAI", "active": false, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("BAI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { !it.active }
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 2
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "BAI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison BAI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("BAS", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "BAI" }
        assertThat(auditMessageDetails).allMatch { !it.active }
      }

      @Test
      fun `deactivates all prison incentive levels even when default for admission is active`() {
        makePrisonIncentiveLevel("BAI", "BAS")
        makePrisonIncentiveLevel("BAI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("BAI", "ENH")

        webTestClient.delete()
          .uri("/incentive/prison-levels/BAI")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "levelCode": "BAS", "levelName": "Basic", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 2750, "remandSpendLimitInPence": 27500, "convictedTransferLimitInPence": 550, "convictedSpendLimitInPence": 5500,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "STD", "levelName": "Standard", "prisonId": "BAI", "active": false, "defaultOnAdmission": true,
                "remandTransferLimitInPence": 6050, "remandSpendLimitInPence": 60500, "convictedTransferLimitInPence": 1980, "convictedSpendLimitInPence": 19800,
                "visitOrders": 2, "privilegedVisitOrders": 1
              },
              {
                "levelCode": "ENH", "levelName": "Enhanced", "prisonId": "BAI", "active": false, "defaultOnAdmission": false,
                "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
                "visitOrders": 2, "privilegedVisitOrders": 1
              }
            ]
            """,
            JsonCompareMode.STRICT,
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("BAI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { !it.active }
        }

        val domainEvents = getPublishedDomainEvents()
        val auditMessages = getSentAuditMessages()

        val expectedPrisonIncentiveLevelChanges = 3
        assertThat(domainEvents).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages).hasSize(expectedPrisonIncentiveLevelChanges)
        assertThat(domainEvents.count { it.eventType == "incentives.prison-level.changed" }).isEqualTo(expectedPrisonIncentiveLevelChanges)
        assertThat(auditMessages.count { it.what == "PRISON_INCENTIVE_LEVEL_UPDATED" }).isEqualTo(expectedPrisonIncentiveLevelChanges)

        assertThat(domainEvents).allMatch {
          it.additionalInformation?.prisonId == "BAI" &&
            it.description.matches(Regex("^Incentive level \\(...\\) in prison BAI has been updated$"))
        }
        assertThat(
          domainEvents.map { it.additionalInformation?.incentiveLevel }.toSet(),
        ).isEqualTo(setOf("BAS", "STD", "ENH"))

        val auditMessageDetails = auditMessages.map { objectMapper.readValue(it.details, PrisonIncentiveLevel::class.java) }
        assertThat(auditMessageDetails).allMatch { it.prisonId == "BAI" }
        assertThat(auditMessageDetails).allMatch { !it.active }
      }

      @Test
      fun `requires correct role to deactivate all prison incentive levels`() {
        makePrisonIncentiveLevel("MDI", "BAS")
        makePrisonIncentiveLevel("MDI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("MDI", "ENH")

        webTestClient.delete()
          .uri("/incentive/prison-levels/MDI")
          .headers(setAuthorisation())
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("MDI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate all prison incentive levels when some levels have prisoners on them`() {
        makePrisonIncentiveLevel("MDI", "BAS")
        makePrisonIncentiveLevel("MDI", "STD") // Standard is the default for admission
        makePrisonIncentiveLevel("MDI", "ENH")
        makeIncentiveReviews("MDI")
        // make only BAS & ENH have prisoners on them
        runBlocking {
          incentiveReviewRepository.deleteAllById(
            incentiveReviewRepository.findAll().filter { review ->
              review.levelCode != "BAS" && review.levelCode != "ENH"
            }.map { it.id }.toList(),
          )
        }

        webTestClient.delete()
          .uri("/incentive/prison-levels/MDI")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must remain active if there are prisoners on it currently",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
            moreInfo = "BAS,ENH",
          )

        runBlocking {
          val prisonIncentiveLevels = prisonIncentiveLevelRepository.findAllByPrisonId("MDI").toList()
          assertThat(prisonIncentiveLevels).hasSize(3)
          assertThat(prisonIncentiveLevels).allMatch { it.active }
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }

    @DisplayName("deactivate level")
    @Nested
    inner class DeactivateLevel {
      @Test
      fun `deactivates a prison incentive level when one exists`() {
        makePrisonIncentiveLevel("WRI", "EN2")
        `deactivates a prison incentive level when per-prison information does not exist`()
      }

      @Test
      fun `deactivates a prison incentive level when per-prison information does not exist`() {
        makePrisonIncentiveLevel("WRI", "STD") // Standard is the default for admission, needed to allow deactivation of EN2

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
              "levelCode": "EN2", "levelName": "Enhanced 2", "prisonId": "WRI", "active": false, "defaultOnAdmission": false,
              "remandTransferLimitInPence": 6600, "remandSpendLimitInPence": 66000, "convictedTransferLimitInPence": 3300, "convictedSpendLimitInPence": 33000,
              "visitOrders": 2, "privilegedVisitOrders": 1
            }
            """,
            JsonCompareMode.STRICT,
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

        assertDomainEventSent("incentives.prison-level.changed").let {
          assertThat(it.description).isEqualTo("Incentive level (EN2) in prison WRI has been updated")
          assertThat(it.additionalInformation?.incentiveLevel).isEqualTo("EN2")
          assertThat(it.additionalInformation?.prisonId).isEqualTo("WRI")
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
        makePrisonIncentiveLevel("WRI", "ENH")

        webTestClient.delete()
          .uri("/incentive/prison-levels/WRI/level/ENH")
          .headers(setAuthorisation())
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("WRI", "ENH")
          assertThat(prisonIncentiveLevel?.active).isTrue
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a prison incentive level when incentive level does not exist`() {
        webTestClient.delete()
          .uri("/incentive/prison-levels/WRI/level/bas")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.NOT_FOUND,
            "No incentive level found for code `bas`",
          )

        runBlocking {
          assertThat(prisonIncentiveLevelRepository.count()).isZero
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a prison incentive level which is default for admission`() {
        runBlocking {
          // Pretend that Standard is not required
          val standard = incentiveLevelRepository.findById("STD")!!.copy(required = false)
          incentiveLevelRepository.save(standard)
        }
        makePrisonIncentiveLevel("MDI", "STD") // Standard is the default for admission

        webTestClient.delete()
          .uri("/incentive/prison-levels/MDI/level/STD")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made inactive and still be the default for admission",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfDefault,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "STD")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.defaultOnAdmission).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a prison incentive level which is required globally`() {
        makePrisonIncentiveLevel("MDI", "BAS") // Basic is globally required

        webTestClient.delete()
          .uri("/incentive/prison-levels/MDI/level/BAS")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level cannot be made inactive and when it is globally required",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfRequired,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "BAS")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }

      @Test
      fun `fails to deactivate a prison incentive level which has prisoners on it`() {
        makePrisonIncentiveLevel("MDI", "STD") // Standard is the default for admission, needed to manipulate other levels
        makePrisonIncentiveLevel("MDI", "EN2")
        makeIncentiveReviews("MDI") // EN2 has A1234AB/1234135 & A1234AD/1234137 on it

        webTestClient.delete()
          .uri("/incentive/prison-levels/MDI/level/EN2")
          .withLocalAuthorisation()
          .header("Content-Type", "application/json")
          .exchange()
          .expectErrorResponse(
            HttpStatus.BAD_REQUEST,
            "A level must remain active if there are prisoners on it currently",
            errorCode = ErrorCode.PrisonIncentiveLevelActiveIfPrisonersExist,
          )

        runBlocking {
          val prisonIncentiveLevel = prisonIncentiveLevelRepository.findFirstByPrisonIdAndLevelCode("MDI", "EN2")
          assertThat(prisonIncentiveLevel?.active).isTrue
          assertThat(prisonIncentiveLevel?.whenUpdated).isNotEqualTo(now)
        }

        assertNoDomainEventSent()
        assertNoAuditMessageSent()
      }
    }
  }

  private fun makeIncentiveReviews(prisonId: String): Unit = runBlocking {
    // prisoner numbers and booking ids match stubbed offender search response
    incentiveReviewRepository.saveAll(
      listOf(
        IncentiveReview(
          prisonerNumber = "A1234AA",
          bookingId = 1234134,
          levelCode = "STD",
          prisonId = prisonId,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        ),
        IncentiveReview(
          prisonerNumber = "A1234AB",
          bookingId = 1234135,
          levelCode = "EN2",
          prisonId = prisonId,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        ),
        IncentiveReview(
          prisonerNumber = "A1234AC",
          bookingId = 1234136,
          levelCode = "ENH",
          prisonId = prisonId,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        ),
        IncentiveReview(
          prisonerNumber = "A1234AD",
          bookingId = 1234137,
          levelCode = "EN2",
          prisonId = prisonId,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        ),
        IncentiveReview(
          prisonerNumber = "A1234AE",
          bookingId = 1234138,
          levelCode = "BAS",
          prisonId = prisonId,
          reviewedBy = "TEST_STAFF1",
          reviewTime = LocalDateTime.now(clock).minusDays(1),
        ),
      ),
    ).collect()
  }
}
