package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get

class LocationsMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8095
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

  fun stubGetByKey(key: String) {
    stubFor(
      get("/locations/key/$key").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
                {
                  "id": "2475f250-434a-4257-afe7-b911f1773a4d",
                  "key": "$key",
                  "prisonId": "MDI",
                  "localName": "Houseblock 1",
                  "pathHierarchy": "$key",
                }
            """,
          ),
      ),
    )
  }
}
