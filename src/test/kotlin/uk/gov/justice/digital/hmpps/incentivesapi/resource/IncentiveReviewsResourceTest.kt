package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewSort
import java.time.LocalDateTime

class IncentiveReviewsResourceTest : IncentiveLevelResourceTestBase() {
  @Autowired
  private lateinit var prisonerIepLevelRepository: PrisonerIepLevelRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  private val timeNow: LocalDateTime = LocalDateTime.now()

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    offenderSearchMockServer.resetAll()
    prisonApiMockServer.resetAll()

    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    // Prisoners on Standard and not overdue
    persistPrisonerIepLevel(bookingId = 1234134, prisonerNumber = "A1234AA", iepCode = "STD", iepTime = timeNow.minusDays(2))
    persistPrisonerIepLevel(bookingId = 1234135, prisonerNumber = "A1234AB", iepCode = "STD", iepTime = timeNow.minusDays(1))
    persistPrisonerIepLevel(bookingId = 1234136, prisonerNumber = "A1234AC", iepCode = "STD", iepTime = timeNow)
    // A prisoner on Basic, also not overdue
    persistPrisonerIepLevel(bookingId = 1234137, prisonerNumber = "A1234AD", iepCode = "BAS", iepTime = timeNow.minusDays(3))
    // A prisoner on Enhanced and overdue
    persistPrisonerIepLevel(bookingId = 1234138, prisonerNumber = "A1234AE", iepCode = "ENH", iepTime = timeNow.minusYears(2))
  }

  private suspend fun persistPrisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
    iepCode: String,
    iepTime: LocalDateTime,
  ) = prisonerIepLevelRepository.save(
    PrisonerIepLevel(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = iepTime,
      prisonId = "MDI",
      iepCode = iepCode,
      reviewType = ReviewType.REVIEW,
      current = true,
      locationId = "1-1-002",
      commentText = "test comment",
      reviewedBy = "TEST_USER",
    ),
  )

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    prisonerIepLevelRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    super.tearDown()
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
      prisonApiMockServer.stubCaseNoteSummary()
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
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `prisonId` must have length of at most 5")
    }

    @Test
    fun `when level code is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("Moorland")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/s")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `levelCode` must have length of at least 2")
    }

    @Test
    fun `when sorting is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISON")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "No enum constant")
    }

    @Test
    fun `when page is incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?page=-1")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `page` must be at least 0")
    }

    @Test
    fun `when page & pageSize are incorrect`() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?page=-1&pageSize=0")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(
          HttpStatus.BAD_REQUEST,
          "Invalid parameters: `page` must be at least 0, `pageSize` must be at least 1",
        )
    }
  }

  @Test
  fun `loads prisoner details from offender search and prison api`() {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubCaseNoteSummary()

    listOf("BAS", "STD", "ENH", "ENT").forEach { levelCode ->
      listOf("MDI").forEach { prisonId ->
        makePrisonIncentiveLevel(prisonId, levelCode)
      }
    }

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "levels": [
              {
                "levelCode": "BAS",
                "levelName": "Basic",
                "reviewCount": 1,
                "overdueCount": 0
              },
              {
                "levelCode": "STD",
                "levelName": "Standard",
                "reviewCount": 3,
                "overdueCount": 0
              },
              {
                "levelCode": "ENH",
                "levelName": "Enhanced",
                "reviewCount": 1,
                "overdueCount": 1
              }
            ],
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1).minusDays(2)},
                "daysSinceLastReview": 2
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1).minusDays(1)},
                "daysSinceLastReview": 1
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1)},
                "daysSinceLastReview": 0
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
    prisonApiMockServer.stubCaseNoteSummary()
    listOf("BAS", "STD", "ENH", "ENT").forEach { levelCode ->
      listOf("MDI").forEach { prisonId ->
        makePrisonIncentiveLevel(prisonId, levelCode)
      }
    }

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
          {
            "levels": [
              {
                "levelCode": "BAS",
                "levelName": "Basic",
                "reviewCount": 1,
                "overdueCount": 0
              },
              {
                "levelCode": "STD",
                "levelName": "Standard",
                "reviewCount": 3,
                "overdueCount": 0
              },
              {
                "levelCode": "ENH",
                "levelName": "Enhanced",
                "reviewCount": 1,
                "overdueCount": 1
              }
            ],
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1).minusDays(2)},
                "daysSinceLastReview": 2
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1).minusDays(1)},
                "daysSinceLastReview": 1
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
                "isNewToPrison": false,
                "nextReviewDate": ${timeNow.toLocalDate().plusYears(1)},
                "daysSinceLastReview": 0
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
    prisonApiMockServer.stubCaseNoteSummary()
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels("MDI")

    if (sort == IncentiveReviewSort.IS_NEW_TO_PRISON) {
      // convert one prisoner to be "new to prison" so that sorting is possible
      var prisonerIepLevel =
        prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(1234136)).first()
      prisonerIepLevel = prisonerIepLevel.copy(reviewType = ReviewType.INITIAL)
      prisonerIepLevelRepository.save(prisonerIepLevel)
    }

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

  @Nested
  inner class `can paginate` {
    @BeforeEach
    fun setUp() {
      offenderSearchMockServer.stubFindOffenders("MDI")
      prisonApiMockServer.stubLocation("MDI-1")
      prisonApiMockServer.stubCaseNoteSummary()
      prisonApiMockServer.stubIepLevels()
      prisonApiMockServer.stubAgenciesIepLevels("MDI")
    }

    private fun loadPage(page: Int) = webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISONER_NUMBER&order=DESC&page=$page&pageSize=1")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("reviews[*].prisonerNumber")

    @Test
    fun `first page`(): Unit = runBlocking {
      loadPage(0).value<List<String>> {
        assertThat(it).isEqualTo(listOf("A1234AC"))
      }
    }

    @Test
    fun `second page`(): Unit = runBlocking {
      loadPage(1).value<List<String>> {
        assertThat(it).isEqualTo(listOf("A1234AB"))
      }
    }

    @Test
    fun `last page`(): Unit = runBlocking {
      loadPage(2).value<List<String>> {
        assertThat(it).isEqualTo(listOf("A1234AA"))
      }
    }

    @Test
    fun `out-of-bounds page`(): Unit = runBlocking {
      val page = 3
      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISONER_NUMBER&order=DESC&page=$page&pageSize=2")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Page number is out of range")
    }
  }

  @Test
  fun `describes error when incentive levels not available in DB`(): Unit = runBlocking {
    offenderSearchMockServer.stubFindOffenders("MDI")
    prisonApiMockServer.stubLocation("MDI-1")
    prisonApiMockServer.stubCaseNoteSummary()
    prisonApiMockServer.stubIepLevels()

    prisonerIepLevelRepository.deleteAll()

    webTestClient.get()
      .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectErrorResponse(
        HttpStatus.NOT_FOUND,
        "No incentive levels found for ID(s) [1234134, 1234135, 1234136, 1234137, 1234138]",
      )
  }
}
