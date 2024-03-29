package uk.gov.justice.digital.hmpps.incentivesapi.task

import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.incentivesapi.config.DEFAULT_LOCKED_AT_LEAST_FOR
import uk.gov.justice.digital.hmpps.incentivesapi.config.DEFAULT_LOCKED_AT_MOST_FOR
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.service.KpiService
import java.time.LocalDate

@Component
class UpdateKpis(
  private val kpiRepository: KpiRepository,
  private val kpiService: KpiService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.update-kpis.cron}")
  @SchedulerLock(
    name = "INC - Update KPIs",
    lockAtLeastFor = DEFAULT_LOCKED_AT_LEAST_FOR,
    lockAtMostFor = DEFAULT_LOCKED_AT_MOST_FOR,
  )
  fun updateKpis(): Unit = runBlocking {
    val day = LocalDate.now()

    val kpiExists = kpiRepository.existsById(day)
    if (kpiExists) {
      LOG.debug("KPI for $day already exists, skipping...")
      return@runBlocking
    }

    LOG.debug("Updating KPIs for $day...")

    val reviewsConductedPrisonersReviewed = kpiService.getNumberOfReviewsConductedAndPrisonersReviewed(day)
    val numberOfPrisonersOverdue = kpiService.getNumberOfPrisonersOverdue()

    // Logs result, it's a different way for us to get the numbers, possibly easier
    LOG.info("KPIs for $day. Reviews conducted = ${reviewsConductedPrisonersReviewed.reviewsConducted}")
    LOG.info("KPIs for $day. Prisoners reviewed = ${reviewsConductedPrisonersReviewed.prisonersReviewed}")
    LOG.info("KPIs for $day. Prisoners overdue = $numberOfPrisonersOverdue")

    // Store the result in the DB table
    kpiRepository.save(
      Kpi(
        day = day,
        overdueReviews = numberOfPrisonersOverdue,
        previousMonthReviewsConducted = reviewsConductedPrisonersReviewed.reviewsConducted,
        previousMonthPrisonersReviewed = reviewsConductedPrisonersReviewed.prisonersReviewed,
      ),
    )

    LOG.debug("KPIs updated for $day")
  }
}
