package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.*
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import javax.validation.constraints.NotEmpty

@Service
class PrisonApiService(private val prisonWebClient: WebClient) {

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

  suspend fun getIepLevelsForPrison(prisonId: String): Flow<IepLevel> =
    prisonWebClient.get()
      .uri("/api/agencies/$prisonId/iepLevels")
      .retrieve()
      .bodyToFlow<IepLevel>()
      .catch {
        if (it is NotFound) { emitAll(emptyFlow()) } else { throw it }
      }

  suspend fun getLocation(locationId: String): PrisonLocation =
    prisonWebClient.get()
      .uri("/api/locations/code/$locationId")
      .retrieve()
      .awaitBody()
}
