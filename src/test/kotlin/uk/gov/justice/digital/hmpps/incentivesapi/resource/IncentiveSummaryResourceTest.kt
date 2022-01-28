package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IntegrationTestBase

class IncentiveSummaryResourceTest : IntegrationTestBase() {
  @BeforeEach
  internal fun setUp() {
    prisonApiMockServer.resetAll()
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
    prisonApiMockServer.stubPrisonersOnWing("MDI-1")
    prisonApiMockServer.stubIEPSummary()
    prisonApiMockServer.stubPositiveCaseNoteSummary()
    prisonApiMockServer.stubNegativeCaseNoteSummary()
    prisonApiMockServer.stubProvenAdj()
    prisonApiMockServer.stubIEPLevels("MDI")
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
   "averageDaysOnLevel":93,
   "averageDaysSinceLastReview":21,
   "totalNumberOfPrisoners":5,
   "totalPositiveBehaviours":14,
   "totalIncentiveWarnings":0,
   "totalNegativeBehaviours":14,
   "totalIncentiveEncouragements":0,

   "incentiveLevelSummary":[
      {
         "level":"ENT",
         "levelDescription":"Entry",
         "prisonerBehaviours":[],
         "numberAtThisLevel":0
      },
      {
         "level":"BAS",
         "levelDescription":"Basic",
         "prisonerBehaviours":[
            {
               "prisonerNumber":"A1234AA",
               "bookingId":1234134,
               "imageId":1241241,
               "firstName":"John",
               "lastName":"Smith",
               "daysOnLevel":399,
               "daysSinceLastReview":35,
               "positiveBehaviours":3,
               "incentiveEncouragements":0,
               "negativeBehaviours":3,
               "incentiveWarnings":0,
               "provenAdjudications":3
            },
            {
               "prisonerNumber":"A1234AD",
               "bookingId":1234137,
               "imageId":1241244,
               "firstName":"Anthony",
               "lastName":"Davies",
               "daysOnLevel":1,
               "daysSinceLastReview":2,
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
               "prisonerNumber":"A1234AB",
               "bookingId":1234135,
               "imageId":1241242,
               "firstName":"David",
               "lastName":"White",
               "daysOnLevel":49,
               "daysSinceLastReview":50,
               "positiveBehaviours":3,
               "incentiveEncouragements":0,
               "negativeBehaviours":3,
               "incentiveWarnings":0,
               "provenAdjudications":1
            },
            {
               "prisonerNumber":"A1234AE",
               "bookingId":1234138,
               "imageId":1241245,
               "firstName":"Paul",
               "lastName":"Rudd",
               "daysOnLevel":5,
               "daysSinceLastReview":6,
               "positiveBehaviours":5,
               "incentiveEncouragements":0,
               "negativeBehaviours":5,
               "incentiveWarnings":0,
               "provenAdjudications":0
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
               "imageId":1241243,
               "firstName":"Trevor",
               "lastName":"Lee",
               "daysOnLevel":11,
               "daysSinceLastReview":12,
               "positiveBehaviours":2,
               "incentiveEncouragements":0,
               "negativeBehaviours":2,
               "incentiveWarnings":0,
               "provenAdjudications":4
            }
         ],
         "numberAtThisLevel":1
      }
   ]
}
         """
      )
  }
}
