package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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

  @Nested
  inner class `validates request parameters` {
    @Test
    fun `when prisonId is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("Moorland")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/Moorland/location/MDI-1")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `prisonId` must have length of at most 5",
            "developerMessage": "Invalid parameters: `prisonId` must have length of at most 5"
          }
          """
        )
    }

    @Test
    fun `when page is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1?page=0")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `page` must be at least 1",
            "developerMessage": "Invalid parameters: `page` must be at least 1"
          }
          """
        )
    }

    @Test
    fun `when page & pageSize are incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1?page=0&pageSize=0")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `page` must be at least 1, `pageSize` must be at least 1",
            "developerMessage": "Invalid parameters: `page` must be at least 1, `pageSize` must be at least 1"
          }
          """
        )
    }
  }

  @Test
  fun `loads prisoner details from offender search and prison api`() {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()

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
                "positiveBehaviours": 3,
                "negativeBehaviours": 4,
                "acctOpenStatus": true
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "positiveBehaviours": 0,
                "negativeBehaviours": 0,
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
    prisonApiMockServer.stubApi404for("/api/locations/code/MDI-1")
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()

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
                "positiveBehaviours": 3,
                "negativeBehaviours": 4,
                "acctOpenStatus": true
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "positiveBehaviours": 0,
                "negativeBehaviours": 0,
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
