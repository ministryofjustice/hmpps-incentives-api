package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.BookingFromDatePair
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsageTypesRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Prison
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerExtraInfo
import java.time.LocalDateTime

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
) {

  private fun getClient(useClientCredentials: Boolean = false): WebClient {
    return if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient
  }

  fun retrieveCaseNoteCountsByFromDate(types: List<String>, prisonerByLastReviewDate: Map<Long, LocalDateTime>): Flow<PrisonerCaseNoteByTypeSubType> =
    prisonWebClientClientCredentials.post()
      .uri("/api/case-notes/usage-by-types")
      .bodyValue(
        CaseNoteUsageTypesRequest(
          types = types,
          bookingFromDateSelection = prisonerByLastReviewDate.map { BookingFromDatePair(it.key, it.value) },
        ),
      )
      .retrieve()
      .bodyToFlow()

  suspend fun getLocation(locationId: String): PrisonLocation =
    prisonWebClient.get()
      .uri("/api/locations/code/$locationId")
      .retrieve()
      .awaitBody()

  suspend fun getPrisonerInfo(prisonerNumber: String, useClientCredentials: Boolean = false): PrisonerAtLocation {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/bookings/offenderNo/$prisonerNumber")
      .retrieve()
      .awaitBody()
  }

  suspend fun getPrisonerInfo(bookingId: Long, useClientCredentials: Boolean = false): PrisonerAtLocation {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/bookings/$bookingId?basicInfo=true")
      .retrieve()
      .awaitBody()
  }

  suspend fun getPrisonerExtraInfo(bookingId: Long, useClientCredentials: Boolean = false): PrisonerExtraInfo {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/bookings/$bookingId?extraInfo=true")
      .retrieve()
      .awaitBody()
  }

  suspend fun getActivePrisons(useClientCredentials: Boolean = false): List<Prison> {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/agencies/prisons")
      .retrieve()
      .awaitBody()
  }
}
