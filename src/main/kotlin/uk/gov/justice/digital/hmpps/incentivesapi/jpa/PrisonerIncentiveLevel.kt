package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IsRealReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import java.time.LocalDate
import java.time.LocalDateTime

@Table("prisoner_iep_level")
data class PrisonerIncentiveLevel(
  @Id
  val id: Long = 0,

  val bookingId: Long,
  val prisonerNumber: String,
  val prisonId: String,
  val locationId: String? = null,
  val reviewTime: LocalDateTime,
  val reviewedBy: String? = null,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean = true,
  override val reviewType: ReviewType = ReviewType.REVIEW,

  @Transient
  @Value("false")
  val new: Boolean = true,

  val whenCreated: LocalDateTime? = null,

) : Persistable<Long>, IsRealReview {

  override fun isNew(): Boolean = new

  override fun getId(): Long = id

  @Transient
  @ReadOnlyProperty
  val reviewDate: LocalDate = reviewTime.toLocalDate()
}
