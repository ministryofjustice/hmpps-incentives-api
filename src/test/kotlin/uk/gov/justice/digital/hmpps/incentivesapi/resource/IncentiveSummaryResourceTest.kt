package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@ActiveProfiles("test", "test-nomis")
class IncentiveSummaryResourceTest : SqsIntegrationTestBase() {
  @TestConfiguration
  internal class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock {
      return Clock.fixed(
        Instant.parse("2022-01-07T15:30:00.000Z"),
        ZoneId.systemDefault()
      )
    }
  }
  @BeforeEach
  internal fun setUp() {
    prisonApiMockServer.resetAll()
  }

  private fun stubApis(prisonId: String, wingId: String = "1") {
    val locationId = "$prisonId-$wingId"
    prisonApiMockServer.stubIepLevels()
    prisonApiMockServer.stubAgenciesIepLevels(prisonId)
    offenderSearchMockServer.stubFindOffenders(prisonId, wingId, includeInvalid = true)
    prisonApiMockServer.stubIEPSummary()
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()
    prisonApiMockServer.stubProvenAdj()
    prisonApiMockServer.stubLocation(locationId)
  }

  @Test
  internal fun `requires a valid token to retrieve data`() {
    webTestClient.get()
      .uri("/incentives-summary/prison/MDI/location/MDI-1")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `get behaviour summary for wing`() {
    stubApis("MDI", "1")

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
                 "levelDescription":"Missing",
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
           "totalIncentiveWarnings":0,
           "totalPositiveBehaviours":14,
           "totalNegativeBehaviours":14,
           "averageDaysOnLevel":30,
           "totalNumberOfPrisoners":7,
           "averageDaysSinceLastReview":30,
           "totalIncentiveEncouragements":0
        }
         """
      )
  }

  @Test
  fun `returns an error if prisonId is incorrect`() {
    stubApis("Moorland", "1")

    webTestClient.get().uri("incentives-summary/prison/Moorland/location/MDI-1?sortBy=DAYS_ON_LEVEL&sortDirection=DESC")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("userMessage")
      .isEqualTo("Invalid parameters: `prisonId` must have length of at most 5")
  }

  @Test
  fun `returns an error if locationId is incorrect`() {
    stubApis("MDI", "MDI")

    webTestClient.get().uri("incentives-summary/prison/MDI/location/MDI?sortBy=DAYS_ON_LEVEL&sortDirection=DESC")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("userMessage")
      .isEqualTo("Invalid parameters: `locationId` must have length of at least 5")
  }
}
