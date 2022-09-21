package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepMigration
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IepLevelResourceTest : SqsIntegrationTestBase() {
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
    prisonApiMockServer.stubIEPSummaryForBooking()

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
  fun `handle undefined path variable`() {
    webTestClient.get().uri("/iep/reviews/booking/undefined")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isBadRequest
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

    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)

    webTestClient.post().uri("/iep/reviews/booking/$bookingId")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("STD", "A comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now().format(DateTimeFormatter.ISO_DATE)
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
             "iepDetails":[
                {
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Standard",
                   "comments":"A comment",
                   "userId":"INCENTIVES_ADM",
                   "auditModuleName":"Incentives-API"
                }
             ]
          }
          """
      )
  }

  @Test
  fun `add IEP Level for a prisoner by noms`() {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77777L)
    prisonApiMockServer.stubGetLocationById(locationId = 77777L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)

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
             "iepDetails":[
                {
                   "prisonerNumber": $prisonerNumber,
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Enhanced",
                   "comments":"A different comment",
                   "userId":"INCENTIVES_ADM",
                   "locationId": "1-2-003",
                   "reviewType": "REVIEW",
                   "auditModuleName":"Incentives-API"
                },
                {
                    "prisonerNumber": $prisonerNumber,
                   "bookingId":$bookingId,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Basic",
                   "comments":"Basic Level",
                   "locationId": "1-2-003",
                   "userId":"INCENTIVES_ADM",
                   "reviewType": "INITIAL",
                   "auditModuleName":"Incentives-API"
                }

             ]
          }
          """
      )
  }

  @Test
  fun `Retrieve list of IEP Reviews from incentives DB`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonApiMockServer.stubGetPrisonerInfoByBooking(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      locationId = 77778L
    )
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)

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
      locationId = 77779L
    )
    prisonApiMockServer.stubGetLocationById(locationId = 77779L, locationDesc = "1-2-004")
    prisonApiMockServer.stubAddIep(bookingId = bookingId2)

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
          """
      )
  }

  @Nested
  inner class migrateIepReview {

    @Test
    fun `store IEP Level for a prisoner`() {
      // Given
      val bookingId = 3330000L
      val prisonerNumber = "A1234AC"
      val iepMigration = iepMigration()
      prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)

      // When
      val migrationRequest = webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(iepMigration)
        .exchange()

      // Then
      migrationRequest.expectStatus().isCreated

      webTestClient.get().uri("/iep/reviews/booking/$bookingId?use-nomis-data=false")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
            {
             "bookingId":$bookingId,
             "daysSinceReview":0,
             "iepDate":"${iepMigration.iepTime.toLocalDate()}",
             "iepLevel":"Standard",
             "iepDetails":[
                {
                   "bookingId":$bookingId,
                   "iepDate":"${iepMigration.iepTime.toLocalDate()}",
                   "agencyId":"${iepMigration.prisonId}",
                   "locationId":"${iepMigration.locationId}",
                   "iepLevel":"Standard",
                   "comments":"${iepMigration.comment}",
                   "userId":"${iepMigration.userId}",
                   "auditModuleName":"Incentives-API"
                }
             ]
          }
          """
        )
    }

    @Test
    fun `handle undefined path variable`() {
      webTestClient.post().uri("/iep/migration/booking/undefined")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("write")))
        .bodyValue(iepMigration())
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `handle bad payload`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("write")))
        .bodyValue(iepMigration("Standard")) // fails validation because it's > 6 length
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `fails without write scope`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
        .bodyValue(iepMigration())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `fails without correct role`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
        .bodyValue(iepMigration())
        .exchange()
        .expectStatus().isForbidden
    }

    private fun iepMigration(iepLevel: String = "STD") = IepMigration(
      iepTime = LocalDateTime.now(),
      prisonId = "MDI",
      locationId = "1-2-003",
      iepLevel = iepLevel,
      comment = "A comment",
      userId = "XYZ_GEN",
      reviewType = ReviewType.REVIEW,
      current = true,
    )
  }
}
