package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Manage incentive reviews resource")
class ManageIncentiveReviewsResourceTest : IncentiveLevelResourceTestBase() {
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
    webTestClient.get().uri("/incentive-reviews/booking/undefined")
      .headers(setAuthorisation())
      .exchange()
      .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request format")
  }

  @Test
  fun `add incentive Level fails without write scope`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add incentive Level fails without correct role`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add incentive review fails when review time in future`() {
    val prisonerNumber = "A1244AB"
    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = 1231232, prisonerNumber = prisonerNumber)

    val reviewTime = LocalDateTime.now().plusHours(1)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("ENH", "Future Review", reviewTime = reviewTime))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `add incentive Level for a prisoner by booking Id`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now().format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = now().plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "bookingId": $bookingId,
          "daysSinceReview": 0,
          "iepDate": "$today",
          "iepLevel": "Standard",
          "iepCode": "STD",
          "nextReviewDate": "$nextReviewDate",
          "iepDetails": [
            {
              "bookingId": $bookingId,
              "iepDate": "$today",
              "agencyId": "MDI",
              "iepLevel": "Standard",
              "iepCode": "STD",
              "comments": "A comment",
              "userId": "INCENTIVES_ADM",
              "auditModuleName": "INCENTIVES_API"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `add incentive Level for a prisoner by noms`() {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    val now = LocalDateTime.now()
    val previousTime = now.minusDays(2)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest(iepLevel = "BAS", comment = "Basic Level", reviewType = ReviewType.INITIAL, reviewTime = previousTime))
      .exchange()
      .expectStatus().isCreated

    val reviewTime = now.minusDays(1)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("ENH", "A different comment", reviewedBy = "DIFFERENT_USER", reviewTime = reviewTime))
      .exchange()
      .expectStatus().isCreated

    val previousReviewTime = previousTime.format(DateTimeFormatter.ISO_DATE)
    val lastReviewTime = reviewTime.format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = reviewTime.plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "bookingId": $bookingId,
          "prisonerNumber": $prisonerNumber,
          "daysSinceReview": 1,
          "iepDate": "$lastReviewTime",
          "iepLevel": "Enhanced",
          "iepCode": "ENH",
          "nextReviewDate": "$nextReviewDate",
          "iepDetails": [
            {
              "prisonerNumber": $prisonerNumber,
              "bookingId": $bookingId,
              "iepDate": "$lastReviewTime",
              "agencyId": $prisonId,
              "iepLevel": "Enhanced",
              "iepCode": "ENH",
              "comments": "A different comment",
              "userId": "DIFFERENT_USER",
              "reviewType": "REVIEW",
              "auditModuleName": "INCENTIVES_API"
            },
            {
              "prisonerNumber": $prisonerNumber,
              "bookingId": $bookingId,
              "iepDate": "$previousReviewTime",
              "agencyId": $prisonId,
              "iepLevel": "Basic",
              "iepCode": "BAS",
              "comments": "Basic Level",
              "userId": "INCENTIVES_ADM",
              "reviewType": "INITIAL",
              "auditModuleName": "INCENTIVES_API"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `Retrieve list of Incentive Reviews from incentives DB`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
    )
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("BAS", "Basic Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "Standard Level"))
      .exchange()
      .expectStatus().isCreated

    val bookingId2 = 3330001L
    val prisonerNumber2 = "A1234AD"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(
      bookingId = bookingId2,
      prisonerNumber = prisonerNumber2,
    )
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId2, prisonerNumber2)

    webTestClient.post().uri("/incentive-reviews/booking/$bookingId2")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("ENH", "Standard Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/incentive-reviews/bookings")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .bodyValue(listOf(3330000L, 3330001L))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
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
        JsonCompareMode.LENIENT,
      )
  }
}
