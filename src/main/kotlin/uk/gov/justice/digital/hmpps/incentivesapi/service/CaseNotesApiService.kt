package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlow
import uk.gov.justice.digital.hmpps.incentivesapi.dto.casenoteapi.BookingFromDatePair
import uk.gov.justice.digital.hmpps.incentivesapi.dto.casenoteapi.CaseNoteUsageTypesRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.casenoteapi.PrisonerCaseNoteByTypeSubType
import java.time.LocalDateTime

@Service
class CaseNotesApiService(
  private val caseNoteApiClientClientCredentials: WebClient,
) {

  fun retrieveCaseNoteCountsByFromDate(
    types: List<String>,
    prisonerByLastReviewDate: Map<Long, LocalDateTime>,
  ): Flow<PrisonerCaseNoteByTypeSubType> = caseNoteApiClientClientCredentials.post()
    .uri("/case-notes/usage")
    .bodyValue(
      CaseNoteUsageTypesRequest(
        types = types,
        bookingFromDateSelection = prisonerByLastReviewDate.map { BookingFromDatePair(it.key, it.value) },
      ),
    )
    .retrieve()
    .bodyToFlow()
}
