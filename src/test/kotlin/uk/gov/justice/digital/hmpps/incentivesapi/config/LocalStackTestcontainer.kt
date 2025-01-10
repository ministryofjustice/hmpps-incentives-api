package uk.gov.justice.digital.hmpps.incentivesapi.config

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

object LocalStackTestcontainer : Testcontainer<LocalStackContainer>("localstack", 4566) {
  override fun start(): LocalStackContainer {
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    log.info("Starting localstack")
    return LocalStackContainer(
      DockerImageName.parse("localstack/localstack").withTag("4"),
    ).apply {
      withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      withEnv("DEFAULT_REGION", "eu-west-2")
      start()
      followOutput(logConsumer)
    }
  }

  override fun setupProperties(instance: LocalStackContainer, registry: DynamicPropertyRegistry) {
    val localstackUrl = instance.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
    registry.add("hmpps.sqs.localstackUrl") { localstackUrl }
    registry.add("hmpps.sqs.region", instance::getRegion)
  }
}
