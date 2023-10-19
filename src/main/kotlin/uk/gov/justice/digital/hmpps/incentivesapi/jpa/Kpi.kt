package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDate
import java.time.LocalDateTime

data class Kpi(
  @Id
  val day: LocalDate,

  val overdueReviews: Int,
  val previousMonthReviewsConducted: Int,
  val previousMonthPrisonersReviewed: Int,

  @ReadOnlyProperty
  val whenCreated: LocalDateTime? = null,

  val whenUpdated: LocalDateTime? = null,

  @Transient
  @Value("false")
  val new: Boolean = true,
) : Persistable<LocalDate> {
  override fun getId(): LocalDate = day
  override fun isNew(): Boolean = new
}
