package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime

data class PrisonerIepLevel(
  @Id
  val id: Long = 0,

  val bookingId: Long,
  val sequence: Int,
  val prisonerNumber: String,
  val prisonId: String,
  val locationId: String,
  val reviewTime: LocalDateTime,
  val reviewedBy: String,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean = true,

  @Transient
  @Value("false")
  val new: Boolean = true,

  val whenCreated: LocalDateTime? = null

) : Persistable<Long> {

  override fun isNew(): Boolean = new

  override fun getId(): Long = id
}
