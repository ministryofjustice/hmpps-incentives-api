package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerIepLevelRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// TODO: move into inner class CountPrisonersOnLevel of PrisonerIepLevelReviewServiceTest once IepLevelService is removed
class CountPrisonersServiceTest {
  private val clock: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.of("Europe/London"))

  private val prisonerIepLevelRepository: PrisonerIepLevelRepository = mock()
  private val offenderSearchService: OffenderSearchService = mock()

  private val countPrisonersService = CountPrisonersService(prisonerIepLevelRepository, offenderSearchService)

  @Test
  fun `count prisoners on a level in a prison`(): Unit = runBlocking {
    // mock offender search returning 2 pages of results with 3 prisoners, only 2 of which are on Standard in the repository
    whenever(offenderSearchService.findOffendersAtLocation("MDI", "MDI")).thenReturn(
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
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(110001, 110002)))
      .thenReturn(
        flowOf(
          PrisonerIepLevel(
            prisonerNumber = "A1409AE",
            bookingId = 110001,
            iepCode = "STD",
            prisonId = "MDI",
            locationId = "MDI-1-1-001",
            reviewedBy = "TEST_STAFF1",
            reviewTime = LocalDateTime.now(clock).minusDays(2),
          ),
          PrisonerIepLevel(
            prisonerNumber = "G6123VU",
            bookingId = 110002,
            iepCode = "ENH",
            prisonId = "MDI",
            locationId = "MDI-1-1-002",
            reviewedBy = "TEST_STAFF1",
            reviewTime = LocalDateTime.now(clock).minusDays(10),
          ),
        ),
      )
    whenever(prisonerIepLevelRepository.findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(listOf(110003)))
      .thenReturn(
        flowOf(
          PrisonerIepLevel(
            prisonerNumber = "G6123VX",
            bookingId = 110003,
            iepCode = "STD",
            prisonId = "MDI",
            locationId = "MDI-1-1-004",
            reviewedBy = "TEST_STAFF1",
            reviewTime = LocalDateTime.now(clock).minusDays(5),
          ),
        ),
      )

    val countOfPrisonersOnLevelInPrison = countPrisonersService.countOfPrisonersOnLevelInPrison("MDI", "STD")

    assertThat(countOfPrisonersOnLevelInPrison).isEqualTo(2)
    verify(offenderSearchService, times(1)).findOffendersAtLocation("MDI", "MDI")
    verify(prisonerIepLevelRepository, times(2)).findAllByBookingIdInAndCurrentIsTrueOrderByReviewTimeDesc(any())
  }
}
