package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsage
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsageRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerExtraInfo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.ProvenAdjudication

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
) {

  private fun getClient(useClientCredentials: Boolean = false): WebClient {
    return if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient
  }

  fun getIepLevelsForPrison(prisonId: String, useClientCredentials: Boolean = false): Flow<IepLevel> {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/agencies/$prisonId/iepLevels")
      .retrieve().bodyToFlow()
  }

  suspend fun getIncentiveLevels(): Map<String, IepLevel> {
    return getIepLevels().toList().associateBy { iep -> iep.iepLevel }
  }

  fun getIepLevels(): Flow<IepLevel> {
    return getClient(true)
      .get()
      .uri("/api/reference-domains/domains/IEP_LEVEL/codes")
      .retrieve().bodyToFlow<IncentiveLevel>()
      .map {
        IepLevel(
          iepLevel = it.code,
          iepDescription = it.description,
          sequence = it.listSeq,
          active = it.activeFlag == "Y"
        )
      }
  }

  fun retrieveCaseNoteCounts(type: String, offenderNos: List<String>): Flow<CaseNoteUsage> =
    prisonWebClient.post()
      .uri("/api/case-notes/usage")
      .bodyValue(CaseNoteUsageRequest(numMonths = 3, offenderNos = offenderNos, type = type, subType = null))
      .retrieve()
      .bodyToFlow()

  fun retrieveProvenAdjudications(bookingIds: List<Long>): Flow<ProvenAdjudication> =
    prisonWebClient.post()
      .uri("/api/bookings/proven-adjudications")
      .bodyValue(bookingIds)
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

  suspend fun getLocationById(locationId: Long, useClientCredentials: Boolean = false): Location {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/locations/$locationId?includeInactive=true")
      .retrieve()
      .awaitBody()
  }
}
