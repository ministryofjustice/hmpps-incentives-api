package uk.gov.justice.digital.hmpps.incentivesapi.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase

class IncentiveSummaryResourceTest : SqsIntegrationTestBase() {
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
               "imageId":1241241,
               "firstName":"John",
               "lastName":"Smith",
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
               "imageId":1241243,
               "firstName":"Old",
               "lastName":"Entry",
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
               "imageId":1541241,
               "firstName":"Missing",
               "lastName":"Iep",
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
   "totalNumberOfPrisoners":7,
   "totalPositiveBehaviours":14,
   "totalNegativeBehaviours":14,
   "totalIncentiveEncouragements":0,
   "totalIncentiveWarnings":0
}
         """
      )
  }
}
