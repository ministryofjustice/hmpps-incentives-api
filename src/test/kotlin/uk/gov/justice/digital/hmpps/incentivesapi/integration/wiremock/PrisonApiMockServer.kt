package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get

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
          .withStatus(status),
      ),
    )
  }

  @Suppress("unused")
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
}
