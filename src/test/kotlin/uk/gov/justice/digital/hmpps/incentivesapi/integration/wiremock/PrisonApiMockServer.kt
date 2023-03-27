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
          .withStatus(status),
      ),
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
            """,
          ),
      ),
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
            """,
          ),
      ),
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
            """,
          ),
      ),
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
            """,
          ),
      ),
    )
  }

  fun stubCaseNoteSummary() {
    stubFor(
      post("/api/case-notes/usage-by-types")
        .withRequestBody(
          WireMock.equalToJson("""{ "types": ["POS", "NEG"] }""", true, true),
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              // language=json
              """
              [
                {
                  "caseNoteSubType": "POS_GEN",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "bookingId": 1234134
                },
                {
                  "caseNoteSubType": "IEP_ENC",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "bookingId": 1234134
                },
                {
                  "caseNoteSubType": "QUAL_ATT",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "bookingId": 1234135
                },
                {
                  "caseNoteSubType": "POS_GEN",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "bookingId": 1234136
                },
                {
                  "caseNoteSubType": "IEP_ENC",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "bookingId": 1234137
                },
                {
                  "caseNoteSubType": "POS_GEN",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 5,
                  "bookingId": 1234138
                },
                {
                  "caseNoteSubType": "IEP_ENC",
                  "caseNoteType": "POS",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "bookingId": 1234138
                },
                {
                  "caseNoteSubType": "BEHAVEWARN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "bookingId": 1234134
                },
                {
                  "caseNoteSubType": "IEP_WARN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "bookingId": 1234134
                },
                {
                  "caseNoteSubType": "WORKWARN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 3,
                  "bookingId": 1234135
                },
                {
                  "caseNoteSubType": "NEG_GEN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 2,
                  "bookingId": 1234136
                },
                {
                  "caseNoteSubType": "NEG_GEN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 1,
                  "bookingId": 1234137
                },
                {
                  "caseNoteSubType": "NEG_GEN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 5,
                  "bookingId": 1234138
                },
                {
                  "caseNoteSubType": "IEP_WARN",
                  "caseNoteType": "NEG",
                  "latestCaseNote": "2022-01-21T09:28:25.673Z",
                  "numCaseNotes": 4,
                  "bookingId": 1234138
                }
              ]
              """,
            ),
        ),
    )
  }

  fun stubAddIep(bookingId: Long) {
    stubFor(
      post("/api/bookings/$bookingId/iepLevels").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
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
            """,
          ),
      ),
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
            """,
          ),
      ),
    )
  }

  fun stubGetPrisonerExtraInfo(bookingId: Long, prisonerNumber: String) {
    stubFor(
      get("/api/bookings/$bookingId?extraInfo=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
              {
                "bookingId": $bookingId,
                "offenderNo": "$prisonerNumber",
                "dateOfBirth": "1971-07-01",
                "receptionDate": "2020-07-01",
                "alerts":  []
              }
            """,
          ),
      ),
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
            """,
          ),
      ),
    )
  }
}
