package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.Prison
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.PrisonerNumberOverdue
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.ReviewsConductedPrisonersReviewed
import java.time.LocalDate

class KpiServiceTest {

  private val kpiRepository: KpiRepository = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val offenderSearchService: OffenderSearchService = mock()

  private val kpiService = KpiService(kpiRepository, prisonApiService, offenderSearchService)

  @Nested
  inner class GetNumberOfReviewsConductedAndPrisonersReviewed {

    @Test
    fun `getNumberOfReviewsConductedAndPrisonersReviewed() returns the counts from the DB`(): Unit = runBlocking {
      val day = LocalDate.now()
      val expected = ReviewsConductedPrisonersReviewed(
        reviewsConducted = 30000,
        prisonersReviewed = 15000,
      )
      whenever(kpiRepository.getNumberOfReviewsConductedAndPrisonersReviewed(day)).thenReturn(expected)

      assertThat(kpiService.getNumberOfReviewsConductedAndPrisonersReviewed(day)).isEqualTo(expected)
    }
  }

  @Nested
  inner class GetNumberOfPrisonersOverdue {

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(kpiRepository.getPrisonerNumbersOverdueReview()).thenReturn(
        flowOf(
          PrisonerNumberOverdue("D1111DD"),
          PrisonerNumberOverdue("A4444AA"),
          PrisonerNumberOverdue("A2222AA"),
          PrisonerNumberOverdue("A1111AA"), // still in prison
          PrisonerNumberOverdue("B9999BB"),
          PrisonerNumberOverdue("B7777BB"),
          PrisonerNumberOverdue("B1111BB"), // still in prison
          PrisonerNumberOverdue("Z1111ZZ"),
        ),
      )

      whenever(prisonApiService.getActivePrisons(true))
        .thenReturn(
          listOf(
            Prison(
              agencyId = "AAA",
              active = true,
              agencyType = "INST",
              description = "Test prison AAA",
              longDescription = "",
            ),
            Prison(
              agencyId = "BBB",
              active = true,
              agencyType = "INST",
              description = "Test prison BBB",
              longDescription = "",
            ),
          ),
        )

      val aaaPrisoners = listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "A1111AA",
          bookingId = 111111,
          firstName = "JAMES",
          middleNames = "",
          lastName = "HALLS",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "AAA",
          alerts = emptyList(),
        ),
        OffenderSearchPrisoner(
          prisonerNumber = "A9999AA",
          bookingId = 999999,
          firstName = "JONATHAN",
          middleNames = "",
          lastName = "MORES",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "AAA",
          alerts = emptyList(),
        ),
      )
      val bbbPrisoners = listOf(
        OffenderSearchPrisoner(
          prisonerNumber = "B1111BB",
          bookingId = 555555,
          firstName = "JOHN",
          middleNames = "",
          lastName = "HOLES",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "BBB",
          alerts = emptyList(),
        ),
        OffenderSearchPrisoner(
          prisonerNumber = "B2222BB",
          bookingId = 222222,
          firstName = "JOE",
          middleNames = "",
          lastName = "MOLES",
          dateOfBirth = LocalDate.parse("1971-07-01"),
          receptionDate = LocalDate.parse("2020-07-01"),
          prisonId = "BBB",
          alerts = emptyList(),
        ),
      )
      whenever(offenderSearchService.getOffendersAtLocation("AAA")).thenReturn(aaaPrisoners)
      whenever(offenderSearchService.getOffendersAtLocation("BBB")).thenReturn(bbbPrisoners)
    }

    @Test
    fun `getNumberOfPrisonersOverdue() returns the correct number of overdue prisoners`() {
      assertThat(kpiService.getNumberOfPrisonersOverdue()).isEqualTo(2)
    }
  }
}
