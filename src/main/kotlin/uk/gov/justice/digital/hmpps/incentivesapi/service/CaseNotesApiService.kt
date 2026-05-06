package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlow
import java.time.LocalDateTime

@Service
class CaseNotesApiService(
  private val caseNoteApiClientClientCredentials: WebClient,
) {

  fun retrieveCaseNoteCountsByFromDate(
    typeSubType: Set<TypeSubTypeRequest>,
    personIdentifiers: Set<String>,
    from: LocalDateTime,
  ): Flow<NoteUsageResponse> = caseNoteApiClientClientCredentials.post()
    .uri("/case-notes/usage")
    .bodyValue(
      UsageByPersonIdentifierRequest(
        typeSubTypes = typeSubType,
        personIdentifiers = personIdentifiers,
        from = from,
      ),
    )
    .retrieve()
    .bodyToFlow()
}

data class UsageByPersonIdentifierRequest(
  val typeSubTypes: Set<TypeSubTypeRequest> = emptySet(),
  val from: LocalDateTime? = null,
  val personIdentifiers: Set<String> = setOf(),
)

data class TypeSubTypeRequest(
  val type: String,
  val subTypes: Set<String> = setOf(),
)

data class NoteUsageResponse(
  val content: Map<String, List<UsageByPersonIdentifierResponse>>,
)

data class UsageByPersonIdentifierResponse(
  val personIdentifier: String,
  val type: String,
  val subType: String,
  val count: Int,
  val latestNote: LatestNote? = null,
)

data class LatestNote(
  val occurredAt: LocalDateTime,
)
