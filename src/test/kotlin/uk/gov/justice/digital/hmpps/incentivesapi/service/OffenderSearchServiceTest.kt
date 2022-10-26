package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisonerAlert

class OffenderSearchServiceTest {
  @Test
  fun `offender search object indicates if ACCT open`() {
    val offenderWithHaAlert = OffenderSearchPrisoner(
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
    )
    assertThat(offenderWithHaAlert.acctOpen).isTrue

    val offenderWithoutHaAlert = OffenderSearchPrisoner(
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
          alertType = "X",
          alertCode = "XA",
          active = true,
          expired = false,
        ),
      ),
    )
    assertThat(offenderWithoutHaAlert.acctOpen).isFalse

    val offenderWithoutAlerts = OffenderSearchPrisoner(
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
      alerts = emptyList(),
    )
    assertThat(offenderWithoutAlerts.acctOpen).isFalse
  }
}