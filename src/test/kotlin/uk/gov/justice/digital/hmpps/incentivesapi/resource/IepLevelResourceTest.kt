package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter

class IepLevelResourceTest : IncentiveLevelResourceTestBase() {
  @Autowired
  private lateinit var repository: PrisonerIepLevelRepository

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
  fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/iep/levels/MDI")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `Prison API '404 Not Found' responses are handled instead of responding 500 Internal Server Error`() {
    val bookingId: Long = 1234134

    prisonApiMockServer.stubApi404for("/api/bookings/$bookingId/iepSummary?withDetails=true")

    webTestClient.get().uri("/iep/reviews/booking/$bookingId?use-nomis-data=true")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isNotFound
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

  @Test
  fun `handle undefined path variable`() {
    webTestClient.get().uri("/iep/reviews/booking/undefined")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `add IEP Level fails without write scope`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
      .bodyValue(IepReview("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add IEP Level fails without correct role`() {
    val bookingId = 3330000L

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("STD", "A comment"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `add IEP Level for a prisoner by booking Id`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("STD", "A comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now().format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = now().plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/iep/reviews/booking/$bookingId?use-nomis-data=false")
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
  fun `add IEP Level for a prisoner by noms`() {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    val locationId = 77777L

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/iep/reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview(iepLevel = "BAS", comment = "Basic Level", reviewType = ReviewType.INITIAL))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/iep/reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("ENH", "A different comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now().format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = now().plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/iep/reviews/prisoner/$prisonerNumber?use-nomis-data=false")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
             "bookingId":$bookingId,
             "prisonerNumber": $prisonerNumber,
             "daysSinceReview":0,
             "iepDate":"$today",
             "iepLevel":"Enhanced",
             "iepCode": "ENH",
             "nextReviewDate":"$nextReviewDate",
             "iepDetails":[
                {
                   "prisonerNumber": $prisonerNumber,
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId": $prisonId,
                   "iepLevel":"Enhanced",
                   "iepCode": "ENH",
                   "comments":"A different comment",
                   "userId":"INCENTIVES_ADM",
                   "locationId": "1-2-003",
                   "reviewType": "REVIEW",
                   "auditModuleName":"INCENTIVES_API"
                },
                {
                   "prisonerNumber": $prisonerNumber,
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId": $prisonId,
                   "iepLevel":"Basic",
                   "iepCode": "BAS",
                   "comments":"Basic Level",
                   "locationId": "1-2-003",
                   "userId":"INCENTIVES_ADM",
                   "reviewType": "INITIAL",
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
    prisonApiMockServer.stubAddIep(bookingId = bookingId)
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("BAS", "Basic Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("STD", "Standard Level"))
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
    prisonApiMockServer.stubAddIep(bookingId = bookingId2)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId2, prisonerNumber2)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId2")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("ENH", "Standard Level"))
      .exchange()
      .expectStatus().isCreated

    webTestClient.post().uri("/iep/reviews/bookings?use-nomis-data=false")
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
