package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post

class CaseNotesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8096
  }

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

  fun stubCaseNoteSummary() {
    stubFor(
      post("/case-notes/usage")
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
}
