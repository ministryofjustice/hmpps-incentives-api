package uk.gov.justice.digital.hmpps.incentivesapi.integration

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  fun setLocalStackProperties(localStackContainer: LocalStackContainer, registry: DynamicPropertyRegistry) {
    val localstackUrl = localStackContainer.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
    val region = localStackContainer.region
    registry.add("hmpps.sqs.localstackUrl") { localstackUrl }
    registry.add("hmpps.sqs.region") { region }
  }

  private fun startLocalstackIfNotRunning(): LocalStackContainer? {
    if (localstackIsRunning()) return null
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    return LocalStackContainer(
      DockerImageName.parse("localstack/localstack").withTag("2.0"),
    ).apply {
      waitingFor(
        Wait.forLogMessage(".*Running on.*", 1),
      )
      start()
      followOutput(logConsumer)
    }
  }

  private fun localstackIsRunning(): Boolean =
    try {
      val serverSocket = ServerSocket(4566)
      serverSocket.localPort == 0
    } catch (e: IOException) {
      true
    }
}
