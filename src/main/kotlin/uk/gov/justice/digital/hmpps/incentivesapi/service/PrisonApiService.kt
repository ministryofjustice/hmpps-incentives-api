package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.BookingFromDatePair
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.CaseNoteUsageTypesRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Location
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerCaseNoteByTypeSubType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerExtraInfo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.ReferenceCode
import uk.gov.justice.digital.hmpps.incentivesapi.util.CachedValue
import java.time.Clock
import java.time.LocalDateTime

@Service
class PrisonApiService(
  private val prisonWebClient: WebClient,
  private val prisonWebClientClientCredentials: WebClient,
  clock: Clock,
) {

  val allIncentiveLevelsCache = CachedValue<List<IepLevel>>(validForHours = 24, clock)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun getClient(useClientCredentials: Boolean = false): WebClient {
    return if (useClientCredentials) prisonWebClientClientCredentials else prisonWebClient
  }

  fun getIepLevelsForPrison(prisonId: String, useClientCredentials: Boolean = false): Flow<IepLevel> {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/agencies/$prisonId/iepLevels")
      .retrieve().bodyToFlow()
  }

  suspend fun getIepLevels(): List<IepLevel> = allIncentiveLevelsCache.get() ?: run {
    log.debug("Getting all incentive levels using GET /api/reference-domains/domains/IEP_LEVEL/codes...")
    val newValue = getClient(true)
      .get()
      .uri("/api/reference-domains/domains/IEP_LEVEL/codes")
      .retrieve()
      .bodyToFlow<ReferenceCode>()
      .map {
        IepLevel(
          iepLevel = it.code,
          iepDescription = it.description,
          sequence = it.listSeq,
          active = it.activeFlag == "Y",
        )
      }
      .toList()

    allIncentiveLevelsCache.update(newValue)

    return newValue
  }

  fun retrieveCaseNoteCountsByFromDate(types: List<String>, prisonerByLastReviewDate: Map<Long, LocalDateTime>): Flow<PrisonerCaseNoteByTypeSubType> =
    prisonWebClient.post()
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

  suspend fun getLocationById(locationId: Long, useClientCredentials: Boolean = false): Location {
    return getClient(useClientCredentials)
      .get()
      .uri("/api/locations/$locationId?includeInactive=true")
      .retrieve()
      .awaitBody()
  }
}
