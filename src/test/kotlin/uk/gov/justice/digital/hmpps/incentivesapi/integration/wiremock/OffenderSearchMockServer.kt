package uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerList
import java.time.LocalDate

class OffenderSearchMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
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

  fun stubFindOffenders(prisonId: String) {
    val mapper = jacksonObjectMapper().findAndRegisterModules()
    stubFor(
      get("/prison/$prisonId/prisoners").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            mapper.writeValueAsBytes(
              OffenderSearchPrisonerList(
                totalElements = 2,
                content = listOf(
                  OffenderSearchPrisoner(
                    prisonerNumber = "A1409AE",
                    bookingId = "110001",
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
                    prisonerNumber = "G6123VU",
                    bookingId = "110002",
                    firstName = "RHYS",
                    middleNames = "BARRY",
                    lastName = "JONES",
                    status = "ACTIVE IN",
                    inOutStatus = "IN",
                    dateOfBirth = LocalDate.parse("1970-03-01"),
                    receptionDate = LocalDate.parse("2020-07-01"),
                    prisonId = prisonId,
                    prisonName = "$prisonId prison",
                    cellLocation = "2-1-003",
                    locationDescription = "$prisonId prison",
                    alerts = listOf(),
                  ),
                ),
              )
            )
          )
      )
    )
  }

  fun stubGetOffender(prisonId: String, prisonerNumber: String, withOpenAcct: Boolean = true) {
    val alerts = if (withOpenAcct) listOf(
      OffenderSearchPrisonerAlert(
        alertType = "H",
        alertCode = "HA",
        active = true,
        expired = false,
      ),
    ) else emptyList()

    val mapper = jacksonObjectMapper().findAndRegisterModules()
    stubFor(
      get("/prisoner/$prisonerNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            mapper.writeValueAsBytes(
              OffenderSearchPrisoner(
                prisonerNumber = prisonerNumber,
                bookingId = "110001",
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
