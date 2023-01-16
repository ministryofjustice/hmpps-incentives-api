package uk.gov.justice.digital.hmpps.incentivesapi.util

import java.time.Clock
import java.time.LocalDateTime

class CachedValue<T>(private val validForHours: Long = 24, var clock: Clock) {
  private var value: T? = null
  private var updatedAt: LocalDateTime? = null

  fun get(): T? {
    return if (isInvalid()) {
      null
    } else {
      value
    }
  }

  fun update(newValue: T) {
    value = newValue
    updatedAt = LocalDateTime.now(clock)
  }

  private fun isInvalid(): Boolean {
    val noValue = value == null || updatedAt == null
    val cacheExpired: Boolean = updatedAt != null && updatedAt!!.plusHours(validForHours) < LocalDateTime.now(clock)
    return noValue || cacheExpired
  }
}
