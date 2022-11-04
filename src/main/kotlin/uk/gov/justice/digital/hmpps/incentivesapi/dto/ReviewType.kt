package uk.gov.justice.digital.hmpps.incentivesapi.dto

enum class ReviewType {
  INITIAL, REVIEW, TRANSFER, MIGRATED
}

interface IsRealReview {
  val reviewType: ReviewType?

  fun isRealReview(): Boolean {
    // NOTE: Reviews from NOMIS wouldn't have a review type.
    // We consider these and `MIGRATED` reviews as "real" as we don't have a way to discriminate
    // real incentives reviews in these cases.
    // Eventually all reviews will come from our DB and have a "review type".
    return reviewType == null || listOf(ReviewType.REVIEW, ReviewType.MIGRATED).contains(reviewType)
  }

}
