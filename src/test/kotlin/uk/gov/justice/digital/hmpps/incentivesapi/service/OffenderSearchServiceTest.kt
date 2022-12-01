package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAlert
import java.time.LocalDate

class OffenderSearchServiceTest {
  @Test
  fun `offender search object indicates if ACCT open`() {
    val offenderWithHaAlert = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = listOf(
        PrisonerAlert(
          alertType = "H",
          alertCode = "HA",
          active = true,
          expired = false,
        ),
      ),
    )
    assertThat(offenderWithHaAlert.hasAcctOpen).isTrue

    val offenderWithoutHaAlert = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = listOf(
        PrisonerAlert(
          alertType = "X",
          alertCode = "XA",
          active = true,
          expired = false,
        ),
      ),
    )
    assertThat(offenderWithoutHaAlert.hasAcctOpen).isFalse

    val offenderWithoutAlerts = OffenderSearchPrisoner(
      prisonerNumber = "A1409AE",
      bookingId = 110001,
      firstName = "JAMES",
      middleNames = "",
      lastName = "HALLS",
      dateOfBirth = LocalDate.parse("1971-07-01"),
      receptionDate = LocalDate.parse("2020-07-01"),
      prisonId = "MDI",
      alerts = emptyList(),
    )
    assertThat(offenderWithoutAlerts.hasAcctOpen).isFalse
  }
}
