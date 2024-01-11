package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime

/**
 * Database representation of an incentive level’s configuration in a prison
 */
@Suppress("ktlint:standard:no-blank-line-in-list", "ktlint:standard:discouraged-comment-location")
data class PrisonIncentiveLevel(
  @Id
  val id: Int = 0,
  val levelCode: String,
  @ReadOnlyProperty
  val levelName: String? = null, // normally retrieved from join to IncentiveLevel
  val prisonId: String,
  val active: Boolean = true,
  val defaultOnAdmission: Boolean = false,
  @ReadOnlyProperty
  val whenCreated: LocalDateTime = LocalDateTime.now(),
  val whenUpdated: LocalDateTime = LocalDateTime.now(),

  val remandTransferLimitInPence: Int,
  val remandSpendLimitInPence: Int,
  val convictedTransferLimitInPence: Int,
  val convictedSpendLimitInPence: Int,

  val visitOrders: Int,
  val privilegedVisitOrders: Int,

  @Transient
  @Value("false")
  val new: Boolean,
) : Persistable<Int> {
  override fun getId(): Int = id

  override fun isNew(): Boolean = new
}
