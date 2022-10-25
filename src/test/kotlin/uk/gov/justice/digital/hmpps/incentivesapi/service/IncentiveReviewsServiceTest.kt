package uk.gov.justice.digital.hmpps.incentivesapi.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReview
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerResponse
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonLocation

class IncentiveReviewsServiceTest {
  private val prisonApiService: PrisonApiService = mock()
  private val offenderSearchService: OffenderSearchService = mock()
  private val incentiveReviewsService = IncentiveReviewsService(offenderSearchService, prisonApiService)

  @Test
  fun `defaults to page 1, size 20`(): Unit = runBlocking {
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-1-2")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders()

    incentiveReviewsService.reviews("MDI", "MDI-1-2")
    verify(offenderSearchService, times(1)).findOffenders("MDI", "MDI-1-2", 0, 20)
  }

  @Test
  fun `accepts different pagination params`(): Unit = runBlocking {
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-1-2")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders()

    incentiveReviewsService.reviews("MDI", "MDI-1-2", 2, 40)
    verify(offenderSearchService, times(1)).findOffenders("MDI", "MDI-1-2", 1, 40)
  }

  @Test
  fun `maps responses from offender search`(): Unit = runBlocking {
    whenever(prisonApiService.getLocation(any())).thenReturnLocation("MDI-2-1")
    whenever(offenderSearchService.findOffenders(any(), any(), any(), any())).thenReturnOffenders(
      listOf(
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
      )
    )

    val reviews = incentiveReviewsService.reviews("MDI", "MDI-2-1")

    verify(offenderSearchService, times(1)).findOffenders(any(), eq("MDI-2-1"), any(), any())
    assertThat(reviews.locationDescription).isEqualTo("A houseblock")
    assertThat(reviews.reviewCount).isEqualTo(2)
    assertThat(reviews.reviews).isEqualTo(
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

  private fun OngoingStubbing<PrisonLocation>.thenReturnLocation(cellLocationPrefix: String) {
    val (prisonId, locationPrefix) = cellLocationPrefix.split("-", limit = 2)
    thenReturn(
      PrisonLocation(
        agencyId = prisonId,
        locationPrefix = locationPrefix,
        description = "A houseblock",
        locationType = "WING",
        userDescription = null,
      )
    )
  }

  private fun OngoingStubbing<OffenderSearchPrisonerResponse>.thenReturnOffenders(offenders: List<OffenderSearchPrisoner> = emptyList()) {
    thenReturn(
      OffenderSearchPrisonerResponse(
        totalElements = offenders.size,
        content = offenders,
      )
    )
  }
}
