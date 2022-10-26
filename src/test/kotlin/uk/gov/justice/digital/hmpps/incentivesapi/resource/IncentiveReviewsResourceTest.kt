package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase

class IncentiveReviewsResourceTest : SqsIntegrationTestBase() {
  @BeforeEach
  internal fun setUp() {
    offenderSearchMockServer.resetAll()
    prisonApiMockServer.resetAll()
  }

  @Test
  fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `requires correct role to retrieve data`() {
    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1")
      .headers(setAuthorisation(roles = emptyList()))
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `validates path parameters`() {
    offenderSearchMockServer.stubFindOffenders("Moorland")

    webTestClient.get()
      .uri("/incentives-reviews/prison/Moorland/location/MDI-1?page=0")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `loads prisoner details from offender search`() {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "reviewCount": 2,
            "reviews": [
              {
                "prisonerNumber": "A1409AE",
                "bookingId": 110001,
                "firstName": "JAMES",
                "lastName": "HALLS",
                "acctOpenStatus": true
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "acctOpenStatus": false
              }
            ],
            "locationDescription": "Houseblock 1"
          }
        """,
        true,
      )
  }

  @Test
  fun `loads prisoner details even when location description not found`() {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubApi404for("/api/locations/code/MDI")

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "reviewCount": 2,
            "reviews": [
              {
                "prisonerNumber": "A1409AE",
                "bookingId": 110001,
                "firstName": "JAMES",
                "lastName": "HALLS",
                "acctOpenStatus": true
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "acctOpenStatus": false
              }
            ],
            "locationDescription": "Unknown location"
          }
        """,
        true,
      )
  }
}
