package uk.gov.justice.digital.hmpps.incentivesapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsIncentivesApi

fun main(args: Array<String>) {
  runApplication<HmppsIncentivesApi>(*args)
}
