package uk.gov.justice.digital.hmpps.incentivesapi.integration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.helper.JwtAuthHelper
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock.HmppsAuthMockServer
import uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock.OffenderSearchMockServer
import uk.gov.justice.digital.hmpps.incentivesapi.integration.wiremock.PrisonApiMockServer

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase : TestBase() {

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  companion object {
    @JvmField
    val prisonApiMockServer = PrisonApiMockServer()

    @JvmField
    val hmppsAuthMockServer = HmppsAuthMockServer()

    @JvmField
    val offenderSearchMockServer = OffenderSearchMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      hmppsAuthMockServer.start()
      hmppsAuthMockServer.stubGrantToken()

      prisonApiMockServer.start()
      offenderSearchMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      offenderSearchMockServer.stop()
      prisonApiMockServer.stop()
      hmppsAuthMockServer.stop()
    }
  }

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  protected fun setAuthorisation(
    user: String = "INCENTIVES_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  fun readFile(file: String): String = this.javaClass.getResource(file).readText()
}
