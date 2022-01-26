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

    webTestClient.get().uri("incentives-summary/prison/MDI/location/MDI-1")
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
         "level":"Entry",
         "prisonerBehaviours":[
            
         ],
         "numberAtThisLevel":0
      },
      {
         "level":"Basic",
         "prisonerBehaviours":[
            {
               "prisonerNumber":"A1234AA",
               "bookingId":1234134,
               "imageId":1241241,
               "firstName":"JOHN",
               "lastName":"SMITH",
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
               "firstName":"ANTHONY",
               "lastName":"DAVIES",
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
         "level":"Standard",
         "prisonerBehaviours":[
            {
               "prisonerNumber":"A1234AB",
               "bookingId":1234135,
               "imageId":1241242,
               "firstName":"DAVID",
               "lastName":"WHITE",
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
               "firstName":"PAUL",
               "lastName":"RUDD",
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
         "level":"Enhanced",
         "prisonerBehaviours":[
            {
               "prisonerNumber":"A1234AC",
               "bookingId":1234136,
               "imageId":1241243,
               "firstName":"TREVOR",
               "lastName":"LEE",
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
   ],
   "totalPositiveBehaviours":14,
   "totalNegativeBehaviours":14,
   "totalIncentiveEncouragements":0,
   "totalIncentiveWarnings":0
}
         """
      )
  }
}
