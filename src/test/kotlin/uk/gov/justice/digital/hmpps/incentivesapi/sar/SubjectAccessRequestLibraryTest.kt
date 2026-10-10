package uk.gov.justice.digital.hmpps.incentivesapi.sar

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.NextReviewDateRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarApiDataTest
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarFlywaySchemaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarIntegrationTestHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarIntegrationTestHelperConfig
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarReportTest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Checks from the HMPPS SAR test library, against a fixed set of incentive reviews.
 *
 * These are approval tests: each compares against a committed file under `src/test/resources/sar/`, and the point
 * of them is what happens when one fails.
 *
 *  - `SarApiDataTest` / `SarReportTest` fail when the response or the rendered report changes. The rendered HTML
 *    is what the Offender SAR team review and sign off, so a diff here is the trigger for a conversation with them
 *    under the SAR change control process, not something to regenerate and move on from. The template was moved
 *    into this repo unchanged from the HAA team's central copy (IR-2039), so today's approved report is the
 *    baseline the re-baseline (IR-2044) will be compared with.
 *  - `SarFlywaySchemaTest` fails when a migration is added, forcing whoever adds it to decide whether the change
 *    affects what a prisoner's report should contain.
 *
 * `SarJpaEntitiesTest` is deliberately not implemented. This service persists through R2DBC, not JPA, so there is
 * no `EntityManager` for it to inspect; `SarFlywaySchemaTest` and the `[SAR: Y|N]` tag `SchemaCommentsTest`
 * requires on every column cover the same ground from the schema side.
 *
 * `SarFlywaySchemaTest` needs a JDBC `DataSource`, which an R2DBC application does not otherwise have. Flyway
 * still migrates over JDBC on startup (from `spring.flyway.url`), so the `DataSource` Spring Boot builds for the
 * `Flyway` bean is used - it points at the same test database the tests run against.
 *
 * Regenerate the files by running with `SAR_GENERATE_ACTUAL=true`, then read them before committing.
 */
@Import(SarIntegrationTestHelperConfig::class)
class SubjectAccessRequestLibraryTest :
  IncentiveLevelResourceTestBase(),
  SarApiDataTest,
  SarReportTest,
  SarFlywaySchemaTest {

  @Autowired
  private lateinit var incentiveReviewRepository: IncentiveReviewRepository

  @Autowired
  private lateinit var nextReviewDateRepository: NextReviewDateRepository

  @Autowired
  private lateinit var databaseClient: DatabaseClient

  @Autowired
  private lateinit var flyway: Flyway

  @Autowired
  private lateinit var sarIntegrationTestHelper: SarIntegrationTestHelper

  override fun getSarHelper(): SarIntegrationTestHelper = sarIntegrationTestHelper

  override fun getWebTestClientInstance(): WebTestClient = webTestClient

  override fun getDataSourceInstance(): DataSource = flyway.configuration.dataSource

  override fun getPrn(): String = PRISONER

  /**
   * Deserialise the response as plain JSON rather than into the service's own type. That is what the SAR tool
   * does - it fetches the JSON and renders the template against the parsed result - so the template helpers see
   * the strings they see in production.
   */
  override fun getContentType(): Class<*> = List::class.java

  /**
   * One prisoner across two bookings: an earlier spell in custody that ended on Basic, and the current one with
   * an initial level, a promotion to Enhanced, a demotion and a transfer. Both bookings have a next review date.
   *
   * The response includes each review's database id, so the id sequence is reset to keep the approved file
   * stable whatever other tests have inserted. Times are fixed, and the clock is the fixed test clock. Idempotent,
   * because the library calls this itself.
   */
  override fun setupTestData(): Unit = runBlocking {
    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    databaseClient.sql("SELECT setval(pg_get_serial_sequence('prisoner_iep_level', 'id'), 1, false)")
      .fetch().first().awaitSingleOrNull()

    incentiveReviewRepository.saveAll(
      listOf(
        review(
          EARLIER_BOOKING,
          "2020-05-01T10:00:00",
          "STD",
          ReviewType.INITIAL,
          "Default level assigned on arrival",
          "INCENTIVES_API",
        ),
        review(
          EARLIER_BOOKING,
          "2020-08-14T15:30:00",
          "BAS",
          ReviewType.REVIEW,
          "Refused to work and abusive to staff on several occasions.",
          "SAR_USER1",
          current = true,
        ),
        review(
          CURRENT_BOOKING,
          "2021-09-01T09:00:00",
          "STD",
          ReviewType.READMISSION,
          "Default level assigned on arrival",
          "INCENTIVES_API",
        ),
        review(
          CURRENT_BOOKING,
          "2021-12-10T11:45:00",
          "ENH",
          ReviewType.REVIEW,
          "Consistently good behaviour and a positive attitude in education.",
          "SAR_USER2",
        ),
        review(
          CURRENT_BOOKING,
          "2022-01-20T14:10:00",
          "STD",
          ReviewType.REVIEW,
          "Found with an unauthorised mobile phone.",
          "SAR_USER1",
        ),
        review(
          CURRENT_BOOKING,
          "2022-02-15T08:20:00",
          "STD",
          ReviewType.TRANSFER,
          "Level transferred from previous establishment",
          "INCENTIVES_API",
          prisonId = "LEI",
          current = true,
        ),
      ),
    ).collect()

    nextReviewDateRepository.save(
      NextReviewDate(bookingId = EARLIER_BOOKING, nextReviewDate = LocalDate.parse("2020-08-28"), new = true),
    )
    nextReviewDateRepository.save(
      NextReviewDate(bookingId = CURRENT_BOOKING, nextReviewDate = LocalDate.parse("2022-05-15"), new = true),
    )
  }

  /**
   * Reviews reference incentive levels, which the base class resets after each test, so they must go first.
   */
  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    incentiveReviewRepository.deleteAll()
    nextReviewDateRepository.deleteAll()
    super.tearDown()
  }

  private fun review(
    bookingId: Long,
    reviewTime: String,
    levelCode: String,
    reviewType: ReviewType,
    commentText: String,
    reviewedBy: String,
    prisonId: String = "MDI",
    current: Boolean = false,
  ) = IncentiveReview(
    bookingId = bookingId,
    prisonerNumber = PRISONER,
    reviewTime = LocalDateTime.parse(reviewTime),
    prisonId = prisonId,
    levelCode = levelCode,
    reviewType = reviewType,
    current = current,
    commentText = commentText,
    reviewedBy = reviewedBy,
    whenCreated = LocalDateTime.parse(reviewTime),
  )

  private companion object {
    const val PRISONER = "A1234SR"
    const val EARLIER_BOOKING = 1234500L
    const val CURRENT_BOOKING = 1234600L
  }
}
