package uk.gov.justice.digital.hmpps.incentivesapi.task

import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.incentivesapi.config.defaultLockAtLeastFor
import uk.gov.justice.digital.hmpps.incentivesapi.config.defaultLockAtMostFor
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.KpiRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.ReviewsConductedPrisonersReviewed
import java.time.LocalDate

@Component
class UpdateKpis(
  private val kpiRepository: KpiRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.update-kpis.cron}")
  @SchedulerLock(
    name = "INC - Update KPIs",
    lockAtLeastFor = defaultLockAtLeastFor,
    lockAtMostFor = defaultLockAtMostFor,
  )
  fun updateKpis() {
    val day = LocalDate.now()
    LOG.debug("Updating KPIs for $day...")

    val reviewsConductedPrisonersReviewed = getReviewsConductedPrisonersReviewed(day)
    // TODO
    val numberOfPrisonersOverdue = getNumberOfPrisonersOverdue()

    // Logs result, it's a different way for us to get the numbers, possibly easier
    LOG.info("KPIs for $day. Reviews conducted = ${reviewsConductedPrisonersReviewed.reviewsConducted}")
    LOG.info("KPIs for $day. Prisoners reviewed = ${reviewsConductedPrisonersReviewed.prisonersReviewed}")
    LOG.info("KPIs for $day. Prisoners overdue = $numberOfPrisonersOverdue")

    // Store the result in the DB table
    runBlocking {
      kpiRepository.save(
        Kpi(
          day = day,
          overdueReviews = numberOfPrisonersOverdue,
          previousMonthReviewsConducted = reviewsConductedPrisonersReviewed.reviewsConducted,
          previousMonthPrisonersReviewed = reviewsConductedPrisonersReviewed.prisonersReviewed,
        ),
      )
    }

    LOG.debug("KPIs updated for $day")
  }

  private fun getReviewsConductedPrisonersReviewed(day: LocalDate): ReviewsConductedPrisonersReviewed = runBlocking {
    kpiRepository.getNumberOfReviewsConductedAndPrisonersReviewed(day)
  }

  // TODO: Get number of prisoners overdue a review
  private fun getNumberOfPrisonersOverdue(): Int {
    // TODO
    return 0
  }
}
