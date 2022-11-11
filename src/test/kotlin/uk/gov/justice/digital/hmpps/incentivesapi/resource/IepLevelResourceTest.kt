package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.SyncPostRequest
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
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

    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels(prisonId)

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
                "sequence": 3,
                "default": true
            },
            {
                "iepLevel": "ENH",
                "iepDescription": "Enhanced",
                "sequence": 4,
                "default": false
            }
        ]
        """.trimIndent()
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
    val prisonId = "MDI"

    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)
    prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)
    offenderSearchMockServer.stubGetOffender(prisonId, prisonerNumber, bookingId, withOpenAcct = false)

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
          """
      )
  }

  @Test
  fun `add IEP Level for a prisoner by noms`() {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"
    val locationId = 77777L

    prisonApiMockServer.stubGetPrisonerInfoByNoms(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = locationId)
    prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
    prisonApiMockServer.stubAddIep(bookingId = bookingId)
    prisonApiMockServer.stubIepLevels()
    offenderSearchMockServer.stubGetOffender(prisonId, prisonerNumber, bookingId, withOpenAcct = false)

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
    prisonApiMockServer.stubIepLevels()

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
  inner class SyncIepReview {

    private val bookingId = 3330000L
    private val requestBody = syncPostRequest()
    private val prisonerNumber = "A1234AC"

    private var existingPrisonerIepLevel: PrisonerIepLevel? = null

    private val syncCreateEndpoint = "/iep/sync/booking/$bookingId"
    private var syncPatchEndpoint: String? = null
    private var syncDeleteEndpoint: String? = null

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      existingPrisonerIepLevel = repository.save(
        prisonerIepLevel(bookingId = bookingId, prisonerNumber = prisonerNumber)
      )

      syncPatchEndpoint = "/iep/sync/booking/$bookingId/id/${existingPrisonerIepLevel!!.id}"
      syncDeleteEndpoint = syncPatchEndpoint
    }

    @Test
    fun `POST to sync endpoint without write scope responds 403 Unauthorized`() {
      // When the client doesn't have the `write` scope the API responds 403 Forbidden
      webTestClient.post().uri(syncCreateEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `PATCH to sync endpoint without write scope responds 403 Unauthorized`() {
      // When the client doesn't have the `write` scope the API responds 403 Forbidden
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `DELETE to sync endpoint without write scope responds 403 Unauthorized`() {
      // When the client doesn't have the `write` scope the API responds 403 Forbidden
      webTestClient.delete().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `POST to sync endpoint without 'ROLE_MAINTAIN_IEP' role responds 403 Unauthorized`() {
      // When the client doesn't have the `ROLE_MAINTAIN_IEP` role the API responds 403 Forbidden
      webTestClient.post().uri(syncCreateEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `PATCH to sync endpoint without 'ROLE_MAINTAIN_IEP' role responds 403 Unauthorized`() {
      // When the client doesn't have the `ROLE_MAINTAIN_IEP` role the API responds 403 Forbidden
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `DELETE to sync endpoint without 'ROLE_MAINTAIN_IEP' role responds 403 Unauthorized`() {
      // When the client doesn't have the `ROLE_MAINTAIN_IEP` role the API responds 403 Forbidden
      webTestClient.delete().uri(syncDeleteEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `POST to sync endpoint when bookingId doesn't exist responds 404 Not Found`() {
      // Given the bookingId is not found
      prisonApiMockServer.stubApi404for("/api/bookings/$bookingId?basicInfo=true")

      // The API responds 404 Not Found
      webTestClient.post().uri(syncCreateEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `PATCH to sync endpoint when record with given id doesn't exist responds 404 Not Found`() {
      val syncPatchEndpoint = "/iep/sync/booking/42000/id/42000"

      // The API responds 404 Not Found
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `DELETE to sync endpoint when record with given id doesn't exist responds 404 Not Found`() {
      val syncDeleteEndpoint = "/iep/sync/booking/42000/id/42000"

      // The API responds 404 Not Found
      webTestClient.delete().uri(syncDeleteEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `PATCH to sync endpoint when bookingId doesn't match the one in the IEP review record responds 404 Not Found`() {
      val wrongBookingId = existingPrisonerIepLevel!!.bookingId + 42
      val syncPatchEndpoint = "/iep/sync/booking/$wrongBookingId/id/${existingPrisonerIepLevel!!.id}"

      // The API responds 404 Not Found
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `DELETE to sync endpoint when bookingId doesn't match the one in the IEP review record responds 404 Not Found`() {
      val wrongBookingId = existingPrisonerIepLevel!!.bookingId + 42
      val syncDeleteEndpoint = "/iep/sync/booking/$wrongBookingId/id/${existingPrisonerIepLevel!!.id}"

      // The API responds 404 Not Found
      webTestClient.delete().uri(syncDeleteEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `POST to sync endpoint when request is invalid responds 400 Bad Request`() {
      val badRequest = mapOf("iepLevel" to "STD")

      // The API responds 400 Bad Request
      webTestClient.post().uri(syncCreateEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(badRequest)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `PATCH to sync endpoint when request is invalid responds 400 Bad Request`() {
      val badRequest = mapOf("iepTime" to null)

      // The API responds 400 Bad Request
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(badRequest)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `POST to sync endpoint when request valid creates the IEP review`() {
      val locationId = 77778L

      // Given the bookingId is valid
      prisonApiMockServer.stubGetPrisonerInfoByBooking(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        locationId = locationId,
      )
      prisonApiMockServer.stubGetLocationById(locationId = locationId, locationDesc = "1-2-003")
      prisonApiMockServer.stubIepLevels()
      prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId, prisonerNumber, locationId)
      offenderSearchMockServer.stubGetOffender(requestBody.prisonId, prisonerNumber, bookingId)

      // API responds 201 Created with the created IEP review record
      val responseBytes = webTestClient.post().uri(syncCreateEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isCreated
        .expectBody().json(
          """
          {
            "bookingId":$bookingId,
            "prisonerNumber": $prisonerNumber,
            "iepDate":"${requestBody.iepTime.toLocalDate()}",
            "agencyId":"${requestBody.prisonId}",
            "locationId":"1-2-003",
            "iepLevel":"Standard",
            "iepCode": "STD",
            "comments":"${requestBody.comment}",
            "userId":"${requestBody.userId}",
            "reviewType":"${requestBody.reviewType}",
            "auditModuleName":"INCENTIVES_API"
          }
          """.trimIndent()
        )
        .returnResult()
        .responseBody

      val prisonerIepLevelId = JSONObject(String(responseBytes)).getLong("id")

      // IEP review is also persisted (can be retrieved later on)
      webTestClient.get().uri("/iep/reviews/id/$prisonerIepLevelId")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
              {
                 "id": $prisonerIepLevelId,
                 "bookingId":$bookingId,
                 "iepDate":"${requestBody.iepTime.toLocalDate()}",
                 "agencyId":"${requestBody.prisonId}",
                 "locationId":"1-2-003",
                 "iepLevel":"Standard",
                 "iepCode": "STD",
                 "comments":"${requestBody.comment}",
                 "userId":"${requestBody.userId}",
                 "reviewType":"${requestBody.reviewType}",
                 "auditModuleName":"INCENTIVES_API"
              }
          """
        )
    }

    @Test
    fun `PATCH to sync endpoint when valid request has only one field updates that IEP review field`() {
      val updatedComment = "Updated comment"
      val requestBody = mapOf("comment" to updatedComment)
      prisonApiMockServer.stubIepLevels()

      val expectedResponseBody = """
        {
           "id": ${existingPrisonerIepLevel!!.id},
           "bookingId":$bookingId,
           "iepDate":"${existingPrisonerIepLevel!!.reviewTime.toLocalDate()}",
           "agencyId":"${existingPrisonerIepLevel!!.prisonId}",
           "locationId":"${existingPrisonerIepLevel!!.locationId}",
           "iepLevel":"Standard",
           "iepCode": "STD",
           "comments":"$updatedComment",
           "userId":"${existingPrisonerIepLevel!!.reviewedBy}",
           "reviewType":"${existingPrisonerIepLevel!!.reviewType}",
           "auditModuleName":"INCENTIVES_API"
        }
      """.trimIndent()

      // API responds 201 Created with the created IEP review record
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expectedResponseBody)

      val prisonerIepLevelId = existingPrisonerIepLevel!!.id

      // IEP review changes are also persisted (can be retrieved later on)
      webTestClient.get().uri("/iep/reviews/id/$prisonerIepLevelId")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expectedResponseBody)
    }

    @Test
    fun `PATCH to sync endpoint when valid request has all fields updates that IEP review`() {
      val updatedComment = "Updated comment"
      val updatedIepTime = LocalDateTime.now().minusDays(10)
      val updatedCurrent = !existingPrisonerIepLevel!!.current
      val requestBody = mapOf(
        "iepTime" to updatedIepTime,
        "comment" to updatedComment,
        "current" to updatedCurrent,
      )
      prisonApiMockServer.stubIepLevels()

      val expectedResponseBody = """
        {
           "id": ${existingPrisonerIepLevel!!.id},
           "bookingId":$bookingId,
           "iepDate":"${updatedIepTime.toLocalDate()}",
           "agencyId":"${existingPrisonerIepLevel!!.prisonId}",
           "locationId":"${existingPrisonerIepLevel!!.locationId}",
           "iepLevel":"Standard",
           "iepCode": "STD",
           "comments":"$updatedComment",
           "userId":"${existingPrisonerIepLevel!!.reviewedBy}",
           "reviewType":"${existingPrisonerIepLevel!!.reviewType}",
           "auditModuleName":"INCENTIVES_API"
        }
      """.trimIndent()

      // API responds 201 Created with the created IEP review record
      webTestClient.patch().uri(syncPatchEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expectedResponseBody)

      val prisonerIepLevelId = existingPrisonerIepLevel!!.id

      // IEP review changes are also persisted (can be retrieved later on)
      webTestClient.get().uri("/iep/reviews/id/$prisonerIepLevelId")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(expectedResponseBody)
    }

    @Test
    fun `DELETE to sync endpoint when request valid deletes the IEP review`() {
      // Given the bookingId is valid
      prisonApiMockServer.stubGetPrisonerInfoByBooking(
        bookingId = bookingId,
        prisonerNumber = prisonerNumber,
        locationId = 77778L,
      )
      prisonApiMockServer.stubGetLocationById(locationId = 77778L, locationDesc = "1-2-003")
      prisonApiMockServer.stubIepLevels()

      // API responds 201 Created with the created IEP review record
      webTestClient.delete().uri(syncDeleteEndpoint)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .exchange()
        .expectStatus().isNoContent

      val prisonerIepLevelId = existingPrisonerIepLevel!!.id

      // IEP review is no longer available (cannot be retrieved later on)
      webTestClient.get().uri("/iep/reviews/id/$prisonerIepLevelId")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isNotFound
    }

    private fun syncPostRequest(iepLevel: String = "STD") = SyncPostRequest(
      iepTime = LocalDateTime.now(),
      prisonId = "MDI",
      iepLevel = iepLevel,
      comment = "A comment",
      userId = "XYZ_GEN",
      reviewType = ReviewType.REVIEW,
      current = true,
    )

    private fun prisonerIepLevel(bookingId: Long = 3330000, prisonerNumber: String = "A1234AC") = PrisonerIepLevel(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = LocalDateTime.now(),
      prisonId = "MDI",
      locationId = "MDI-1",
      iepCode = "STD",
      commentText = "test comment",
      reviewedBy = "TEST_USER",
      reviewType = ReviewType.REVIEW,
      current = true,
    )
  }

  @Nested
  inner class migrateIepReview {

    @Test
    fun `store IEP Level for a prisoner`() {
      // Given
      val bookingId = 3330000L
      val prisonerNumber = "A1234AC"
      val migrationRequest = syncPostRequest()
      prisonApiMockServer.stubGetPrisonerInfoByBooking(bookingId = bookingId, prisonerNumber = prisonerNumber, locationId = 77778L)
      prisonApiMockServer.stubIepLevels()

      // When
      val response = webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read", "write")))
        .bodyValue(migrationRequest)
        .exchange()

      // Then
      response.expectStatus().isCreated

      webTestClient.get().uri("/iep/reviews/booking/$bookingId")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
            {
             "bookingId":$bookingId,
             "daysSinceReview":0,
             "iepDate":"${migrationRequest.iepTime.toLocalDate()}",
             "iepLevel":"Standard",
             "nextReviewDate":"${migrationRequest.iepTime.toLocalDate().plusYears(1)}",
             "iepDetails":[
                {
                   "bookingId":$bookingId,
                   "iepDate":"${migrationRequest.iepTime.toLocalDate()}",
                   "agencyId":"${migrationRequest.prisonId}",
                   "iepLevel":"Standard",
                   "iepCode": "STD",
                   "comments":"${migrationRequest.comment}",
                   "userId":"${migrationRequest.userId}",
                   "reviewType":"${migrationRequest.reviewType}",
                   "auditModuleName":"INCENTIVES_API"
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
        .bodyValue(syncPostRequest())
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `handle bad payload`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          // language=json
          """{
            "iepTime": "${LocalDateTime.now()}",
            "prisonId": "MDI",
            "iepLevel": "Standard",
            "comment": "A comment",
            "userId": "XYZ_GEN",
            "reviewType": "MIGRATED",
            "current": true
          }"""
        )
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("userMessage").value<String> {
          assertThat(it).contains("Invalid parameters: `iepLevel` must have length of at most 6")
        }
    }

    @Test
    fun `fails without write scope`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_IEP"), scopes = listOf("read")))
        .bodyValue(syncPostRequest())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `fails without correct role`() {
      val bookingId = 3330000L

      webTestClient.post().uri("/iep/migration/booking/$bookingId")
        .headers(setAuthorisation(roles = listOf("ROLE_DUMMY"), scopes = listOf("read", "write")))
        .bodyValue(syncPostRequest())
        .exchange()
        .expectStatus().isForbidden
    }

    private fun syncPostRequest(iepLevel: String = "STD") = SyncPostRequest(
      iepTime = LocalDateTime.now(),
      prisonId = "MDI",
      iepLevel = iepLevel,
      comment = "A comment",
      userId = "XYZ_GEN",
      reviewType = ReviewType.MIGRATED,
      current = true,
    )
  }
}
