package uk.gov.justice.digital.hmpps.incentivesapi.task

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.ReviewsConductedPrisonersReviewed
import uk.gov.justice.digital.hmpps.incentivesapi.service.KpiService
import java.time.LocalDate

@DisplayName("UpdateKpis task")
class UpdateKpisTest {

  private val kpiRepository: KpiRepository = mock()
  private val kpiService: KpiService = mock()

  private val task = UpdateKpis(kpiRepository, kpiService)

  private val today = LocalDate.now()

  private val numberOfPrisonersOverdue = 5000
  private val reviewsConductedPrisonersReviewed = ReviewsConductedPrisonersReviewed(
    reviewsConducted = 30000,
    prisonersReviewed = 15000,
  )

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    whenever(kpiService.getNumberOfPrisonersOverdue()).thenReturn(numberOfPrisonersOverdue)
    whenever(
      kpiService.getNumberOfReviewsConductedAndPrisonersReviewed(today),
    ).thenReturn(reviewsConductedPrisonersReviewed)
  }

  @Test
  fun `updateKpis() update the kpi table with the KPIs numbers for the month`(): Unit = runBlocking {
    whenever(kpiRepository.existsById(today)).thenReturn(false)

    task.updateKpis()

    verify(kpiRepository, times(1)).save(
      Kpi(
        day = today,
        overdueReviews = numberOfPrisonersOverdue,
        previousMonthReviewsConducted = reviewsConductedPrisonersReviewed.reviewsConducted,
        previousMonthPrisonersReviewed = reviewsConductedPrisonersReviewed.prisonersReviewed,
      ),
    )
  }

  @Test
  fun `updateKpis() skips month if already in DB`(): Unit = runBlocking {
    whenever(kpiRepository.existsById(today)).thenReturn(true)

    task.updateKpis()

    verify(kpiRepository, never()).save(any())
  }
}
