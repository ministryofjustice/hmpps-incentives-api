package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IntegrationTestBase
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter

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
      .expectStatus().isNoContent

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
                   "sequence":1,
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
    val bookingId = 1234134L
    val prisonerNumber = "A1234AB"

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77777L)
    prisonApiMockServer.stubGetLocationById(locationId = 77777L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)

    webTestClient.post().uri("/iep/reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("BAS", "Basic Level"))
      .exchange()
      .expectStatus().isNoContent

    webTestClient.post().uri("/iep/reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
      .bodyValue(IepReview("ENH", "A different comment"))
      .exchange()
      .expectStatus().isNoContent

    val today = now().format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/iep/reviews/prisoner/$prisonerNumber?use-nomis-data=false")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
             "bookingId":$bookingId,
             "daysSinceReview":0,
             "iepDate":"$today",
             "iepLevel":"Enhanced",
             "iepDetails":[
                {
                   "bookingId":$bookingId,
                   "sequence":2,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Enhanced",
                   "comments":"A different comment",
                   "userId":"INCENTIVES_ADM",
                   "auditModuleName":"Incentives-API"
                },
                {
                   "bookingId":$bookingId,
                   "sequence":1,
                   "iepDate":"$today",
                   "agencyId":"MDI",
                   "iepLevel":"Basic",
                   "comments":"Basic Level",
                   "userId":"INCENTIVES_ADM",
                   "auditModuleName":"Incentives-API"
                }

             ]
          }
          """
      )
  }
}
