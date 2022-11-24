package uk.gov.justice.digital.hmpps.incentivesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Used as the system oauth username when calling HMPPS apis
 * as well as the "audit module" for creating records in prison-api
 */
const val SYSTEM_USERNAME = "INCENTIVES_API"

@SpringBootApplication()
class HmppsIncentivesApi

fun main(args: Array<String>) {
  runApplication<HmppsIncentivesApi>(*args)
}
