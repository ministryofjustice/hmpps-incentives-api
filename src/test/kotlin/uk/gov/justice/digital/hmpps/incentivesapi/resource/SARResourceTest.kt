package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.Clock
import java.time.LocalDateTime

class SARResourceTest : IncentiveLevelResourceTestBase() {
  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  private val timeNow: LocalDateTime = LocalDateTime.now(clock)
  private val nextReviewDate = timeNow.toLocalDate().plusYears(1)

  @BeforeEach
  fun setUp() {
    runBlocking {
      // set up 1 prisoner with 2 bookings
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
      nextReviewDateRepository.save(NextReviewDate(bookingId = 76, nextReviewDate = timeNow.minusYears(1).toLocalDate().plusDays(7)))
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
    val oldestReviewTime = timeNow.minusDays(6)
    webTestClient.get().uri("/subject-access-request?prn=A1234AA&fromDate=${oldestReviewTime.toLocalDate()}&toDate=${timeNow.minusDays(1).toLocalDate()}")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
            {
              "content": [
                {
                  "bookingId": 100,
                  "prisonerNumber": "A1234AA",
                  "nextReviewDate": "$nextReviewDate",
                  "levelCode": "STD",
                  "prisonId": "MDI",
                  "locationId": "1-1-002",
                  "reviewTime": "${timeNow.minusDays(2)}",
                  "reviewedBy": "TEST_USER",
                  "commentText": "test comment",
                  "current": false,
                  "reviewType": "REVIEW"
                },
                {
                  "bookingId": 100,
                  "prisonerNumber": "A1234AA",
                  "nextReviewDate": "$nextReviewDate",
                  "levelCode": "ENH",
                  "prisonId": "MDI",
                  "locationId": "1-1-002",
                  "reviewTime": "${timeNow.minusDays(4)}",
                  "reviewedBy": "TEST_USER",
                  "commentText": "test comment",
                  "current": false,
                  "reviewType": "REVIEW"
                },
                {
                  "bookingId": 100,
                  "prisonerNumber": "A1234AA",
                  "nextReviewDate": "$nextReviewDate",
                  "levelCode": "STD",
                  "prisonId": "MDI",
                  "locationId": "1-1-002",
                  "reviewTime": "$oldestReviewTime",
                  "reviewedBy": "TEST_USER",
                  "commentText": "test comment",
                  "current": false,
                  "reviewType": "REVIEW"
                }
              ]
            }         
          """,
      )
  }
}
