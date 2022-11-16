package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList
import java.time.LocalDate

class OffenderSearchMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
  }

  private val mapper = ObjectMapper().findAndRegisterModules()

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

  fun stubFindOffenders(prisonId: String = "MDI", wing: String = "1", includeInvalid: Boolean = false) {
    // <editor-fold desc="mocked offenders">
    val offenders = mutableListOf(
      OffenderSearchPrisoner(
        prisonerNumber = "A1234AA",
        bookingId = 1234134,
        firstName = "JOHN",
        lastName = "SMITH",
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1971-07-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        prisonName = "$prisonId prison",
        cellLocation = "$wing-1-001",
        locationDescription = "$prisonId prison",
        alerts = listOf(
          OffenderSearchPrisonerAlert(
            alertType = "H",
            alertCode = "HA",
            active = true,
            expired = false,
          ),
        ),
      ),
      OffenderSearchPrisoner(
        prisonerNumber = "A1234AB",
        bookingId = 1234135,
        firstName = "DAVID",
        lastName = "WHITE",
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1970-03-01"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        prisonName = "$prisonId prison",
        cellLocation = "$wing-1-002",
        locationDescription = "$prisonId prison",
      ),
      OffenderSearchPrisoner(
        prisonerNumber = "A1234AC",
        bookingId = 1234136,
        firstName = "TREVOR",
        lastName = "LEE",
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1970-03-02"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        prisonName = "$prisonId prison",
        cellLocation = "$wing-1-003",
        locationDescription = "$prisonId prison",
      ),
      OffenderSearchPrisoner(
        prisonerNumber = "A1234AD",
        bookingId = 1234137,
        firstName = "ANTHONY",
        lastName = "DAVIES",
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1970-03-03"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        prisonName = "$prisonId prison",
        cellLocation = "$wing-1-004",
        locationDescription = "$prisonId prison",
      ),
      OffenderSearchPrisoner(
        prisonerNumber = "A1234AE",
        bookingId = 1234138,
        firstName = "PAUL",
        lastName = "RUDD",
        status = "ACTIVE IN",
        inOutStatus = "IN",
        dateOfBirth = LocalDate.parse("1970-03-04"),
        receptionDate = LocalDate.parse("2020-07-01"),
        prisonId = prisonId,
        prisonName = "$prisonId prison",
        cellLocation = "$wing-1-005",
        locationDescription = "$prisonId prison",
      ),
    )
    if (includeInvalid) {
      offenders.addAll(
        listOf(
          // does not have a known incentive level according to prison-api
          OffenderSearchPrisoner(
            prisonerNumber = "A1834AA",
            bookingId = 2234134,
            firstName = "MISSING",
            lastName = "IEP",
            status = "ACTIVE IN",
            inOutStatus = "IN",
            dateOfBirth = LocalDate.parse("1970-03-05"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = prisonId,
            prisonName = "$prisonId prison",
            cellLocation = "$wing-1-006",
            locationDescription = "$prisonId prison"
          ),
          // has an unknown incentive level
          OffenderSearchPrisoner(
            prisonerNumber = "A1934AA",
            bookingId = 2734134,
            firstName = "OLD",
            lastName = "ENTRY",
            status = "ACTIVE IN",
            inOutStatus = "IN",
            dateOfBirth = LocalDate.parse("1970-03-06"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = prisonId,
            prisonName = "$prisonId prison",
            cellLocation = "$wing-1-007",
            locationDescription = "$prisonId prison"
          ),
        )
      )
    }
    // </editor-fold>
    stubFor(
      get(urlPathEqualTo("/prison/$prisonId/prisoners")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            mapper.writeValueAsBytes(
              OffenderSearchPrisonerList(totalElements = offenders.size, content = offenders)
            )
          )
      )
    )
  }

  fun stubGetOffender(prisonId: String, prisonerNumber: String, bookingId: Long, withOpenAcct: Boolean = true) {
    val alerts = if (withOpenAcct) listOf(
      OffenderSearchPrisonerAlert(
        alertType = "H",
        alertCode = "HA",
        active = true,
        expired = false,
      ),
    ) else emptyList()

    stubFor(
      get("/prisoner/$prisonerNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            mapper.writeValueAsBytes(
              OffenderSearchPrisoner(
                prisonerNumber = prisonerNumber,
                bookingId = bookingId,
                firstName = "JAMES",
                middleNames = null,
                lastName = "HALLS",
                status = "ACTIVE IN",
                inOutStatus = "IN",
                dateOfBirth = LocalDate.parse("1971-07-01"),
                receptionDate = LocalDate.parse("2020-07-01"),
                prisonId = prisonId,
                prisonName = "$prisonId prison",
                cellLocation = "2-1-002",
                locationDescription = "$prisonId prison",
                alerts = alerts,
              )
            )
          )
      )
    )
  }
}
