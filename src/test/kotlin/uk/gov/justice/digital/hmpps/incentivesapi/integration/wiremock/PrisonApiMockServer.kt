package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8093
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()

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
            // language=json
            """
                {
                  "agencyId": "MDI",
                  "description": "Houseblock 1",
                  "locationPrefix": "$locationId",
                  "locationType": "WING",
                  "userDescription": "Houseblock 1"
                }
            """
          )
      )
    )
  }

  fun stubIepLevels() {
    stubFor(
      get("/api/reference-domains/domains/IEP_LEVEL/codes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
              [
                {
                  "domain": "IEP_LEVEL",
                  "code": "BAS",
                  "description": "Basic",
                  "listSeq": 1,
                  "activeFlag": "Y"
                },
                {
                  "domain": "IEP_LEVEL",
                  "code": "ENT",
                  "description": "Entry",
                  "listSeq": 2,
                  "activeFlag": "N"
                },
                {
                  "domain": "IEP_LEVEL",
                  "code": "STD",
                  "description": "Standard",
                  "listSeq": 3,
                  "activeFlag": "Y"
                },
                {
                  "domain": "IEP_LEVEL",
                  "code": "ENH",
                  "description": "Enhanced",
                  "listSeq": 4,
                  "activeFlag": "Y"
                },
                {
                  "domain": "IEP_LEVEL",
                  "code": "EN2",
                  "description": "Enhanced 2",
                  "listSeq": 5,
                  "activeFlag": "Y"
                },
                {
                  "domain": "IEP_LEVEL",
                  "code": "EN3",
                  "description": "Enhanced 3",
                  "listSeq": 6,
                  "activeFlag": "Y"
                }
              ]
            """
          )
      )
    )
  }

  fun stubAgenciesIepLevels(agencyId: String) {
    stubFor(
      get("/api/agencies/$agencyId/iepLevels").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
              [
                  {
                      "iepLevel": "BAS",
                      "iepDescription": "Basic",
                      "sequence": 1,
                      "defaultLevel": false
                  },
                  {
                      "iepLevel": "STD",
                      "iepDescription": "Standard",
                      "sequence": 3,
                      "defaultLevel": true
                  },
                  {
                      "iepLevel": "ENH",
                      "iepDescription": "Enhanced",
                      "sequence": 4,
                      "defaultLevel": false
                  }
              ]
            """
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
            // language=json
            """
              [
                {
                  "bookingId": 1234134,
                  "iepLevel": "Basic",
                  "daysSinceReview": 35,
                  "iepDate": "2021-12-02",
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
                  "iepDate": "2022-01-02",
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
                  "iepDate": "2021-10-02",
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
                  "iepDate": "2021-12-02",
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
                  "iepDate": "2021-12-02",
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
                  "iepDate": "2022-01-02",
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
            """
          )
      )
    )
  }

  fun stubIEPSummaryForBooking(bookingId: Long = 1234134) {
    stubFor(
      get("/api/bookings/$bookingId/iepSummary?withDetails=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
              {
                "bookingId": $bookingId,
                "iepLevel": "Basic",
                "daysSinceReview": 35,
                "iepDate": "2021-12-02",
                "iepTime": "2021-12-02T09:24:42.894Z",
                "iepDetails": [
                  {
                    "agencyId": "MDI",
                    "bookingId": $bookingId,
                    "sequence": 2,
                    "iepDate": "2021-12-02",
                    "iepLevel": "Basic",
                    "iepTime": "2021-12-02T09:24:42.894Z",
                    "comment": "A new Review",
                    "userId": "TEST_USER",
                    "auditModuleName": "PRISON_API"
                  },
                   {
                    "agencyId": "BXI",
                    "bookingId": $bookingId,
                    "sequence": 1,
                    "iepDate": "2020-11-02",
                    "iepLevel": "Entry",
                    "iepTime": "2021-11-02T09:00:42.894Z",
                    "comment": "A new Review",
                    "userId": "TEST_USER",
                    "auditModuleName": "PRISON_API"
                  }
                ]
              }
            """
          )
      )
    )
  }

  fun stubApi404for(url: String = "/api/bookings/1234134/iepSummary?withDetails=true") {
    stubFor(
      get(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(404)
          .withBody(
            // language=json
            """
            {
              "status": 404,
              "userMessage": "Entity Not Found",
              "errorCode": 42,
              "developerMessage": "This is a test 404 Not Found response",
              "moreInfo": "This is a test 404 Not Found response"
            }
            """
          )
      )
    )
  }

  fun stubPositiveCaseNoteSummary() {
    stubFor(
      post("/api/case-notes/usage")
        .withRequestBody(
          WireMock.equalToJson("""{"numMonths": 3, "type": "POS"}""", true, true)
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              // language=json
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
                },
                {
                  "caseNoteSubType": "POS",
                  "caseNoteType": "IEP_ENC",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "offenderNo": "A1409AE"
                }
              ]
              """
            )
        )
    )
  }
  fun stubNegativeCaseNoteSummary() {
    stubFor(
      post("/api/case-notes/usage")
        .withRequestBody(
          WireMock.equalToJson("""{"numMonths": 3, "type": "NEG"}""", true, true)
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              // language=json
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
                },
                {
                  "caseNoteSubType": "NEG",
                  "caseNoteType": "IEP_WARN",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 4,
                  "offenderNo": "A1409AE"
                }
              ]
              """
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
            // language=json
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
            """
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
          // language=json
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
            """
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
            // language=json
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
            """
          )
      )
    )
  }

  fun stubGetLocationById(locationId: Long, locationDesc: String) {
    stubFor(
      get("/api/locations/$locationId?includeInactive=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
              {
                "agencyId": "MDI",
                "locationId": $locationId,
                "description": "$locationDesc",
                "locationType": "CELL"
              }
            """
          )
      )
    )
  }
}
