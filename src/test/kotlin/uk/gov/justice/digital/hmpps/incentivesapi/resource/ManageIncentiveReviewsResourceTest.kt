package uk.gov.justice.digital.hmpps.incentivesapi.resource

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.incentivesapi.dto.CreateIncentiveReviewRequest
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.integration.IncentiveLevelResourceTestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository.IncentiveReviewRepository
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Manage incentive reviews resource")
class ManageIncentiveReviewsResourceTest : IncentiveLevelResourceTestBase() {
  @Autowired
  private lateinit var repository: IncentiveReviewRepository

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    prisonApiMockServer.resetAll()
    prisonerSearchMockServer.resetAll()
    repository.deleteAll()
  }

  @AfterEach
  override fun tearDown(): Unit = runBlocking {
    prisonApiMockServer.resetRequests()
    prisonerSearchMockServer.resetRequests()
    repository.deleteAll()
    super.tearDown()
  }

  @Test
  fun `add incentive review fails when review time in future`() {
    val prisonerNumber = "A1244AB"
    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(bookingId = 1231232, prisonerNumber = prisonerNumber)

    val reviewTime = LocalDateTime.now(clock).plusDays(1)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("ENH", "Future Review", reviewTime = reviewTime))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `add incentive review for a prisoner by booking id`() {
    val bookingId = 3330000L
    val prisonerNumber = "A1234AC"

    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(prisonerNumber = prisonerNumber, bookingId = bookingId)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId = bookingId, prisonerNumber = prisonerNumber)

    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(CreateIncentiveReviewRequest("STD", "A comment"))
      .exchange()
      .expectStatus().isCreated

    val today = now(clock).format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = now(clock).plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "bookingId": $bookingId,
          "daysSinceReview": 0,
          "iepDate": "$today",
          "iepLevel": "Standard",
          "iepCode": "STD",
          "nextReviewDate": "$nextReviewDate",
          "iepDetails": [
            {
              "bookingId": $bookingId,
              "iepDate": "$today",
              "agencyId": "MDI",
              "iepLevel": "Standard",
              "iepCode": "STD",
              "comments": "A comment",
              "userId": "INCENTIVES_ADM",
              "auditModuleName": "INCENTIVES_API"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `add incentive review for a prisoner by prisoner number`() {
    val bookingId = 1294134L
    val prisonerNumber = "A1244AB"
    val prisonId = "MDI"

    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(bookingId, prisonerNumber)
    prisonApiMockServer.stubGetPrisonerExtraInfo(bookingId, prisonerNumber)

    val previousTime = now.minusDays(2)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(
        CreateIncentiveReviewRequest(
          iepLevel = "BAS",
          comment = "Basic Level",
          reviewType = ReviewType.INITIAL,
          reviewTime = previousTime,
        ),
      )
      .exchange()
      .expectStatus().isCreated

    val reviewTime = now.minusDays(1)
    webTestClient.post().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .bodyValue(
        CreateIncentiveReviewRequest(
          "ENH",
          "A different comment",
          reviewedBy = "DIFFERENT_USER",
          reviewTime = reviewTime,
        ),
      )
      .exchange()
      .expectStatus().isCreated

    val previousReviewTime = previousTime.format(DateTimeFormatter.ISO_DATE)
    val lastReviewTime = reviewTime.format(DateTimeFormatter.ISO_DATE)
    val nextReviewDate = reviewTime.plusYears(1).format(DateTimeFormatter.ISO_DATE)
    webTestClient.get().uri("/incentive-reviews/prisoner/$prisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "bookingId": $bookingId,
          "prisonerNumber": $prisonerNumber,
          "daysSinceReview": 1,
          "iepDate": "$lastReviewTime",
          "iepLevel": "Enhanced",
          "iepCode": "ENH",
          "nextReviewDate": "$nextReviewDate",
          "iepDetails": [
            {
              "prisonerNumber": $prisonerNumber,
              "bookingId": $bookingId,
              "iepDate": "$lastReviewTime",
              "agencyId": $prisonId,
              "iepLevel": "Enhanced",
              "iepCode": "ENH",
              "comments": "A different comment",
              "userId": "DIFFERENT_USER",
              "reviewType": "REVIEW",
              "auditModuleName": "INCENTIVES_API"
            },
            {
              "prisonerNumber": $prisonerNumber,
              "bookingId": $bookingId,
              "iepDate": "$previousReviewTime",
              "agencyId": $prisonId,
              "iepLevel": "Basic",
              "iepCode": "BAS",
              "comments": "Basic Level",
              "userId": "INCENTIVES_ADM",
              "reviewType": "INITIAL",
              "auditModuleName": "INCENTIVES_API"
            }
          ]
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  /**
   * The prisoner was mistakenly admitted on a new (higher) booking, where this service recorded the
   * prison's default level, and NOMIS staff have since put them back on the earlier booking where
   * they hold Enhanced. The repair endpoint corrects prisoners this happened to before the
   * `READMISSION_SWITCH_BOOKING` event was handled, and any the event is missed for since.
   */
  private val repairPrisonerNumber = "A1244AB"
  private val reinstatedBookingId = 1294100L
  private val mistakenBookingId = 1294200L

  private suspend fun givenPrisonerNeedingRepair(): Pair<Long, Long> {
    prisonerSearchMockServer.stubGetPrisonerInfoByPrisonerNumber(reinstatedBookingId, repairPrisonerNumber)
    prisonApiMockServer.stubGetPrisonerExtraInfo(reinstatedBookingId, repairPrisonerNumber)

    val reinstatedReview = repository.save(
      IncentiveReview(
        bookingId = reinstatedBookingId,
        prisonerNumber = repairPrisonerNumber,
        prisonId = "LEI",
        reviewedBy = "TEST_STAFF1",
        levelCode = "ENH",
        current = true,
        reviewTime = LocalDateTime.now(clock).minusDays(100),
      ),
    )
    val mistakenReview = repository.save(
      IncentiveReview(
        bookingId = mistakenBookingId,
        prisonerNumber = repairPrisonerNumber,
        prisonId = "MDI",
        reviewedBy = "INCENTIVES_API",
        levelCode = "STD",
        current = true,
        reviewType = ReviewType.INITIAL,
        reviewTime = LocalDateTime.now(clock).minusDays(1),
      ),
    )
    return reinstatedReview.id to mistakenReview.id
  }

  @Test
  fun `repair after booking switch requires the write scope`() {
    webTestClient.post().uri("/incentive-reviews/prisoner/$repairPrisonerNumber/repair-booking-switch")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `repair after booking switch requires authorisation`() {
    webTestClient.post().uri("/incentive-reviews/prisoner/$repairPrisonerNumber/repair-booking-switch")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `repair after booking switch moves the current level back onto the reinstated booking`(): Unit = runBlocking {
    // Given
    val (reinstatedReviewId, mistakenReviewId) = givenPrisonerNeedingRepair()

    // When
    webTestClient.post().uri("/incentive-reviews/prisoner/$repairPrisonerNumber/repair-booking-switch")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "prisonerNumber": "$repairPrisonerNumber",
          "bookingId": $reinstatedBookingId,
          "outcome": "REPAIRED",
          "dryRun": false,
          "levelCodeBefore": "STD",
          "levelCodeAfter": "ENH",
          "reviewIdsStoodDown": [$mistakenReviewId]
        }
        """,
        JsonCompareMode.LENIENT,
      )

    // Then
    assertThat(repository.findById(mistakenReviewId)?.current).isFalse()
    assertThat(repository.findById(reinstatedReviewId)?.current).isTrue()

    // and — the assertion that matters — the prisoner now reads back as Enhanced. This is what
    // prisoner-search and the NOMIS reconciliation both call, so flipping the flags is only useful
    // insofar as it changes this. The stood-down review is newer by review time and would win a
    // naive "newest wins" read.
    webTestClient.get().uri("/incentive-reviews/prisoner/$repairPrisonerNumber")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "id": $reinstatedReviewId,
          "bookingId": $reinstatedBookingId,
          "iepCode": "ENH",
          "iepLevel": "Enhanced"
        }
        """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `repair after booking switch is a safe no-op when the prisoner is already correct`(): Unit = runBlocking {
    // Given - so the repair can be re-run over a list without checking each prisoner first
    val (reinstatedReviewId, mistakenReviewId) = givenPrisonerNeedingRepair()
    val uri = "/incentive-reviews/prisoner/$repairPrisonerNumber/repair-booking-switch"
    webTestClient.post().uri(uri)
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .exchange()
      .expectStatus().isOk

    // When - repaired a second time
    webTestClient.post().uri(uri)
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "outcome": "NOTHING_TO_DO",
          "levelCodeBefore": "ENH",
          "levelCodeAfter": "ENH",
          "reviewIdsStoodDown": [],
          "reviewIdReinstated": null
        }
        """,
        JsonCompareMode.LENIENT,
      )

    // Then
    assertThat(repository.findById(mistakenReviewId)?.current).isFalse()
    assertThat(repository.findById(reinstatedReviewId)?.current).isTrue()
  }

  @Test
  fun `repair after booking switch dry run reports the change without making it`(): Unit = runBlocking {
    // Given
    val (reinstatedReviewId, mistakenReviewId) = givenPrisonerNeedingRepair()

    // When
    webTestClient.post().uri("/incentive-reviews/prisoner/$repairPrisonerNumber/repair-booking-switch?dry-run=true")
      .headers(setAuthorisation(roles = listOf("ROLE_INCENTIVE_REVIEWS"), scopes = listOf("read", "write")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        // language=json
        """
        {
          "outcome": "REPAIRED",
          "dryRun": true,
          "levelCodeBefore": "STD",
          "levelCodeAfter": "ENH",
          "reviewIdsStoodDown": [$mistakenReviewId]
        }
        """,
        JsonCompareMode.LENIENT,
      )

    // Then - the reviews are untouched
    assertThat(repository.findById(mistakenReviewId)?.current).isTrue()
    assertThat(repository.findById(reinstatedReviewId)?.current).isTrue()
  }
}
