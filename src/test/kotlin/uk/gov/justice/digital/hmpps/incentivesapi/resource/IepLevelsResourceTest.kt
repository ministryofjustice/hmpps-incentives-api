package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase

class IepLevelsResourceTest : IncentiveLevelResourceTestBase() {

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetAll()
  }

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    super.tearDown()
  }

  @Test
  fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/iep/levels/MDI")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `get IEP Levels for a prison`() {
    val prisonId = "MDI"
    listOf("BAS", "STD", "ENH", "ENT").forEach { levelCode ->
      listOf("MDI").forEach { prisonId ->
        makePrisonIncentiveLevel(prisonId, levelCode)
      }
    }

    webTestClient.get().uri("/iep/levels/$prisonId")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .json(
        """
        [
            {
                "iepLevel": "BAS",
                "iepDescription": "Basic",
                "sequence": 1,
                "default": false
            },
            {
                "iepLevel": "STD",
                "iepDescription": "Standard",
                "sequence": 2,
                "default": true
            },
            {
                "iepLevel": "ENH",
                "iepDescription": "Enhanced",
                "sequence": 3,
                "default": false
            }
        ]
        """.trimIndent(),
      )
  }
}
