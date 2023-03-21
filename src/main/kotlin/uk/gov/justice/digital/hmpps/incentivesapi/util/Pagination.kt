package uk.gov.justice.digital.hmpps.incentivesapi.util

import org.springframework.data.domain.PageRequest
import javax.validation.ValidationException
import kotlin.math.min

infix fun <T> List<T>.paginateWith(pageRequest: PageRequest): List<T> {
  if (pageRequest.pageNumber == 0 && isEmpty()) {
    return emptyList()
  }

  val offset = pageRequest.offset.toInt()
  if (offset > size) {
    throw ValidationException("Page number is out of range")
  }
  val limit = min(offset + pageRequest.pageSize, size)
  return this.subList(offset, limit)
}
