/**
 * Because Spring's javax validation annotation processing do not currently work with suspend function arguments,
 * this can be used instead to validate request parameters within resources.
 */
package uk.gov.justice.digital.hmpps.incentivesapi.util

import jakarta.validation.ValidationException

fun ensure(block: Ensure.() -> Unit) = Ensure().apply {
  block()
  if (errors.isNotEmpty()) {
    throw ParameterValidationException(errors)
  }
}

class Ensure {
  val errors: MutableList<String> = mutableListOf()

  fun <T : Comparable<T>> Pair<String, T>.isAtLeast(min: T): Pair<String, T> {
    if (second < min) {
      errors.add("`$first` must be at least $min")
    }
    return this
  }

  fun <T : Comparable<T>> Pair<String, T>.isAtMost(max: T): Pair<String, T> {
    if (second > max) {
      errors.add("`$first` must be at most $max")
    }
    return this
  }

  fun <T : CharSequence> Pair<String, T>.hasLengthAtLeast(min: Int): Pair<String, T> {
    if (second.length < min) {
      errors.add("`$first` must have length of at least $min")
    }
    return this
  }

  fun <T : CharSequence> Pair<String, T>.hasLengthAtMost(max: Int): Pair<String, T> {
    if (second.length > max) {
      errors.add("`$first` must have length of at most $max")
    }
    return this
  }

  fun <T, C : Collection<T>> Pair<String, C>.isNotEmpty(): Pair<String, C> {
    if (second.isEmpty()) {
      errors.add("`$first` list must not be empty")
    }
    return this
  }

  fun <T, C : Collection<T>> Pair<String, C>.hasSizeAtLeast(min: Int): Pair<String, C> {
    if (second.size < min) {
      errors.add("`$first` must have size of at least $min")
    }
    return this
  }

  fun <T, C : Collection<T>> Pair<String, C>.hasSizeAtMost(max: Int): Pair<String, C> {
    if (second.size > max) {
      errors.add("`$first` must have size of at most $max")
    }
    return this
  }
}

// NB: property `errors` is read in tests so must be public
class ParameterValidationException(
  val errors: List<String>,
) : ValidationException("Invalid parameters: ${errors.joinToString(", ")}")
