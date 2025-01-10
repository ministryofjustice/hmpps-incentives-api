package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import java.io.IOException
import java.net.ServerSocket

abstract class Testcontainer<InstanceType>(
  val containerName: String,
  val exposedPort: Int,
) {
  protected val log: Logger = LoggerFactory.getLogger(this::class.java)

  val instance: InstanceType? by lazy { startIfNotRunning() }

  open fun isRunning(): Boolean = try {
    val serverSocket = ServerSocket(exposedPort)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }

  open fun startIfNotRunning(): InstanceType? {
    if (isRunning()) {
      log.warn("Not starting $containerName container because port $exposedPort is already open")
      return null
    }
    return start()
  }

  abstract fun start(): InstanceType

  open fun setupProperties(instance: InstanceType, registry: DynamicPropertyRegistry) {}
}
