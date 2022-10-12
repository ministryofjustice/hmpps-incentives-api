package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

enum class ReviewAddedSyncMechanism {
  DOMAIN_EVENT,
  PRISON_API,
}

@Service
class FeatureFlagsService(
  @Value("\${feature.reviewAddedSyncMechanism}")
  val reviewAddedSyncMechanism: String,
) {

  fun reviewAddedSyncMechanism(): ReviewAddedSyncMechanism {
    return ReviewAddedSyncMechanism.valueOf(reviewAddedSyncMechanism)
  }

}
