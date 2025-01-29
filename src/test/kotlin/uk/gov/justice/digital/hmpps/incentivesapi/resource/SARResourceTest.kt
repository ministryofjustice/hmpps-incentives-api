package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.Clock
import java.time.LocalDateTime

@DisplayName("SAR resource")
class SARResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock // "2022-03-15T12:34:56+00:00"
  }

  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  @BeforeEach
  fun setUp() {
    runBlocking {
      // set up 1 prisoner with 2 bookings

      val timeNow: LocalDateTime = LocalDateTime.now(clock)
      val nextReviewDate = timeNow.toLocalDate().plusYears(1)

      persistPrisonerIepLevel(
        bookingId = 100,
        prisonerNumber = "A1234AA",
        iepCode = "STD",
        iepTime = timeNow,
        current = true,
      )
      nextReviewDateRepository.save(NextReviewDate(bookingId = 100, nextReviewDate = nextReviewDate))
      persistPrisonerIepLevel(
        bookingId = 100,
        prisonerNumber = "A1234AA",
        iepCode = "STD",
        iepTime = timeNow.minusDays(2),
      )
      persistPrisonerIepLevel(
        bookingId = 100,
        prisonerNumber = "A1234AA",
        iepCode = "ENH",
        iepTime = timeNow.minusDays(4),
      )
      persistPrisonerIepLevel(
        bookingId = 100,
        prisonerNumber = "A1234AA",
        iepCode = "STD",
        iepTime = timeNow.minusDays(6),
      )
      persistPrisonerIepLevel(
        bookingId = 76,
        prisonerNumber = "A1234AA",
        iepCode = "BAS",
        iepTime = timeNow.minusYears(1),
        current = true,
      )
      nextReviewDateRepository.save(
        NextReviewDate(bookingId = 76, nextReviewDate = timeNow.minusYears(1).toLocalDate().plusDays(7)),
      )
    }
  }

  private suspend fun persistPrisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
    iepCode: String,
    iepTime: LocalDateTime,
    current: Boolean = false,
  ) = incentiveReviewRepository.save(
    IncentiveReview(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = iepTime,
      prisonId = "MDI",
      levelCode = iepCode,
      reviewType = ReviewType.REVIEW,
      current = current,
      locationId = "1-1-002",
      commentText = "test comment",
      reviewedBy = "TEST_USER",
      whenCreated = iepTime,
    ),
  )

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    super.tearDown()
  }

  @Test
  fun `get SAR content for a prisoner`() {
    webTestClient.get()
      .uri("/subject-access-request?prn=A1234AA")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "content": [
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "STD",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-15T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": true,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "STD",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-13T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "ENH",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-11T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "STD",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-09T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 76,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "BAS",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2021-03-15T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `get filtered SAR content for a prisoner`() {
    webTestClient.get()
      .uri("/subject-access-request?prn=A1234AA&fromDate=2022-03-09&toDate=2022-03-14")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "content": [
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "STD",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-13T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "ENH",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-11T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            },
            {
              "bookingId": 100,
              "prisonerNumber": "A1234AA",
              "nextReviewDate": "2023-03-15",
              "levelCode": "STD",
              "prisonId": "MDI",
              "locationId": "1-1-002",
              "reviewTime": "2022-03-09T12:34:56",
              "reviewedBy": "TEST_USER",
              "commentText": "test comment",
              "current": false,
              "reviewType": "REVIEW"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `get SAR for a prisoner with no Incentive reviews`() {
    val prisonerNumber = "A1111BB"

    webTestClient.get()
      .uri("/subject-access-request?prn=$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isNoContent
  }

  @Test
  fun `get filtered SAR content for a prisoner, no reviews matching`() {
    val prisonerNumber = "A1234AA"
    // No reviews that old
    val toDate = LocalDateTime.now(clock).minusYears(5).toLocalDate()

    webTestClient.get()
      .uri("/subject-access-request?prn=$prisonerNumber&toDate=$toDate")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isNoContent
  }
}
