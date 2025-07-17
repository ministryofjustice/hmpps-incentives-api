package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.PrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.incentivesapi.service.mockPrisoner
import java.time.LocalDate

class PrisonerSearchMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
  }

  private val mapper = ObjectMapper().findAndRegisterModules()

  fun getCountFor(path: String) = this.findAll(getRequestedFor(urlPathEqualTo(path))).count()

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

  fun stubFindPrisoners(prisonId: String = "MDI", includeInvalid: Boolean = false) {
    // <editor-fold desc="mocked prisoners">
    val prisoners = mutableListOf(
      Prisoner(
        prisonerNumber = "A1234AA",
        bookingId = 1234134,
        firstName = "JOHN",
        lastName = "SMITH",
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        alerts = listOf(
          PrisonerAlert(
            alertType = "H",
            alertCode = "HA",
            active = true,
            expired = false,
          ),
        ),
      ),
      Prisoner(
        prisonerNumber = "A1234AB",
        bookingId = 1234135,
        firstName = "DAVID",
        lastName = "WHITE",
        dateOfBirth = LocalDate.parse("1970-03-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
      ),
      Prisoner(
        prisonerNumber = "A1234AC",
        bookingId = 1234136,
        firstName = "TREVOR",
        lastName = "LEE",
        dateOfBirth = LocalDate.parse("1970-03-02"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
      ),
      Prisoner(
        prisonerNumber = "A1234AD",
        bookingId = 1234137,
        firstName = "ANTHONY",
        lastName = "DAVIES",
        dateOfBirth = LocalDate.parse("1970-03-03"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
      ),
      Prisoner(
        prisonerNumber = "A1234AE",
        bookingId = 1234138,
        firstName = "PAUL",
        lastName = "RUDD",
        dateOfBirth = LocalDate.parse("1970-03-04"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
      ),
    )
    if (includeInvalid) {
      prisoners.addAll(
        listOf(
          // does not have a known incentive level according to prison-api
          Prisoner(
            prisonerNumber = "A1834AA",
            bookingId = 2234134,
            firstName = "MISSING",
            lastName = "IEP",
            dateOfBirth = LocalDate.parse("1970-03-05"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = prisonId,
          ),
          // has an unknown incentive level
          Prisoner(
            prisonerNumber = "A1934AA",
            bookingId = 2734134,
            firstName = "OLD",
            lastName = "ENTRY",
            dateOfBirth = LocalDate.parse("1970-03-06"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = prisonId,
          ),
        ),
      )
    }
    // </editor-fold>
    stubFor(
      get(urlPathEqualTo("/prison/$prisonId/prisoners")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            mapper.writeValueAsBytes(
              mapOf(
                "content" to prisoners,
                "totalElements" to prisoners.size,
                "last" to true,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetPrisonerInfoByPrisonerNumber(bookingId: Long, prisonerNumber: String) {
    val prisoner = mockPrisoner(prisonerNumber, bookingId)
    stubFor(
      get(urlPathEqualTo("/prisoner/$prisonerNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          // language=json
          .withBody(
            mapper.writeValueAsBytes(
              prisoner
            )
          ),
      ),
    )
  }

  fun stubGetPrisonerInfoByBookingId(bookingId: Long, prisonerNumber: String) {
    val prisoner = mockPrisoner(prisonerNumber, bookingId)
    stubFor(
      post(urlPathEqualTo("/prisoner-search/booking-ids"))
        .withRequestBody(
          equalToJson("""{ "bookingIds": [$bookingId] }""", true, true),
        ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          // language=json
          .withBody(
            mapper.writeValueAsBytes(
              listOf(prisoner)
            )
          ),
      ),
    )
  }
}
