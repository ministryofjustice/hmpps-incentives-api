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

  fun stubLocation(locationId: String) {
    stubFor(
      get("/api/locations/code/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
                {
                  "agencyId": "MDI",
                  "description": "Houseblock 1",
                  "locationPrefix": "$locationId",
                  "locationType": "WING",
                  "userDescription": "Houseblock 1"
                }
            """.trimIndent()
          )
      )
    )
  }

  fun stubPrisonersOnWing(locationId: String) {
    stubFor(
      get("/api/locations/description/$locationId/inmates").willReturn(
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
                  "lastName": "LEE",
                  "offenderNo": "A1234AC"
                },  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-4",
                  "bookingId": 1234137,
                  "bookingNo": "A12124",
                  "facialImageId": 1241244,
                  "firstName": "ANTHONY",
                  "lastName": "DAVIES",
                  "offenderNo": "A1234AD"
                },  {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-5",
                  "bookingId": 1234138,
                  "bookingNo": "A12125",
                  "facialImageId": 1241245,
                  "firstName": "PAUL",
                  "lastName": "RUDD",
                  "offenderNo": "A1234AE"
                },
                {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-6",
                  "bookingId": 2234134,
                  "bookingNo": "B12121",
                  "facialImageId": 1541241,
                  "firstName": "MISSING",
                  "lastName": "IEP",
                  "offenderNo": "A1834AA"
                },
                {
                  "agencyId": "MDI",
                  "assignedLivingUnitDesc": "$locationId-1-7",
                  "bookingId": 2734134,
                  "bookingNo": "B12122",
                  "facialImageId": 1241243,
                  "firstName": "OLD",
                  "lastName": "ENTRY",
                  "offenderNo": "A1934AA"
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
                      "sequence": 2,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Basic",
                      "iepTime": "2021-12-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
                    },
                     {
                      "agencyId": "MDI",
                      "bookingId": 1234134,
                      "sequence": 1,
                      "iepDate": "2020-11-02",
                      "iepLevel": "Entry",
                      "iepTime": "2021-11-02T09:00:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
                    }
                  ]
                },
                {
                  "bookingId": 1234135,
                  "iepLevel": "Standard",
                  "daysSinceReview": 50,
                  "iepDate": "2022-01-02T00:00:00",
                  "iepTime": "2022-01-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 1234135,
                      "sequence": 1,
                      "iepDate": "2022-01-02",
                      "iepLevel": "Standard",
                      "iepTime": "2022-01-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
                    }
                  ]
                },
                 {
                  "bookingId": 1234136,
                  "iepLevel": "Enhanced",
                  "daysSinceReview": 12,
                  "iepDate": "2021-10-02T00:00:00",
                  "iepTime": "2021-10-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "bookingId": 1234136,
                      "sequence": 1,
                      "agencyId": "MDI",
                      "iepDate": "2021-10-02",
                      "iepLevel": "Enhanced",
                      "iepTime": "2021-10-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
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
                      "sequence": 1,
                      "iepDate": "2021-12-02",
                      "iepLevel": "Basic",
                      "iepTime": "2021-12-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
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
                      "sequence": 1,  
                      "iepDate": "2021-12-02",
                      "iepLevel": "Standard",
                      "iepTime": "2021-12-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
                    }
                  ]
                },
                {
                  "bookingId": 2734134,
                  "iepLevel": "Entry",
                  "daysSinceReview": 1,
                  "iepDate": "2022-01-02T00:00:00",
                  "iepTime": "2022-01-02T09:24:42.894Z",
                  "iepDetails": [
                    {
                      "agencyId": "MDI",
                      "bookingId": 2734134,
                      "sequence": 1,  
                      "iepDate": "2022-01-02",
                      "iepLevel": "Entry",
                      "iepTime": "2022-01-02T09:24:42.894Z",
                      "comment": "A new Review",
                      "userId": "TEST_USER",
                      "auditModuleName": "PRISON_API"
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

  fun stubAddIep(bookingId: Long) {
    stubFor(
      post("/api/bookings/$bookingId/iepLevels").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204)
      )
    )
  }

  fun stubGetPrisonerInfoByNoms(prisonerNumber: String, bookingId: Long, locationId: Long) {
    stubFor(
      get("/api/bookings/offenderNo/$prisonerNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
                  {
                  "agencyId": "MDI",
                  "assignedLivingUnitId": $locationId,
                  "bookingId": $bookingId,
                  "bookingNo": "A12121",
                  "firstName": "JOHN",
                  "lastName": "SMITH",
                  "offenderNo": "$prisonerNumber"
                }
            """.trimIndent()
          )
      )
    )
  }

  fun stubGetPrisonerInfoByBooking(bookingId: Long, prisonerNumber: String, locationId: Long) {
    stubFor(
      get("/api/bookings/$bookingId?basicInfo=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
                    {
                  "agencyId": "MDI",
                  "assignedLivingUnitId": $locationId,
                  "bookingId": $bookingId,
                  "bookingNo": "A12121",
                  "firstName": "JOHN",
                  "lastName": "SMITH",
                  "offenderNo": "$prisonerNumber"
                }
            """.trimIndent()
          )
      )
    )
  }

  fun stubGetLocationById(locationId: Long, locationDesc: String) {
    stubFor(
      get("/api/locations/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
                {
                  "agencyId": "MDI",
                  "locationId": $locationId,
                  "description": "$locationDesc",
                  "locationType": "CELL"
                }
            """.trimIndent()
          )
      )
    )
  }
}
