package uk.gov.justice.digital.hmpps.incentivesapi.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import javax.validation.ValidationException

class PaginationTests {
  @Test
  fun `slices a list into pages`() {
    val list = listOf("a", "b", "c", "d", "e")
    var page = list paginateWith PageRequest.of(0, 2)
    assertThat(page).isEqualTo(listOf("a", "b"))
    page = list paginateWith PageRequest.of(1, 2)
    assertThat(page).isEqualTo(listOf("c", "d"))
    page = list paginateWith PageRequest.of(2, 2)
    assertThat(page).isEqualTo(listOf("e"))
  }

  @Test
  fun `throws an error for pages starting beyond end of list`() {
    val empty = emptyList<Int>()
    assertThatThrownBy {
      empty paginateWith PageRequest.of(1, 10)
    }.isInstanceOf(ValidationException::class.java)
      .hasMessage("Page number is out of range")

    val nonEmpty = listOf(1, 2, 3)
    assertThatThrownBy {
      nonEmpty paginateWith PageRequest.of(1, 10)
    }.isInstanceOf(ValidationException::class.java)
      .hasMessage("Page number is out of range")
  }

  @Test
  fun `returns an empty page 0 for an empty list`() {
    val empty = emptyList<Int>()
    val page = empty paginateWith PageRequest.of(0, 10)
    assertThat(page).isEmpty()
  }
}
