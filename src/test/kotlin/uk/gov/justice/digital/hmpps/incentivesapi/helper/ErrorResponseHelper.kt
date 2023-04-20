package uk.gov.justice.digital.hmpps.incentivesapi.helper

import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.config.ErrorCode

fun <T : WebTestClient.ResponseSpec> T.expectErrorResponse(
  status: HttpStatus,
  vararg messages: String,
  errorCode: ErrorCode? = null,
  moreInfo: String? = null,
): T {
  expectStatus().isEqualTo(status)

  with(expectBody()) {
    jsonPath("$.status").isEqualTo(status.value())

    val userMessage = jsonPath("$.userMessage")
    for (message in messages) {
      userMessage.value<String> { assertThat(it).contains(message) }
    }

    if (errorCode != null) {
      jsonPath("$.errorCode").isEqualTo(errorCode.errorCode)
    } else {
      jsonPath("$.errorCode").doesNotExist()
    }

    if (moreInfo != null) {
      jsonPath("$.moreInfo").isEqualTo(moreInfo)
    } else {
      jsonPath("$.moreInfo").doesNotExist()
    }
  }

  return this
}
