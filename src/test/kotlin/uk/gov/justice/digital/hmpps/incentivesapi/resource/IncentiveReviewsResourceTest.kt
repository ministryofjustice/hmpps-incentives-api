package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.helper.expectErrorResponse
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.IncentiveReviewSort
import java.time.LocalDateTime

@DisplayName("Incentive reviews resource")
class IncentiveReviewsResourceTest : IncentiveLevelResourceTestBase() {
  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  private val timeNow: LocalDateTime = LocalDateTime.now()

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    locationsMockServer.resetAll()
    prisonerSearchMockServer.resetAll()
    prisonApiMockServer.resetAll()

    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    // Prisoners on Standard and not overdue
    persistPrisonerIepLevel(
      bookingId = 1234134,
      prisonerNumber = "A1234AA",
      iepCode = "STD",
      iepTime = timeNow.minusDays(2),
    )
    persistPrisonerIepLevel(
      bookingId = 1234135,
      prisonerNumber = "A1234AB",
      iepCode = "STD",
      iepTime = timeNow.minusDays(1),
    )
    persistPrisonerIepLevel(bookingId = 1234136, prisonerNumber = "A1234AC", iepCode = "STD", iepTime = timeNow)
    // A prisoner on Basic, also not overdue
    persistPrisonerIepLevel(
      bookingId = 1234137,
      prisonerNumber = "A1234AD",
      iepCode = "BAS",
      iepTime = timeNow.minusDays(3),
    )
    // A prisoner on Enhanced and overdue
    persistPrisonerIepLevel(
      bookingId = 1234138,
      prisonerNumber = "A1234AE",
      iepCode = "ENH",
      iepTime = timeNow.minusYears(2),
    )
  }

  private suspend fun persistPrisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
    iepCode: String,
    iepTime: LocalDateTime,
  ) = incentiveReviewRepository.save(
    IncentiveReview(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = iepTime,
      prisonId = "MDI",
      levelCode = iepCode,
      reviewType = ReviewType.REVIEW,
      current = true,
      commentText = "test comment",
      reviewedBy = "TEST_USER",
    ),
  )

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    incentiveReviewRepository.deleteAll()
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

  @DisplayName("validates request parameters")
  @Nested
  inner class Validation {
    @BeforeEach
    fun setUp() {
      prisonApiMockServer.stubCaseNoteSummary()
    }

    @Test
    fun `when prisonId is incorrect`() {
      prisonerSearchMockServer.stubFindPrisoners("Moorland")
      locationsMockServer.stubGetByKey("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/Moorland/location/MDI-1/level/STD")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `prisonId` must have length of at most 5")
    }

    @Test
    fun `when level code is incorrect`() {
      prisonerSearchMockServer.stubFindPrisoners("Moorland")
      locationsMockServer.stubGetByKey("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/s")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `levelCode` must have length of at least 2")
    }

    @Test
    fun `when sorting is incorrect`() {
      prisonerSearchMockServer.stubFindPrisoners("MDI")
      locationsMockServer.stubGetByKey("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISON")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request format")
    }

    @Test
    fun `when page is incorrect`() {
      prisonerSearchMockServer.stubFindPrisoners("MDI")
      locationsMockServer.stubGetByKey("MDI-1")

      webTestClient.get()
        .uri("/incentives-reviews/prison/MDI/location/MDI-1/level/STD?page=-1")
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameters: `page` must be at least 0")
    }

    @Test
    fun `when page & pageSize are incorrect`() {
      prisonerSearchMockServer.stubFindPrisoners("MDI")
      locationsMockServer.stubGetByKey("MDI-1")

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
  fun `loads prisoner details from prisoner search and prison api`() {
    prisonerSearchMockServer.stubFindPrisoners("MDI")
    locationsMockServer.stubGetByKey("MDI-1")
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
        JsonCompareMode.STRICT,
      )
  }

  @Test
  fun `loads prisoner details even when location description not found`() {
    prisonerSearchMockServer.stubFindPrisoners("MDI")
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
        JsonCompareMode.STRICT,
      )
  }

  @ParameterizedTest
  @EnumSource(IncentiveReviewSort::class)
  fun `sorts by provided parameters`(sort: IncentiveReviewSort): Unit = runBlocking {
    prisonerSearchMockServer.stubFindPrisoners("MDI")
    locationsMockServer.stubGetByKey("MDI-1")
    prisonApiMockServer.stubCaseNoteSummary()

    if (sort == IncentiveReviewSort.IS_NEW_TO_PRISON) {
      // convert one prisoner to be "new to prison" so that sorting is possible
      var prisonerIepLevel =
        incentiveReviewRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(1234136)).first()
      prisonerIepLevel = prisonerIepLevel.copy(reviewType = ReviewType.INITIAL)
      incentiveReviewRepository.save(prisonerIepLevel)
    }

    fun loadReviewsField(
      sortParam: String,
      orderParam: String,
      responseField: String,
    ) = webTestClient.get()
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

  @DisplayName("can paginate")
  @Nested
  inner class Pagination {
    @BeforeEach
    fun setUp() {
      prisonerSearchMockServer.stubFindPrisoners("MDI")
      locationsMockServer.stubGetByKey("MDI-1")
      prisonApiMockServer.stubCaseNoteSummary()
    }

    private fun loadPage(page: Int) = webTestClient.get()
      .uri(
        "/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISONER_NUMBER&order=DESC&page=$page&pageSize=1",
      )
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
        .uri(
          "/incentives-reviews/prison/MDI/location/MDI-1/level/STD?sort=PRISONER_NUMBER&order=DESC&page=$page&pageSize=2",
        )
        .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
        .exchange()
        .expectErrorResponse(HttpStatus.BAD_REQUEST, "Page number is out of range")
    }
  }

  @Test
  fun `describes error when incentive levels not available in DB`(): Unit = runBlocking {
    prisonerSearchMockServer.stubFindPrisoners("MDI")
    locationsMockServer.stubGetByKey("MDI-1")
    prisonApiMockServer.stubCaseNoteSummary()

    incentiveReviewRepository.deleteAll()

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
