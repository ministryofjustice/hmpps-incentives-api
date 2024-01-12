package uk.gov.justice.digital.hmpps.incentivesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import java.time.LocalDate

class OffenderSearchServiceTest {
  private val mapper = ObjectMapper().findAndRegisterModules()

  @Test
  fun `offender search object indicates if ACCT open`() {
    val offenderWithHaAlert = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = listOf(
        PrisonerAlert(
          alertType = "H",
          alertCode = "HA",
          active = true,
          expired = false,
        ),
      ),
    )
    assertThat(offenderWithHaAlert.hasAcctOpen).isTrue

    val offenderWithoutHaAlert = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = listOf(
        PrisonerAlert(
          alertType = "X",
          alertCode = "XA",
          active = true,
          expired = false,
        ),
      ),
    )
    assertThat(offenderWithoutHaAlert.hasAcctOpen).isFalse

    val offenderWithoutAlerts = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = emptyList(),
    )
    assertThat(offenderWithoutAlerts.hasAcctOpen).isFalse
  }

  private fun createWebClientMockResponses(vararg responses: List<OffenderSearchPrisoner>) = WebClient.builder()
    .exchangeFunction {
      val page = UriComponentsBuilder.fromUri(it.url()).build().queryParams["page"]?.get(0)?.toIntOrNull()
      val response =
        if (page != null && page < responses.size) {
          ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body(
              mapper.writeValueAsString(
                mapOf(
                  "content" to responses[page],
                  "totalElements" to responses.sumOf { it.size },
                  "last" to (page == responses.size - 1),
                ),
              ),
            )
            .build()
        } else {
          ClientResponse.create(HttpStatus.NOT_FOUND).build()
        }
      Mono.just(response)
    }
    .build()

  @Test
  fun `only one page of results is loaded if it is the last`(): Unit = runBlocking {
    // mocks 1 page with 1 result
    val webClient = createWebClientMockResponses(
      listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "JAMES",
          lastName = "HALLS",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "MDI",
        ),
      ),
    )
    val offenderSearchService = OffenderSearchService(webClient)
    val offenders = offenderSearchService.getOffendersAtLocation("MDI", "MDI-1")
    assertThat(offenders).hasSize(1)
  }

  @Test
  fun `all pages of results are loaded`(): Unit = runBlocking {
    // mocks 3 pages of 1 result per page
    val webClient = createWebClientMockResponses(
      listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "JAMES",
          lastName = "HALLS",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "MDI",
        ),
      ),
      listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "G6123VU",
          bookingId = 110002,
          firstName = "RHYS",
          middleNames = "BARRY",
          lastName = "JONES",
          dateOfBirth = LocalDate.parse("1970-03-02"),
          receptionDate = LocalDate.parse("2020-07-02"),
          prisonId = "MDI",
        ),
      ),
      listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "G6123VX",
          bookingId = 110003,
          firstName = "JOHN",
          lastName = "HALL",
          dateOfBirth = LocalDate.parse("1970-03-31"),
          receptionDate = LocalDate.parse("2020-07-03"),
          prisonId = "MDI",
        ),
      ),
    )
    val offenderSearchService = OffenderSearchService(webClient)
    val offenders = offenderSearchService.getOffendersAtLocation("MDI", "MDI-1")
    assertThat(offenders).hasSize(3)
  }
}
