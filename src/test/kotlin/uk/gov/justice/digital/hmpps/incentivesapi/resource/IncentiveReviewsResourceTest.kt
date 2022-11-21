package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewSort
import java.time.LocalDateTime

class IncentiveReviewsResourceTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var prisonerIepLevelRepository: PrisonerIepLevelRepository
  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    offenderSearchMockServer.resetAll()
    prisonApiMockServer.resetAll()

    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    persistPrisonerIepLevel(bookingId = 1234134, prisonerNumber = "A1234AA")
    persistPrisonerIepLevel(bookingId = 1234135, prisonerNumber = "A1234AB")
    persistPrisonerIepLevel(bookingId = 1234136, prisonerNumber = "A1234AC")
    persistPrisonerIepLevel(bookingId = 1234137, prisonerNumber = "A1234AD")
    persistPrisonerIepLevel(bookingId = 1234138, prisonerNumber = "A1234AE")
  }

  private val iepTime: LocalDateTime = LocalDateTime.now()

  private suspend fun persistPrisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
  ) = prisonerIepLevelRepository.save(
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
    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
  }

  @Test
  fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `requires correct role to retrieve data`() {
    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = emptyList()))
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Nested
  inner class `validates request parameters` {
    @BeforeEach
    fun setUp() {
      prisonApiMockServer.stubPositiveCaseNoteSummary()
      prisonApiMockServer.stubNegativeCaseNoteSummary()
      prisonApiMockServer.stubIepLevels()
    }

    @Test
    fun `when prisonId is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("Moorland")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/Moorland/location/MDI-1/level/STD")
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
    fun `when level code is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("Moorland")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/s")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `levelCode` must have length of at least 2",
            "developerMessage": "Invalid parameters: `levelCode` must have length of at least 2"
          }
          """
        )
    }

    @Test
    fun `when sorting is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISON")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("status").isEqualTo(400)
        .jsonPath("userMessage").value<String> {
          assertThat(it).contains("No enum constant")
        }
    }

    @Test
    fun `when page is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?page=-1")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `page` must be at least 0",
            "developerMessage": "Invalid parameters: `page` must be at least 0"
          }
          """
        )
    }

    @Test
    fun `when page & pageSize are incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?page=-1&pageSize=0")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().json(
          // language=json
          """
          {
            "status": 400,
            "userMessage": "Invalid parameters: `page` must be at least 0, `pageSize` must be at least 1",
            "developerMessage": "Invalid parameters: `page` must be at least 0, `pageSize` must be at least 1"
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
    prisonApiMockServer.stubIepLevels()

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "reviewCount": 5,
            "overdueCount": 0,
            "reviews": [
              {
                "prisonerNumber": "A1234AA",
                "bookingId": 1234134,
                "firstName": "John",
                "lastName": "Smith",
                "levelCode": "STD",
                "positiveBehaviours": 3,
                "negativeBehaviours": 3,
                "hasAcctOpen": true,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AB",
                "bookingId": 1234135,
                "firstName": "David",
                "lastName": "White",
                "levelCode": "STD",
                "positiveBehaviours": 3,
                "negativeBehaviours": 3,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AC",
                "bookingId": 1234136,
                "firstName": "Trevor",
                "lastName": "Lee",
                "levelCode": "STD",
                "positiveBehaviours": 2,
                "negativeBehaviours": 2,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AD",
                "bookingId": 1234137,
                "firstName": "Anthony",
                "lastName": "Davies",
                "levelCode": "STD",
                "positiveBehaviours": 1,
                "negativeBehaviours": 1,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AE",
                "bookingId": 1234138,
                "firstName": "Paul",
                "lastName": "Rudd",
                "levelCode": "STD",
                "positiveBehaviours": 5,
                "negativeBehaviours": 5,
                "hasAcctOpen": false,
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
    prisonApiMockServer.stubIepLevels()

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "reviewCount": 5,
            "overdueCount": 0,
            "reviews": [
              {
                "prisonerNumber": "A1234AA",
                "bookingId": 1234134,
                "firstName": "John",
                "lastName": "Smith",
                "levelCode": "STD",
                "positiveBehaviours": 3,
                "negativeBehaviours": 3,
                "hasAcctOpen": true,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AB",
                "bookingId": 1234135,
                "firstName": "David",
                "lastName": "White",
                "levelCode": "STD",
                "positiveBehaviours": 3,
                "negativeBehaviours": 3,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AC",
                "bookingId": 1234136,
                "firstName": "Trevor",
                "lastName": "Lee",
                "levelCode": "STD",
                "positiveBehaviours": 2,
                "negativeBehaviours": 2,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AD",
                "bookingId": 1234137,
                "firstName": "Anthony",
                "lastName": "Davies",
                "levelCode": "STD",
                "positiveBehaviours": 1,
                "negativeBehaviours": 1,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              },
              {
                "prisonerNumber": "A1234AE",
                "bookingId": 1234138,
                "firstName": "Paul",
                "lastName": "Rudd",
                "levelCode": "STD",
                "positiveBehaviours": 5,
                "negativeBehaviours": 5,
                "hasAcctOpen": false,
                "nextReviewDate": ${iepTime.toLocalDate().plusYears(1)}
              }
            ],
            "locationDescription": "Unknown location"
          }
        """,
        true,
      )
  }

  @ParameterizedTest
  @EnumSource(IncentiveReviewSort::class)
  fun `sorts by provided parameters`(sort: IncentiveReviewSort): Unit = runBlocking {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()
    prisonApiMockServer.stubIepLevels()

    // pre-cache different next review dates as `persistPrisonerIepLevel` defaults lead to all being today + 1 year
    nextReviewDateRepository.saveAll(
      listOf(
        NextReviewDate(bookingId = 1234134, nextReviewDate = iepTime.toLocalDate().plusDays(1)),
        NextReviewDate(bookingId = 1234135, nextReviewDate = iepTime.toLocalDate().plusDays(2)),
        NextReviewDate(bookingId = 1234136, nextReviewDate = iepTime.toLocalDate().plusDays(3)),
        NextReviewDate(bookingId = 1234137, nextReviewDate = iepTime.toLocalDate().plusDays(4)),
        NextReviewDate(bookingId = 1234138, nextReviewDate = iepTime.toLocalDate().plusDays(5)),
      )
    ).collect()

    fun loadReviewsField(sortParam: String, orderParam: String, responseField: String) = webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=$sortParam&order=$orderParam")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("reviews[*].$responseField")

    loadReviewsField(sort.name, "ASC", sort.field).value<List<Comparable<*>>> {
      assertThat(it.toSet()).hasSizeGreaterThan(1) // otherwise sorting cannot be tested
      assertThat(it).isSorted
    }

    loadReviewsField(sort.name, "DESC", sort.field).value<List<Comparable<*>>> {
      assertThat(it.toSet()).hasSizeGreaterThan(1) // otherwise sorting cannot be tested
      assertThat(it.reversed()).isSorted
    }
  }

  @Test
  fun `describes error when incentive levels not available in DB`(): Unit = runBlocking {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()
    prisonApiMockServer.stubIepLevels()

    prisonerIepLevelRepository.deleteAll()

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isNotFound
      .expectBody().json(
        // language=json
        """
          {
            "status": 404,
            "userMessage": "No incentive levels found for ID(s) [1234134, 1234135, 1234136, 1234137, 1234138]",
            "developerMessage": "No incentive levels found for ID(s) [1234134, 1234135, 1234136, 1234137, 1234138]"
          }
          """
      )
  }
}
