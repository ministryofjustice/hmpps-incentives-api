/**
 * Because Spring's javax validation annotation processing do not currently work with suspend function arguments,
 * this can be used instead to validate request parameters within resources.
 */
package uk.gov.justice.digital.hmpps.incentivesapi.util

import javax.validation.ValidationException

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
}

class ParameterValidationException(val errors: List<String>) :
  ValidationException("Invalid parameters: ${errors.joinToString(", ")}")
