package uk.gov.justice.digital.hmpps.incentivesapi.sar

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.SqsIntegrationTestBase

/**
 * The template endpoint serves the mustache report template to the SAR tool, which renders it against the data
 * endpoint's response. The endpoint itself is the library's; what is worth asserting here is that it is switched
 * on, that it serves exactly the file in this repo, and that it needs the SAR role.
 *
 * "Exactly" matters: the SAR tool identifies a template by its hash, and an unregistered hash suspends the
 * product, so the served bytes must be the registered file with nothing added or trimmed.
 */
class SubjectAccessRequestTemplateTest : SqsIntegrationTestBase() {

  @Test
  fun `requires a token`() {
    webTestClient.get().uri(TEMPLATE_URL)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `requires the SAR_DATA_ACCESS role`() {
    webTestClient.get().uri(TEMPLATE_URL)
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVES")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `serves the report template unchanged as plain text`() {
    val served = webTestClient.get().uri(TEMPLATE_URL)
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
      .expectBody(String::class.java)
      .returnResult().responseBody

    val file = javaClass.getResource(TEMPLATE_FILE)!!.readText()

    assertThat(served).isEqualTo(file)
    assertThat(served).startsWith("<h1 class=\"title\">Incentives</h1>")
  }

  private companion object {
    const val TEMPLATE_URL = "/subject-access-request/template"
    const val TEMPLATE_FILE = "/sar/templates/V1__sar_template.mustache"
  }
}
