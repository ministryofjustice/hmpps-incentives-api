package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import javax.validation.constraints.NotEmpty

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
) {

  suspend fun findPrisonersAtLocation(prisonId: String, locationId: String): Flow<PrisonerAtLocation> =
    prisonWebClient.get()
      .uri("/api/locations/description/$locationId/inmates")
      .header("Page-Limit", "3000")
      .retrieve()
      .bodyToFlow<PrisonerAtLocation>()
      .catch {
        if (it is NotFound) { emitAll(emptyFlow()) } else { throw it }
      }

  fun getIEPSummaryPerPrisoner(@NotEmpty bookingIds: List<Long>): Flow<IepSummary> =
    prisonWebClient.post()
      .uri("/api/bookings/iepSummary")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlow<IepSummary>()
      .catch {
        if (it is NotFound) { emitAll(emptyFlow()) } else { throw it }
      }

  suspend fun getIEPSummaryForPrisoner(bookingId: Long, withDetails: Boolean): IepSummary =
    prisonWebClient.get()
      .uri("/api/bookings/$bookingId/iepSummary?withDetails=$withDetails")
      .retrieve()
      .awaitBody()

  suspend fun retrieveCaseNoteCounts(type: String, @NotEmpty offenderNos: List<String>): Flow<CaseNoteUsage> =
    prisonWebClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToFlow<CaseNoteUsage>()
      .catch {
        if (it is NotFound) { emitAll(emptyFlow()) } else { throw it }
      }

  suspend fun retrieveProvenAdjudications(@NotEmpty bookingIds: List<Long>): Flow<ProvenAdjudication> =
    prisonWebClient.post()
      .uri("/api/bookings/proven-adjudications")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlow<ProvenAdjudication>()
      .catch {
        if (it is NotFound) { emitAll(emptyFlow()) } else { throw it }
      }

  suspend fun getLocation(locationId: String): PrisonLocation =
    prisonWebClient.get()
      .uri("/api/locations/code/$locationId")
      .retrieve()
      .awaitBody()

  suspend fun addIepReview(bookingId: Long, iepReview: IepReviewInNomis) =
    prisonWebClient.post()
      .uri("/api/bookings/$bookingId/iepLevels")
      .bodyValue(iepReview)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getPrisonerInfo(prisonerNumber: String, useClientCredentials: Boolean = false): PrisonerAtLocation {
    return if (useClientCredentials) {
      prisonWebClientClientCredentials.get()
        .uri("/api/bookings/offenderNo/$prisonerNumber")
        .retrieve()
        .awaitBody()
    } else {
      prisonWebClient.get()
        .uri("/api/bookings/offenderNo/$prisonerNumber")
        .retrieve()
        .awaitBody()
    }
  }

  suspend fun getPrisonerInfo(bookingId: Long): PrisonerAtLocation =
    prisonWebClient.get()
      .uri("/api/bookings/$bookingId?basicInfo=true")
      .retrieve()
      .awaitBody()

  suspend fun getLocationById(locationId: Long, useClientCredentials: Boolean = false): Location {
    return if (useClientCredentials) {
      prisonWebClientClientCredentials.get()
        .uri("/api/locations/$locationId")
        .retrieve()
        .awaitBody()
    } else {
      prisonWebClient.get()
        .uri("/api/locations/$locationId")
        .retrieve()
        .awaitBody()
    }
  }
}
