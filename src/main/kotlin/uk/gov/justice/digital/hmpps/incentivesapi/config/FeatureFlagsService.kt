package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class FeatureFlagsService(
  @Value("\${feature.incentives-reference-data-source-of-truth:false}")
  val incentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase: Boolean,
) {

  fun isIncentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase(): Boolean {
    return incentiveReferenceDataMasteredOutsideNomisInIncentivesDatabase
  }
}
