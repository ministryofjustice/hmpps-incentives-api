package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime

/**
 * Database representation of an incentive level
 */
data class IncentiveLevel(
  @Id
  val code: String,
  val name: String,
  val description: String = "",
  val sequence: Int,
  val active: Boolean = true,
  val required: Boolean = false,
  @ReadOnlyProperty
  val whenCreated: LocalDateTime = LocalDateTime.now(),
  val whenUpdated: LocalDateTime = LocalDateTime.now(),

  @Transient
  @Value("false")
  val new: Boolean,
) : Persistable<String> {
  override fun getId(): String = code
  override fun isNew(): Boolean = new
}
