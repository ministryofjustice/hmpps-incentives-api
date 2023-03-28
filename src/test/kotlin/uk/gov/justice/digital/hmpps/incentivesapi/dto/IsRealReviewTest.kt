package uk.gov.justice.digital.hmpps.incentivesapi.dto

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class IsRealReviewTest {

  inner class SomeReviewClass(override val reviewType: ReviewType) : IsRealReview

  @Test
  fun `when reviewType is REVIEW it is a real review`() {
    val review = SomeReviewClass(reviewType = ReviewType.REVIEW)
    assertThat(review.isRealReview()).isTrue
  }

  @Test
  fun `when reviewType is MIGRATED (eg data migrated from NOMIS) is considered real`() {
    val review = SomeReviewClass(reviewType = ReviewType.MIGRATED)
    assertThat(review.isRealReview()).isTrue
  }

  @Test
  fun `when reviewType is INITIAL (admission) it is not considered real review`() {
    val review = SomeReviewClass(reviewType = ReviewType.INITIAL)
    assertThat(review.isRealReview()).isFalse
  }

  @Test
  fun `when reviewType is TRANSFER it is not considered real review`() {
    val review = SomeReviewClass(reviewType = ReviewType.TRANSFER)
    assertThat(review.isRealReview()).isFalse
  }
}
