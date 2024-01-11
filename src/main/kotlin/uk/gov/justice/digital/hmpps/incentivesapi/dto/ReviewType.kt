package uk.gov.justice.digital.hmpps.incentivesapi.dto

enum class ReviewType {
  INITIAL,
  REVIEW,
  TRANSFER,
  MIGRATED,
  READMISSION,
}

interface IsRealReview {
  val reviewType: ReviewType

  fun isRealReview(): Boolean {
    // We consider `MIGRATED`/historic reviews as "real" as we don't have a way to discriminate
    // real incentives reviews in these cases.
    // Eventually all reviews will come from our DB and have a "review type".
    return listOf(ReviewType.REVIEW, ReviewType.MIGRATED).contains(reviewType)
  }
}
