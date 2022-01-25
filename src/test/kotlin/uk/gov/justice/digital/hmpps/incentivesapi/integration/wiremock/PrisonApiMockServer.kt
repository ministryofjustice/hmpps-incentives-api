package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8093
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubIEPLevels(prisonId: String) {
    stubFor(
      get("/api/agencies/$prisonId/iepLevels").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "iepLevel": "BAS",
                  "iepDescription": "Basic"
                },
                {
                  "iepLevel": "STD",
                  "iepDescription": "Standard"
                },
                {
                  "iepLevel": "ENT",
                  "iepDescription": "Entry"
                },
                {
                  "iepLevel": "ENH",
                  "iepDescription": "Enhanced"
                }
              ]
            """.trimIndent()
          )
      )
    )
  }

  fun stubPrisonLocations(prisonId: String) {
    stubFor(
      get("/api/agencies/$prisonId/locations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "agencyId": "$prisonId",
                  "description": "1",
                  "locationPrefix": "MDI-1",
                  "locationType": "WING",
                  "userDescription": "Houseblock 1"
                },
                {
                  "agencyId": "$prisonId",
                  "description": "2",
                  "locationPrefix": "MDI-2",
                  "locationType": "WING",
                  "userDescription": "Houseblock 2"
                },
                {
                  "agencyId": "$prisonId",
                  "description": "3",
                  "locationPrefix": "MDI-3",
                  "locationType": "WING",
                  "userDescription": "Houseblock 3"
                },
                {
                  "agencyId": "$prisonId",
                  "description": "4",
                  "locationPrefix": "MDI-4",
                  "locationType": "WING",
                  "userDescription": "Houseblock 4"
                }
              ]
            """.trimIndent()
          )
      )
    )
  }

  fun stubPrisonersOnWing(locationId: String) {
    stubFor(
      get("/api/locations/description/$locationId/inmates?returnIep=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-1",
                  "bookingId": 1234134,
                  "bookingNo": "A12121",
                  "facialImageId": 1241241,
                  "firstName": "JOHN",
                  "iepLevel": "Basic",
                  "lastName": "SMITH",
                  "offenderNo": "A1234AA"
                },
                  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-2",
                  "bookingId": 1234135,
                  "bookingNo": "A12122",
                  "facialImageId": 1241242,
                  "firstName": "DAVID",
                  "iepLevel": "Standard",
                  "lastName": "WHITE",
                  "offenderNo": "A1234AB"
                },
                  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-3",
                  "bookingId": 1234136,
                  "bookingNo": "A12123",
                  "facialImageId": 1241243,
                  "firstName": "TREVOR",
                  "iepLevel": "Enhanced",
                  "lastName": "LEE",
                  "offenderNo": "A1234AC"
                },  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-4",
                  "bookingId": 1234137,
                  "bookingNo": "A12124",
                  "facialImageId": 1241244,
                  "firstName": "ANTHONY",
                  "iepLevel": "Basic",
                  "lastName": "DAVIES",
                  "offenderNo": "A1234AD"
                },  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-5",
                  "bookingId": 1234138,
                  "bookingNo": "A12125",
                  "facialImageId": 1241245,
                  "firstName": "PAUL",
                  "iepLevel": "Standard",
                  "lastName": "RUDD",
                  "offenderNo": "A1234AE"
                }
              ]
            """.trimIndent()
          )
      )
    )
  }

  fun stubIEPSummary() {
    stubFor(
      post("/api/bookings/iepSummary").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "bookingId": 1234134,
                  "iepLevel": "Basic",
                  "daysSinceReview": 35,
                  "iepDate": "2021-12-02T00:00:00",
                  "iepTime": "2021-12-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234134,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Basic",
                      "iepTime": "2021-12-02T09:24:42.894Z"
                    }
                  ]
                },
                       {
                  "bookingId": 1234135,
                  "iepLevel": "Standard",
                  "daysSinceReview": 50,
                  "iepDate": "2021-12-02T00:00:00",
                  "iepTime": "2021-12-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234135,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Standard",
                      "iepTime": "2021-12-02T09:24:42.894Z"
                    }
                  ]
                },
                       {
                  "bookingId": 1234136,
                  "iepLevel": "Enhanced",
                  "daysSinceReview": 12,
                  "iepDate": "2021-12-02T00:00:00",
                  "iepTime": "2021-12-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234136,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Enhanced",
                      "iepTime": "2021-12-02T09:24:42.894Z"
                    }
                  ]
                },
                       {
                  "bookingId": 1234137,
                  "iepLevel": "Basic",
                  "daysSinceReview": 2,
                  "iepDate": "2021-12-02T00:00:00",
                  "iepTime": "2021-12-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234137,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Basic",
                      "iepTime": "2021-12-02T09:24:42.894Z"
                    }
                  ]
                },
                       {
                  "bookingId": 1234138,
                  "iepLevel": "Standard",
                  "daysSinceReview": 6,
                  "iepDate": "2021-12-02T00:00:00",
                  "iepTime": "2021-12-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234138,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Standard",
                      "iepTime": "2021-12-02T09:24:42.894Z"
                    }
                  ]
                }
              ]
            """.trimIndent()
          )
      )
    )
  }

  fun stubPositiveCaseNoteSummary() {
    stubFor(
      post("/api/case-notes/usage").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "POS_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "offenderNo": "A1234AA"
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "IEP_ENC",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "offenderNo": "A1234AA"
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "QUAL_ATT",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "offenderNo": "A1234AB"
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "POS_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "offenderNo": "A1234AC"
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "IEP_ENC",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "offenderNo": "A1234AD"
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "POS_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 5,
                  "offenderNo": "A1234AE"
                }
              ]
            """.trimIndent()
          )
      )
    )
  }
  fun stubNegativeCaseNoteSummary() {
    stubFor(
      post("/api/case-notes/usage").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              [
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "BEHAVEWARN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "offenderNo": "A1234AA"
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "IEP_WARN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "offenderNo": "A1234AA"
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "WORKWARN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "offenderNo": "A1234AB"
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "NEG_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "offenderNo": "A1234AC"
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "NEG_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "offenderNo": "A1234AD"
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "NEG_GEN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 5,
                  "offenderNo": "A1234AE"
                }
              ]
            """.trimIndent()
          )
      )
    )
  }

  fun stubProvenAdj() {
    stubFor(
      post("/api/bookings/proven-adjudications").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
                    [
                      {
                        "bookingId": 1234134,
                        "provenAdjudicationCount": 3
                      },
                      {
                        "bookingId": 1234135,
                        "provenAdjudicationCount": 1
                      },
                      {
                        "bookingId": 1234136,
                        "provenAdjudicationCount": 4
                      },
                      {
                        "bookingId": 1234137,
                        "provenAdjudicationCount": 2
                      }
                    ]
            """.trimIndent()
          )
      )
    )
  }
}
