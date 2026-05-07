package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
    val prisonerResponses = mapOf(
      "A1234AA" to """
        {
          "content": {
            "A1234AA": [
              {
                "personIdentifier": "A1234AA",
                "type": "POS",
                "subType": "POS_GEN",
                "count": 2,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AA",
                "type": "POS",
                "subType": "IEP_ENC",
                "count": 1,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AA",
                "type": "NEG",
                "subType": "BEHAVEWARN",
                "count": 2,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AA",
                "type": "NEG",
                "subType": "IEP_WARN",
                "count": 1,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              }
            ]
          }
        }
      """,
      "A1234AB" to """
        {
          "content": {
            "A1234AB": [
              {
                "personIdentifier": "A1234AB",
                "type": "POS",
                "subType": "QUAL_ATT",
                "count": 3,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AB",
                "type": "NEG",
                "subType": "WORKWARN",
                "count": 3,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              }
            ]
          }
        }
      """,
      "A1234AC" to """
        {
          "content": {
            "A1234AC": [
              {
                "personIdentifier": "A1234AC",
                "type": "POS",
                "subType": "POS_GEN",
                "count": 2,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AC",
                "type": "NEG",
                "subType": "NEG_GEN",
                "count": 2,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              }
            ]
          }
        }
      """,
      "A1234AD" to """
        {
          "content": {
            "A1234AD": [
              {
                "personIdentifier": "A1234AD",
                "type": "POS",
                "subType": "IEP_ENC",
                "count": 1,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AD",
                "type": "NEG",
                "subType": "NEG_GEN",
                "count": 1,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              }
            ]
          }
        }
      """,
      "A1234AE" to """
        {
          "content": {
            "A1234AE": [
              {
                "personIdentifier": "A1234AE",
                "type": "POS",
                "subType": "POS_GEN",
                "count": 5,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AE",
                "type": "POS",
                "subType": "IEP_ENC",
                "count": 3,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AE",
                "type": "NEG",
                "subType": "NEG_GEN",
                "count": 5,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              },
              {
                "personIdentifier": "A1234AE",
                "type": "NEG",
                "subType": "IEP_WARN",
                "count": 4,
                "latestNote": { "occurredAt": "2022-01-21T09:28:25.673Z" }
              }
            ]
          }
        }
      """,
    )

    prisonerResponses.forEach { (personIdentifier, body) ->
      stubFor(
        post("/case-notes/usage")
          .withRequestBody(matchingJsonPath("$.personIdentifiers[?(@ == '$personIdentifier')]"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(body),
          ),
      )
    }
  }
}
