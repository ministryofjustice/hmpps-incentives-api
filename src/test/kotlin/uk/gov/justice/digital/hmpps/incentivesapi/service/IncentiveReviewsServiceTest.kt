package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerResponse

class IncentiveReviewsServiceTest {
  private val offenderSearchService: OffenderSearchService = mock()
  private val incentiveReviewsService = IncentiveReviewsService(offenderSearchService)

  @Test
  fun `defaults to page 1, size 20`(): Unit = runBlocking {
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturn(
      OffenderSearchPrisonerResponse(
        totalElements = 0,
        content = emptyList(),
      )
    )

    incentiveReviewsService.reviews("MDI", "1-2")
    verify(offenderSearchService, times(1)).findOffenders("MDI", "1-2", 0, 20)
  }

  @Test
  fun `accepts different pagination params`(): Unit = runBlocking {
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturn(
      OffenderSearchPrisonerResponse(
        totalElements = 0,
        content = emptyList(),
      )
    )

    incentiveReviewsService.reviews("MDI", "1-2", 2, 40)
    verify(offenderSearchService, times(1)).findOffenders("MDI", "1-2", 1, 40)
  }

  @Test
  fun `maps responses from offender search`(): Unit = runBlocking {
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturn(
      OffenderSearchPrisonerResponse(
        totalElements = 2,
        content = listOf(
          OffenderSearchPrisoner(
            prisonerNumber = "A1409AE",
            bookingId = "110001",
            firstName = "JAMES",
            middleNames = "",
            lastName = "HALLS",
            status = "ACTIVE IN",
            inOutStatus = "IN",
            prisonId = "MDI",
            prisonName = "Moorland (HMP & YOI)",
            cellLocation = "2-1-002",
            locationDescription = "Moorland (HMP & YOI)",
            alerts = listOf(
              OffenderSearchPrisonerAlert(
                alertType = "H",
                alertCode = "HA",
                active = true,
                expired = false,
              ),
            ),
          ),
          OffenderSearchPrisoner(
            prisonerNumber = "G6123VU",
            bookingId = "110002",
            firstName = "RHYS",
            middleNames = "BARRY",
            lastName = "JONES",
            status = "ACTIVE IN",
            inOutStatus = "IN",
            prisonId = "MDI",
            prisonName = "Moorland (HMP & YOI)",
            cellLocation = "2-1-003",
            locationDescription = "Moorland (HMP & YOI)",
            alerts = listOf(),
          ),
        ),
      )
    )

    val reviews = incentiveReviewsService.reviews("MDI", "2-1")

    verify(offenderSearchService, times(1)).findOffenders(any(), eq("2-1"), any(), any())
    Assertions.assertThat(reviews.reviewCount).isEqualTo(2)
    Assertions.assertThat(reviews.reviews).isEqualTo(
      listOf(
        IncentiveReview(
          prisonerNumber = "A1409AE",
          bookingId = 110001,
          firstName = "JAMES",
          lastName = "HALLS",
          acctOpenStatus = true,
        ),
        IncentiveReview(
          prisonerNumber = "G6123VU",
          bookingId = 110002,
          firstName = "RHYS",
          lastName = "JONES",
          acctOpenStatus = false,
        ),
      )
    )
  }
}
