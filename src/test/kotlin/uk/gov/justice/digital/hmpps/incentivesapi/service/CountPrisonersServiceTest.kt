package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.LocalDate

@DisplayName("Count prisoners service")
class CountPrisonersServiceTest {
  private val incentiveReviewRepository: IncentiveReviewRepository = mock()
  private val offenderSearchService: OffenderSearchService = mock()

  private val countPrisonersService = CountPrisonersService(incentiveReviewRepository, offenderSearchService)

  @Test
  fun `finds no prisoners in a prison`(): Unit = runBlocking {
    // mock offender search returning no prisoners
    whenever(offenderSearchService.findOffendersAtLocation("MDI", "")).thenReturn(flowOf(listOf()))

    assertThat(
      countPrisonersService.prisonersExistOnLevelInPrison("MDI", "STD"),
    ).isFalse()

    @Suppress("UnusedFlow")
    verify(offenderSearchService, times(1)).findOffendersAtLocation("MDI", "")
    verify(incentiveReviewRepository, times(0)).somePrisonerCurrentlyOnLevel(any(), eq("STD"))
  }

  @Test
  fun `finds prisoners on a level in a prison`(): Unit = runBlocking {
    // mock offender search returning 2 pages of results with 3 prisoners, only 2 of which are on Standard in the repository
    whenever(offenderSearchService.findOffendersAtLocation("MDI", "")).thenReturn(
      flowOf(
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
          OffenderSearchPrisoner(
            prisonerNumber = "G6123VU",
            bookingId = 110002,
            firstName = "RHYS",
            lastName = "JONES",
            dateOfBirth = LocalDate.parse("1970-03-01"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = "MDI",
          ),
        ),
        listOf(
          OffenderSearchPrisoner(
            prisonerNumber = "G6123VX",
            bookingId = 110003,
            firstName = "JOHN",
            lastName = "MILLER",
            dateOfBirth = LocalDate.parse("1970-03-01"),
            receptionDate = LocalDate.parse("2020-07-01"),
            prisonId = "MDI",
          ),
        ),
      ),
    )
    whenever(incentiveReviewRepository.somePrisonerCurrentlyOnLevel(listOf(110001, 110002), "STD"))
      .thenReturn(true)
    whenever(incentiveReviewRepository.somePrisonerCurrentlyOnLevel(listOf(110003), "STD"))
      .thenReturn(true)
    // NB: second call is not made because first page already returned true

    assertThat(
      countPrisonersService.prisonersExistOnLevelInPrison("MDI", "STD"),
    ).isTrue

    @Suppress("UnusedFlow")
    verify(offenderSearchService, times(1)).findOffendersAtLocation("MDI", "")
    verify(incentiveReviewRepository, times(1)).somePrisonerCurrentlyOnLevel(any(), eq("STD"))
  }
}
