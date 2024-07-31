package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.OffenderSearchPrisoner
import uk.gov.justice.digital.hmpps.incentivesapi.dto.PrisonIncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerExtraInfo
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.PrisonerInfo
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import java.time.LocalDate
import java.time.LocalDateTime

fun prisonIncentiveLevel(
  prisonId: String,
  levelCode: String,
  active: Boolean? = null,
  defaultOnAdmission: Boolean? = null,
) = PrisonIncentiveLevel(
  prisonId = prisonId,
  levelCode = levelCode,

  active = active ?: (levelCode != "ENT"),
  defaultOnAdmission = defaultOnAdmission ?: (levelCode == "STD"),

  remandTransferLimitInPence = when (levelCode) {
    "BAS" -> 27_50
    "STD" -> 60_50
    else -> 66_00
  },
  remandSpendLimitInPence = when (levelCode) {
    "BAS" -> 275_00
    "STD" -> 605_00
    else -> 660_00
  },
  convictedTransferLimitInPence = when (levelCode) {
    "BAS" -> 5_50
    "STD" -> 19_80
    else -> 33_00
  },
  convictedSpendLimitInPence = when (levelCode) {
    "BAS" -> 55_00
    "STD" -> 198_00
    else -> 330_00
  },

  visitOrders = 2,
  privilegedVisitOrders = 1,
)

fun offenderSearchPrisoner(
  prisonerNumber: String = "A1244AB",
  bookingId: Long = 1234567L,
) = OffenderSearchPrisoner(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  firstName = "JAMES",
  middleNames = "",
  lastName = "HALLS",
  dateOfBirth = LocalDate.parse("1971-07-01"),
  receptionDate = LocalDate.parse("2020-07-01"),
  prisonId = "MDI",
  alerts = emptyList(),
)

fun prisonerExtraInfo(
  prisonerNumber: String = "A1244AB",
  bookingId: Long = 1234567L,
) = PrisonerExtraInfo(
  bookingId = bookingId,
  offenderNo = prisonerNumber,
  dateOfBirth = LocalDate.parse("1971-07-01"),
  receptionDate = LocalDate.parse("2020-07-01"),
  alerts = emptyList(),
)

fun prisonerAtLocation(bookingId: Long = 1234567, offenderNo: String = "A1234AA", agencyId: String = "MDI") =
  PrisonerInfo(bookingId, 1, "John", "Smith", offenderNo, agencyId)

fun prisonerIepLevel(
  bookingId: Long,
  iepCode: String = "STD",
  reviewTime: LocalDateTime,
  current: Boolean = true,
  reviewType: ReviewType = ReviewType.REVIEW,
) =
  IncentiveReview(
    levelCode = iepCode,
    prisonId = "MDI",
    bookingId = bookingId,
    current = current,
    reviewedBy = "TEST_STAFF1",
    reviewTime = reviewTime,
    prisonerNumber = "A1234AB",
    reviewType = reviewType,
  )
