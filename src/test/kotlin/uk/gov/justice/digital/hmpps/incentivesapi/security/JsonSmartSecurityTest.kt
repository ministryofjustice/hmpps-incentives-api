package uk.gov.justice.digital.hmpps.incentivesapi.security

import net.minidev.json.JSONValue
import net.minidev.json.parser.JSONParser
import net.minidev.json.parser.ParseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.StringReader

class JsonSmartSecurityTest {
  @Test
  fun `strict JSON parsing rejects a literal end marker followed by extra content`() {
    val valid = """{"value":1}"""
    val malicious = "$valid\u001agarbage"

    assertThat(JSONValue.isValidJsonStrict(valid)).isTrue()
    assertThat(JSONValue.isValidJsonStrict(StringReader(valid))).isTrue()
    assertThat(JSONValue.isValidJsonStrict(malicious)).isFalse()
    assertThat(JSONValue.isValidJsonStrict(StringReader(malicious))).isFalse()
    assertThatThrownBy { JSONValue.parseStrict(malicious) }.isInstanceOf(ParseException::class.java)
    assertThatThrownBy { JSONValue.parseStrict(StringReader(malicious)) }.isInstanceOf(ParseException::class.java)
  }

  @Test
  fun `strict parser backends reject literal end markers without rejecting escaped values`() {
    val invalidInputs =
      listOf(
        "1\u001agarbage",
        "true\u001afalse",
        """{"value":1}""" + "\u001a",
        """{"value":1}""" + " \t\u001agarbage",
        """{"value":"a""" + "\u001a" + """b"}""",
      )
    val validInput = """{"value":"a\u001ab"}"""

    listOf(JSONParser.MODE_RFC4627, JSONParser.MODE_STRICTEST).forEach { mode ->
      val parseInputs: List<(String) -> Any?> =
        listOf(
          { JSONParser(mode).parse(it) },
          { JSONParser(mode).parse(StringReader(it)) },
          { JSONParser(mode).parse(it.toByteArray()) },
          { JSONParser(mode).parse(ByteArrayInputStream(it.toByteArray())) },
        )

      parseInputs.forEach { parse ->
        invalidInputs.forEach { invalid ->
          assertThatThrownBy { parse(invalid) }.isInstanceOf(ParseException::class.java)
        }
        assertThat((parse(validInput) as Map<*, *>)["value"]).isEqualTo("a\u001ab")
      }
    }

    assertThat(JSONParser(JSONParser.MODE_RFC4627).parse("{} \t\n")).isEqualTo(emptyMap<String, Any>())
  }
}
