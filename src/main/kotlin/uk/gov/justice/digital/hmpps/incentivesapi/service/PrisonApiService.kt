package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepSummary
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsage
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsageRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.ProvenAdjudication
import javax.validation.constraints.NotEmpty

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
) {

  suspend fun getIepLevelsForPrison(prisonId: String): Flow<IepLevel> {
    return prisonWebClient.get().uri("/api/agencies/$prisonId/iepLevels").retrieve().bodyToFlow<IepLevel>()
  }

  suspend fun findPrisonersAtLocation(prisonId: String, locationId: String): Flow<PrisonerAtLocation> =
    prisonWebClient.get()
      .uri("/api/locations/description/$locationId/inmates")
      .header("Page-Limit", "3000")
      .retrieve()
      .bodyToFlow<PrisonerAtLocation>()

  fun getIEPSummaryPerPrisoner(@NotEmpty bookingIds: List<Long>): Flow<IepSummary> =
    prisonWebClient.post()
      .uri("/api/bookings/iepSummary")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlow<IepSummary>()

  suspend fun getIEPSummaryForPrisoner(bookingId: Long, withDetails: Boolean, useClientCredentials: Boolean = false): IepSummary {
    val webClient = if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient

    return webClient.get()
      .uri("/api/bookings/$bookingId/iepSummary?withDetails=$withDetails")
      .retrieve()
      .awaitBody()
  }

  suspend fun retrieveCaseNoteCounts(type: String, @NotEmpty offenderNos: List<String>): Flow<CaseNoteUsage> =
    prisonWebClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToFlow<CaseNoteUsage>()

  suspend fun retrieveProvenAdjudications(@NotEmpty bookingIds: List<Long>): Flow<ProvenAdjudication> =
    prisonWebClient.post()
      .uri("/api/bookings/proven-adjudications")
      .bodyValue(bookingIds)
      .retrieve()
      .bodyToFlow<ProvenAdjudication>()

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
    val webClient = if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient

    return webClient.get()
      .uri("/api/bookings/offenderNo/$prisonerNumber")
      .retrieve()
      .awaitBody()
  }

  suspend fun getPrisonerInfo(bookingId: Long, useClientCredentials: Boolean = false): PrisonerAtLocation {
    val webClient = if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient
    return webClient.get()
      .uri("/api/bookings/$bookingId?basicInfo=true")
      .retrieve()
      .awaitBody()
  }

  suspend fun getLocationById(locationId: Long, useClientCredentials: Boolean = false): Location {
    val webClient = if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient

    return webClient.get()
      .uri("/api/locations/$locationId?includeInactive=true")
      .retrieve()
      .awaitBody()
  }
}
