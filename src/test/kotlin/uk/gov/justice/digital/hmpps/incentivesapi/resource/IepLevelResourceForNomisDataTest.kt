package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDate.now

@ActiveProfiles("test", "test-nomis")
class IepLevelResourceForNomisDataTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var repository: PrisonerIepLevelRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetAll()
    repository.deleteAll()
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    repository.deleteAll()
  }

  @Test
  fun `get IEP Levels for a prisoner`() {
    prisonApiMockServer.stubIEPSummaryForBooking()

    val lastReviewDate = LocalDate.of(2021, 12, 2)
    val daysSinceReview = Duration.between(lastReviewDate.atStartOfDay(), now().atStartOfDay()).toDays().toInt()

    webTestClient.get().uri("/iep/reviews/booking/1234134")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
             "bookingId":1234134,
             "daysSinceReview": $daysSinceReview,
             "iepDate":"2021-12-02",
             "iepLevel":"Basic",
             "iepTime":"2021-12-02T09:24:42.894",
             "nextReviewDate": "2021-12-09",
             "iepDetails":[
                {
                   "bookingId":1234134,
                   "iepDate":"2021-12-02",
                   "iepTime":"2021-12-02T09:24:42.894",
                   "agencyId":"MDI",
                   "iepLevel":"Basic",
                   "userId":"TEST_USER",
                   "auditModuleName":"PRISON_API"
                },
                {
                   "bookingId":1234134,
                   "iepDate":"2020-11-02",
                   "iepTime":"2021-11-02T09:00:42.894",
                   "agencyId":"BXI",
                   "iepLevel":"Entry",
                   "userId":"TEST_USER",
                   "auditModuleName":"PRISON_API"
                }
             ]
          }
          """
      )
  }

  @Test
  fun `get IEP Levels for a list of prisoners`() {
    prisonApiMockServer.stubIEPSummary()

    webTestClient.post().uri("/iep/reviews/bookings")
      .headers(setAuthorisation())
      .bodyValue(listOf(1234134, 1234135, 1234136, 1234137, 1234138, 2734134))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
          [
            {
             "bookingId": 1234134,
             "iepLevel": "Basic"
             },
               {
             "bookingId": 1234135,
             "iepLevel": "Standard"
             },
               {
             "bookingId": 1234136,
             "iepLevel": "Enhanced"
             },
               {
             "bookingId": 1234137,
             "iepLevel": "Basic"
             },
               {
             "bookingId": 1234138,
             "iepLevel": "Standard"
             },
               {
             "bookingId": 2734134,
             "iepLevel": "Entry"
             }
          ]
          """
      )
  }

  @Test
  fun `get IEP Levels returns an error if no booking IDs provided`() {
    prisonApiMockServer.stubIEPSummary()

    webTestClient.post().uri("/iep/reviews/bookings")
      .headers(setAuthorisation())
      .bodyValue(emptyList<Long>())
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("userMessage")
      .isEqualTo("Invalid parameters: `bookingIds` list must not be empty")
  }
}
