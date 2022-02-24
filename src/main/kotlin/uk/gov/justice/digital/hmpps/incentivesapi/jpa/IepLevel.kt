package uk.gov.justice.digital.hmpps.incentivesapi.jpa

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.LocalDateTime

data class IepLevel(
  @Id
  val iepCode: String,

  val iepDescription: String,

  val sequence: Int = 99,

  val active: Boolean = true,

  @Transient
  @Value("false")
  val new: Boolean = true,

  val whenCreated: LocalDateTime? = null

) : Persistable<String> {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IepLevel) return false

    if (iepCode != other.iepCode) return false

    return true
  }

  override fun hashCode(): Int {
    return iepCode.hashCode()
  }

  override fun isNew(): Boolean = new

  override fun getId(): String = iepCode
}
