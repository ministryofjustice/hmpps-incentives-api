package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.time.ZoneId

class IncentiveSummaryMasteredResourceTest : SqsIntegrationTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock {
      return Clock.fixed(
        Instant.parse("2022-01-07T15:30:00.000Z"),
        ZoneId.systemDefault()
      )
    }
  }

  @Autowired
  private lateinit var repository: PrisonerIepLevelRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetAll()

    // Prisoner A1234AA has 2 incentive entries current BAS
    prisonerIepLevel(
      bookingId = 1234134,
      prisonerNumber = "A1234AA",
      level = "BAS",
      reviewTime = LocalDateTime.of(2021, 12, 2, 9, 24, 42),
      current = true
    )

    prisonerIepLevel(
      bookingId = 1234134,
      prisonerNumber = "A1234AA",
      level = "ENT",
      reviewType = ReviewType.INITIAL,
      reviewTime = LocalDateTime.of(2021, 11, 2, 9, 24, 42),
      current = false,
      prisonId = "MDI"
    )

    // Prisoner A1234AB has 1 incentive entry current STD from entering prison
    prisonerIepLevel(
      bookingId = 1234135,
      prisonerNumber = "A1234AB",
      reviewType = ReviewType.INITIAL,
      reviewTime = LocalDateTime.of(2022, 1, 2, 9, 9, 24, 42),
      current = true
    )

    // Prisoner A1234AC as 2 incentive entry current ENH,
    prisonerIepLevel(
      bookingId = 1234136,
      prisonerNumber = "A1234AC",
      reviewType = ReviewType.REVIEW,
      level = "ENH",
      reviewTime = LocalDateTime.of(2021, 10, 2, 9, 24, 42)
    )
    prisonerIepLevel(
      bookingId = 1234136,
      prisonerNumber = "A1234AC",
      reviewType = ReviewType.INITIAL,
      reviewTime = LocalDateTime.of(2021, 9, 9, 2, 9, 24, 42)
    )

    // Prisoner A1234AD as 3 incentive entry current BASic,
    prisonerIepLevel(
      bookingId = 1234137,
      prisonerNumber = "A1234AD",
      reviewType = ReviewType.TRANSFER,
      level = "BAS",
      reviewTime = LocalDateTime.of(2022, 1, 7, 2, 9, 24, 42),
      prisonId = "MDI"
    )

    prisonerIepLevel(
      bookingId = 1234137,
      prisonerNumber = "A1234AD",
      reviewType = ReviewType.REVIEW,
      level = "BAS",
      reviewTime = LocalDateTime.of(2021, 12, 2, 9, 24, 42),
      prisonId = "LEI"
    )

    prisonerIepLevel(
      bookingId = 1234137,
      prisonerNumber = "A1234AD",
      level = "STD",
      reviewType = ReviewType.MIGRATED,
      reviewTime = LocalDateTime.of(2020, 7, 2, 9, 24, 42),
      prisonId = "LEI"
    )

    // Prisoner A1234AE as 1 incentive entry current Standard,
    prisonerIepLevel(
      bookingId = 1234138,
      prisonerNumber = "A1234AE",
      reviewType = ReviewType.REVIEW,
      reviewTime = LocalDateTime.of(2021, 12, 2, 9, 24, 42),
      prisonId = "MDI"
    )

    // Prisoner A1934AA as 2 incentive entry current Entry (Invalid),
    prisonerIepLevel(
      bookingId = 2734134,
      prisonerNumber = "A1934AA",
      reviewType = ReviewType.MIGRATED,
      level = "ENT",
      reviewTime = LocalDateTime.of(2022, 1, 2, 9, 24, 42),
      prisonId = "MDI"
    )
    prisonerIepLevel(
      bookingId = 2734134,
      prisonerNumber = "A1934AA",
      reviewType = ReviewType.MIGRATED,
      reviewTime = LocalDateTime.of(2021, 11, 2, 9, 24, 42),
      prisonId = "MDI"
    )
  }

  suspend fun prisonerIepLevel(
    bookingId: Long,
    prisonerNumber: String,
    level: String = "STD",
    reviewType: ReviewType = ReviewType.REVIEW,
    reviewTime: LocalDateTime = now(),
    current: Boolean = false,
    prisonId: String = "MDI"
  ) = repository.save(
    PrisonerIepLevel(
      bookingId = bookingId,
      prisonerNumber = prisonerNumber,
      reviewTime = reviewTime,
      prisonId = prisonId,
      iepCode = level,
      reviewType = reviewType,
      current = current,
      locationId = "1-1-002",
      commentText = "test comment",
      reviewedBy = "TEST_USER",
    )
  )

  @Test
  fun `get behaviour summary for wing`() {

    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels("MDI")
    offenderSearchMockServer.stubFindOffenders("MDI", includeInvalid = true)
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()
    prisonApiMockServer.stubProvenAdj()
    prisonApiMockServer.stubLocation("MDI-1")

    webTestClient.get().uri("incentives-summary/prison/MDI/location/MDI-1?sortBy=DAYS_ON_LEVEL&sortDirection=DESC")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
        {
           "prisonId":"MDI",
           "locationId":"MDI-1",
           "locationDescription":"Houseblock 1",
           "incentiveLevelSummary":[
              {
                 "level":"BAS",
                 "levelDescription":"Basic",
                 "prisonerBehaviours":[
                    {
                       "prisonerNumber":"A1234AA",
                       "bookingId":1234134,
                       "firstName":"John",
                       "lastName":"Smith",
                       "daysOnLevel":36,
                       "daysSinceLastReview":36,
                       "positiveBehaviours":3,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":3,
                       "incentiveWarnings":0,
                       "provenAdjudications":3
                    },
                    {
                       "prisonerNumber":"A1234AD",
                       "bookingId":1234137,
                       "firstName":"Anthony",
                       "lastName":"Davies",
                       "daysOnLevel":36,
                       "daysSinceLastReview":36,
                       "positiveBehaviours":1,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":1,
                       "incentiveWarnings":0,
                       "provenAdjudications":2
                    }
                 ],
                 "numberAtThisLevel":2
              },
              {
                 "level":"STD",
                 "levelDescription":"Standard",
                 "prisonerBehaviours":[
                    {
                       "prisonerNumber":"A1234AE",
                       "bookingId":1234138,
                       "firstName":"Paul",
                       "lastName":"Rudd",
                       "daysOnLevel":36,
                       "daysSinceLastReview":36,
                       "positiveBehaviours":5,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":5,
                       "incentiveWarnings":0,
                       "provenAdjudications":0
                    },
                    {
                       "prisonerNumber":"A1234AB",
                       "bookingId":1234135,
                       "firstName":"David",
                       "lastName":"White",
                       "daysOnLevel":5,
                       "daysSinceLastReview":5,
                       "positiveBehaviours":3,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":3,
                       "incentiveWarnings":0,
                       "provenAdjudications":1
                    }
                 ],
                 "numberAtThisLevel":2
              },
              {
                 "level":"ENH",
                 "levelDescription":"Enhanced",
                 "prisonerBehaviours":[
                    {
                       "prisonerNumber":"A1234AC",
                       "bookingId":1234136,
                       "firstName":"Trevor",
                       "lastName":"Lee",
                       "daysOnLevel":97,
                       "daysSinceLastReview":97,
                       "positiveBehaviours":2,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":2,
                       "incentiveWarnings":0,
                       "provenAdjudications":4
                    }
                 ],
                 "numberAtThisLevel":1
              },
              {
                 "level":"INV",
                 "levelDescription":"Invalid",
                 "prisonerBehaviours":[
                    {
                       "prisonerNumber":"A1934AA",
                       "bookingId":2734134,
                       "firstName":"Old",
                       "lastName":"Entry",
                       "daysOnLevel":5,
                       "daysSinceLastReview":5,
                       "positiveBehaviours":0,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":0,
                       "incentiveWarnings":0,
                       "provenAdjudications":0
                    }
                 ],
                 "numberAtThisLevel":1
              },
              {
                 "level":"MIS",
                 "levelDescription":"No Review",
                 "prisonerBehaviours":[
                    {
                       "prisonerNumber":"A1834AA",
                       "bookingId":2234134,
                       "firstName":"Missing",
                       "lastName":"Iep",
                       "daysOnLevel":0,
                       "daysSinceLastReview":0,
                       "positiveBehaviours":0,
                       "incentiveEncouragements":0,
                       "negativeBehaviours":0,
                       "incentiveWarnings":0,
                       "provenAdjudications":0
                    }
                 ],
                 "numberAtThisLevel":1
              }
           ],
           "averageDaysSinceLastReview":30,
           "totalIncentiveEncouragements":0,
           "totalNumberOfPrisoners":7,
           "averageDaysOnLevel":30,
           "totalPositiveBehaviours":14,
           "totalNegativeBehaviours":14,
           "totalIncentiveWarnings":0
        }
         """
      )
  }
}
