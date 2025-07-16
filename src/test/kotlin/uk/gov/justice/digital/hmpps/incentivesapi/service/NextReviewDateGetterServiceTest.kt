package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import java.time.LocalDate

@DisplayName("Next review date getter service")
class NextReviewDateGetterServiceTest {
  private val nextReviewDateRepository: NextReviewDateRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val nextReviewDateUpdaterService: NextReviewDateUpdaterService = mock()
  private val nextReviewDateGetterService = NextReviewDateGetterService(
    nextReviewDateRepository,
    prisonerSearchService,
    nextReviewDateUpdaterService,
  )

  @Test
  fun `getMany() returns the next review dates (updates missing ones)`(): Unit = runBlocking {
    val prisoners = listOf(
      mockPrisoner("AB123C", 123L),
      mockPrisoner("XY456Z", 456L),
    )
    val expectedNextReviewDates = mapOf(
      123L to LocalDate.parse("2025-01-23"),
      456L to LocalDate.parse("2025-04-06"),
    )

    // one prisoner with a NextReviewDate record
    whenever(nextReviewDateRepository.findAllById(listOf(123L, 456L)))
      .thenReturn(flowOf(NextReviewDate(456L, expectedNextReviewDates[456L]!!)))

    // calls update() with prisoners without next review date
    whenever(nextReviewDateUpdaterService.updateMany(listOf(prisoners[0])))
      .thenReturn(mapOf(123L to expectedNextReviewDates[123L]!!))

    val result = nextReviewDateGetterService.getMany(prisoners)

    assertThat(result).isEqualTo(expectedNextReviewDates)
  }

  @Test
  fun `get() return the next review date (update it if missing)`(): Unit = runBlocking {
    val bookingId = 1234L
    val prisonerNumber = "A1244AB"
    val expectedNextReviewDate = LocalDate.parse("2022-07-30")

    val prisonerInfo = mockPrisoner(prisonerNumber, bookingId)
    whenever(prisonerSearchService.getPrisonerInfo(bookingId))
      .thenReturn(prisonerInfo)

    // no record found in the database
    whenever(nextReviewDateRepository.findAllById(listOf(bookingId)))
      .thenReturn(emptyFlow())
    whenever(nextReviewDateUpdaterService.updateMany(listOf(prisonerInfo)))
      .thenReturn(mapOf(prisonerInfo.bookingId to expectedNextReviewDate))

    val result = nextReviewDateGetterService.get(bookingId)

    // check next review date was updated for prisoner
    verify(nextReviewDateUpdaterService, times(1))
      .updateMany(listOf(prisonerInfo))

    assertThat(result).isEqualTo(expectedNextReviewDate)
  }
}
