package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDate
import java.time.LocalDateTime

data class NextReviewDate(
  @Id
  val bookingId: Long,

  val nextReviewDate: LocalDate,

  @ReadOnlyProperty
  val whenCreated: LocalDateTime? = null,

  val whenUpdated: LocalDateTime? = null,

  @Transient
  @param:Value("false")
  val new: Boolean = true,
) : Persistable<Long> {
  override fun getId(): Long = bookingId
  override fun isNew(): Boolean = new
}
