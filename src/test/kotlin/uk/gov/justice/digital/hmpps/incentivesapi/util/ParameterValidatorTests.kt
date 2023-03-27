package uk.gov.justice.digital.hmpps.incentivesapi.util

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ParameterValidatorTests {
  @Test
  fun `does not throw errors when nothing is expected`() {
    ensure {}
  }

  @Test
  fun `does not throw errors when parameters have no assertions`() {
    ensure {
      ("param" to 123)
    }
  }

  @Test
  fun `does not throw errors when parameters meet assertions`() {
    ensure {
      ("param" to 123).isAtLeast(100).isAtMost(200)
      ("param2" to "abc").hasLengthAtLeast(1).hasLengthAtMost(5)
      ("collection" to listOf(1, 2)).hasSizeAtLeast(1).hasSizeAtMost(3)
    }
  }

  @Test
  fun `throws an error when a parameter does not meet assertions`() {
    assertThatThrownBy {
      ensure {
        ("param1" to 123).isAtLeast(200)
        ("param2" to 500).isAtLeast(200)
      }
    }.isInstanceOf(ParameterValidationException::class.java)
      .hasMessage("Invalid parameters: `param1` must be at least 200")
      .hasFieldOrPropertyWithValue("errors", listOf("`param1` must be at least 200"))
  }

  @Test
  fun `throws an error when multiple parameters do not meet assertions`() {
    assertThatThrownBy {
      ensure {
        ("param1" to 123).isAtLeast(200)
        ("param2" to "abc").hasLengthAtLeast(5).hasLengthAtMost(5)
        ("collection" to listOf(1, 2)).hasSizeAtLeast(3).hasSizeAtMost(5)
      }
    }.isInstanceOf(ParameterValidationException::class.java)
      .hasMessage("Invalid parameters: `param1` must be at least 200, `param2` must have length of at least 5, `collection` must have size of at least 3")
      .hasFieldOrPropertyWithValue(
        "errors",
        listOf(
          "`param1` must be at least 200",
          "`param2` must have length of at least 5",
          "`collection` must have size of at least 3",
        ),
      )
  }
}
