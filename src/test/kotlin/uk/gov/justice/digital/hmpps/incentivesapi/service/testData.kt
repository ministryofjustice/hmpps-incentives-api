package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import java.time.LocalDate

fun offenderSearchPrisoner(
  prisonerNumber: String = "A1244AB",
  bookingId: Long = 1234567L,
) = OffenderSearchPrisoner(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  firstName = "JAMES",
  middleNames = "",
  lastName = "HALLS",
  status = "ACTIVE IN",
  inOutStatus = "IN",
  dateOfBirth = LocalDate.parse("1971-07-01"),
  receptionDate = LocalDate.parse("2020-07-01"),
  prisonId = "MDI",
  prisonName = "Moorland",
  cellLocation = "2-1-002",
  locationDescription = "Cell 2",
  alerts = emptyList(),
)

fun prisonerAtLocation(bookingId: Long = 1234567, offenderNo: String = "A1234AA", agencyId: String = "MDI") =
  PrisonerAtLocation(
    bookingId, 1, "John", "Smith", offenderNo, agencyId, 1
  )
