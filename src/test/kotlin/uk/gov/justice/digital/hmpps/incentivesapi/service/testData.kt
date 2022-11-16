package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerAtLocation
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate
import java.time.LocalDateTime

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

fun prisonerIepLevel(bookingId: Long, reviewTime: LocalDateTime = LocalDateTime.now()) = PrisonerIepLevel(
  iepCode = "STD",
  prisonId = "MDI",
  locationId = "MDI-1-1-004",
  bookingId = bookingId,
  current = true,
  reviewedBy = "TEST_STAFF1",
  reviewTime = reviewTime,
  prisonerNumber = "A1234AB"
)
