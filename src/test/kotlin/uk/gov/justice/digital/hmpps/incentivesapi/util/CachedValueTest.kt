package uk.gov.justice.digital.hmpps.incentivesapi.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class CachedValueTest {

  private var clockBefore: Clock = Clock.fixed(Instant.parse("2022-08-01T12:45:00.00Z"), ZoneId.systemDefault())
  private var clockAfter: Clock = Clock.offset(clockBefore, Duration.ofHours(2))
  private val cachedValue = CachedValue<String>(validForHours = 1, clockBefore)

  @Test
  fun `get() returns the value when is not expired`() {
    cachedValue.update("value in cache")
    assertThat(cachedValue.get()).isEqualTo("value in cache")
  }

  @Test
  fun `get() returns null when cache is empty`() {
    assertThat(cachedValue.get()).isNull()
  }

  @Test
  fun `get() returns null when cache is expired`() {
    cachedValue.update("will expire")
    assertThat(cachedValue.get()).isEqualTo("will expire")

    cachedValue.clock = clockAfter

    assertThat(cachedValue.get()).isNull()
  }

  @Test
  fun `update() updates cached value`() {
    assertThat(cachedValue.get()).isNull()

    cachedValue.update("new value")
    assertThat(cachedValue.get()).isEqualTo("new value")
  }
}
