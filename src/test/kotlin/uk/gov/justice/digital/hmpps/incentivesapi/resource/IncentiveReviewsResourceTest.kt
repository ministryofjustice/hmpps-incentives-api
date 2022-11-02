package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDateTime

class IncentiveReviewsResourceTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var repository: PrisonerIepLevelRepository

  @BeforeEach
  internal fun setUp(): Unit = runBlocking {
    offenderSearchMockServer.resetAll()
    prisonApiMockServer.resetAll()

    repository.deleteAll()
    persistPrisonerIepLevel(
      bookingId = 110001,
      prisonerNumber = "A1409AE",
    )

    persistPrisonerIepLevel(
      bookingId = 110002,
      prisonerNumber = "G6123VU",
    )
  }

  private val iepTime: LocalDateTime = LocalDateTime.now()
  suspend fun persistPrisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
  ) = repository.save(
    PrisonerIepLevel(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = iepTime,
      prisonId = "MDI",
      iepCode = "STD",
      reviewType = ReviewType.REVIEW,
      current = true,
      locationId = "1-1-002",
      commentText = "test comment",
      reviewedBy = "TEST_USER",
    )
  )

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    repository.deleteAll()
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
            "overdueCount": 0,
            "reviews": [
              {
                "prisonerNumber": "A1409AE",
                "bookingId": 110001,
                "firstName": "JAMES",
                "lastName": "HALLS",
                "positiveBehaviours": 3,
                "negativeBehaviours": 4,
                "acctOpenStatus": true,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "positiveBehaviours": 0,
                "negativeBehaviours": 0,
                "acctOpenStatus": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
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
            "overdueCount": 0,
            "reviews": [
              {
                "prisonerNumber": "A1409AE",
                "bookingId": 110001,
                "firstName": "JAMES",
                "lastName": "HALLS",
                "positiveBehaviours": 3,
                "negativeBehaviours": 4,
                "acctOpenStatus": true,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "G6123VU",
                "bookingId": 110002,
                "firstName": "RHYS",
                "lastName": "JONES",
                "positiveBehaviours": 0,
                "negativeBehaviours": 0,
                "acctOpenStatus": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              }
            ],
            "locationDescription": "Unknown location"
          }
        """,
        true,
      )
  }

  @Test
  fun `when insufficient data to calculate next review date`(): Unit = runBlocking {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()

    repository.deleteAll()

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isNotFound
      .expectBody().json(
        // language=json
        """
          {
            "status": 404,
            "userMessage": "Not Found: No Data found for ID(s) [110001, 110002]",
            "developerMessage": "No Data found for ID(s) [110001, 110002]"
          }
          """
      )
  }
}
