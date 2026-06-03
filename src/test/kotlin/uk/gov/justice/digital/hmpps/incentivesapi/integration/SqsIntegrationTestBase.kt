package uk.gov.justice.digital.hmpps.incentivesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.incentivesapi.config.LocalStackContainer
import uk.gov.justice.digital.hmpps.incentivesapi.config.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : IntegrationTestBase() {

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @MockitoSpyBean
  protected lateinit var hmppsSqsPropertiesSpy: HmppsSqsProperties

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  protected val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("domainevents")
      ?: throw MissingQueueException("HmppsTopic domainevents not found")
  }
  protected val domainEventsTopicSnsClient by lazy { domainEventsTopic.snsClient }
  protected val domainEventsTopicArn by lazy { domainEventsTopic.arn }

  protected val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") as HmppsQueue }
  protected val incentivesQueue by lazy { hmppsQueueService.findByQueueId("incentives") as HmppsQueue }
  protected val testDomainEventQueue by lazy { hmppsQueueService.findByQueueId("test") as HmppsQueue }

  fun HmppsSqsProperties.domaineventsTopicConfig() = topics["domainevents"]
    ?: throw MissingTopicException("domainevents has not been loaded from configuration properties")

  @BeforeEach
  fun cleanQueue() {
    listOf(auditQueue, incentivesQueue, testDomainEventQueue).forEach { it.purgeAndAwaitEmpty() }
  }

  /**
   * Purges a queue and waits for it to drain. SQS [PurgeQueueRequest] is asynchronous (and message counts are only
   * approximate), so this allows generous time for in-flight messages to clear rather than relying on the default
   * 10s await, which is prone to flaking when a preceding test leaves a message being consumed.
   */
  private fun HmppsQueue.purgeAndAwaitEmpty() {
    sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
    await.atMost(Duration.ofSeconds(30)) untilCallTo {
      sqsClient.countMessagesOnQueue(queueUrl).get()
    } matches { it == 0 }
  }

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? =
    incentivesQueue.sqsClient.countMessagesOnQueue(incentivesQueue.queueUrl).get()
}
