package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter

@Deprecated("This test class is deprecated. Use ManageIncentiveReviewsResourceTestLevel instead.")
class UnprotectedIncentiveReviewsResourceTestLevel : IncentiveLevelResourceTestBase() {
  @Autowired
  private lateinit var repository: IncentiveReviewRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetAll()
    repository.deleteAll()
  }

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    repository.deleteAll()
    super.tearDown()
  }

  @Test
  fun `handle undefined path variable`() {
    webTestClient.get().uri("/iep/reviews/booking/undefined")
      .headers(setAuthorisation())
      .exchange()
      .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request format")
  }

  @Test
  fun `add IEP Level fails without write scope`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add IEP Level fails without correct role`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add IEP Level for a prisoner by booking Id`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now().format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = now().plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
             "bookingId":$bookingId,
             "daysSinceReview":0,
             "iepDate":"$today",
             "iepLevel":"Standard",
             "iepCode": "STD",
             "nextReviewDate": "$nextReviewDate",
             "iepDetails":[
                {
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Standard",
                   "iepCode": "STD",
                   "comments":"A comment",
                   "userId":"INCENTIVES_ADM",
                   "auditModuleName":"INCENTIVES_API"
                }
             ]
          }
          """,
      )
  }

  @Test
  fun `Retrieve list of IEP Reviews from incentives DB`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      locationId = 77778L,
    )
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("BAS", "Basic Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "Standard Level"))
      .exchange()
      .expectStatus().isCreated

    val bookingId2 = 3330001L
    val prisonerNumber2 = "A1234AD"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(
      bookingId = bookingId2,
      prisonerNumber = prisonerNumber2,
      locationId = 77779L,
    )
    prisonApiMockServer.stubGetLocationById(locationId = 77779L, locationDesc = "1-2-004")
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId2, prisonerNumber2)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId2")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("ENH", "Standard Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/iep/reviews/bookings")
      .headers(setAuthorisation())
      .bodyValue(listOf(3330000L, 3330001L))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
          [
            {
             "bookingId": 3330000,
             "iepLevel": "Standard"
             },
               {
             "bookingId": 3330001,
             "iepLevel": "Enhanced"
              }
          ]
          """,
      )
  }
}