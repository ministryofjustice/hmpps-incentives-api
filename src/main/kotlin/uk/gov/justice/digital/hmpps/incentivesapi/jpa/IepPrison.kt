package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDate
import java.time.LocalDateTime

data class IepPrison(
  @Id
  val id: Long = 0,

  val iepCode: String,
  val prisonId: String,
  val active: Boolean = true,
  val expiryDate: LocalDate? = null,
  val defaultIep: Boolean = false,

  @Transient
  @Value("false")
  val new: Boolean = true,

  val whenCreated: LocalDateTime? = null

) : Persistable<Long> {

  override fun isNew(): Boolean = new

  override fun getId(): Long = id
}
