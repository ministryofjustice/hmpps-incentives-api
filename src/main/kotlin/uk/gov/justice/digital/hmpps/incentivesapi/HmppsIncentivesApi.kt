package uk.gov.justice.digital.hmpps.incentivesapi

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import uk.gov.justice.digital.hmpps.incentivesapi.config.LOCK_AT_LEAST_FOR

/**
 * Used as the system oauth username when calling HMPPS apis
 * as well as the "audit module" for creating records in prison-api
 */
const val SYSTEM_USERNAME = "INCENTIVES_API"

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = LOCK_AT_LEAST_FOR)
class HmppsIncentivesApi

fun main(args: Array<String>) {
  runApplication<HmppsIncentivesApi>(*args)
}
