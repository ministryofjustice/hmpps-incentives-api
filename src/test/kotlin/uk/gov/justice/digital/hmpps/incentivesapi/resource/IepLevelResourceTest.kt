package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IntegrationTestBase

class IepLevelResourceTest : IntegrationTestBase() {
  @BeforeEach
  internal fun setUp() {
    prisonApiMockServer.resetAll()
  }

  val matchByIepCode = "$.[?(@.iepLevel == '%s')]"

  @Test
  internal fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/iep/levels/MDI")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `get IEP Levels for a prison`() {

    webTestClient.get().uri("/iep/levels/MDI")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.[*]").isArray
      .jsonPath(matchByIepCode, "STD").exists()
      .jsonPath(matchByIepCode, "BAS").exists()
      .jsonPath(matchByIepCode, "ENH").exists()
      .jsonPath(matchByIepCode, "ENT").doesNotExist()
  }

  @Test
  fun `get IEP Levels for a prisoner`() {
    prisonApiMockServer.stubIEPSummary()

    webTestClient.get().uri("/iep/reviews/booking/1234134")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
             "bookingId":1234134,
             "daysSinceReview":35,
             "iepDate":"2021-12-02",
             "iepLevel":"Basic",
             "iepTime":"2021-12-02T09:24:42.894",
             "iepDetails":[
                {
                   "bookingId":1234134,
                   "sequence":2,
                   "iepDate":"2021-12-02",
                   "iepTime":"2021-12-02T09:24:42.894",
                   "agencyId":"MDI",
                   "iepLevel":"Basic",
                   "userId":"TEST_USER",
                   "auditModuleName":"PRISON_API"
                },
                {
                   "bookingId":1234134,
                   "sequence":1,
                   "iepDate":"2020-11-02",
                   "iepTime":"2021-11-02T09:00:42.894",
                   "agencyId":"MDI",
                   "iepLevel":"Entry",
                   "userId":"TEST_USER",
                   "auditModuleName":"PRISON_API"
                }
             ]
          }
          """
      )
  }
}
