package uk.gov.justice.digital.hmpps.incentivesapi.util.flow

import kotlinx.coroutines.flow.Flow

/**
 * Collects given flow into a [destination] map indexed by the key returned from [keySelector] function
 * applied to each element. If any two elements would have the same key returned by [keySelector]
 * the last one gets added to the map.
 */
suspend fun <K, V> Flow<V>.toMap(destination: MutableMap<K, V> = LinkedHashMap(), keySelector: (V) -> K): Map<K, V> {
  collect { element ->
    destination[keySelector(element)] = element
  }
  return destination
}
