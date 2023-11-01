package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveLevel
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveReview
import java.time.LocalDate

fun IncentiveReview.toIepDetail(incentiveLevels: Map<String, IncentiveLevel>) =
  IncentiveReviewDetail(
    id = id,
    bookingId = bookingId,
    iepDate = reviewTime.toLocalDate(),
    iepTime = reviewTime,
    agencyId = prisonId,
    iepLevel = incentiveLevels[levelCode]?.name ?: "Unmapped",
    iepCode = levelCode,
    reviewType = reviewType,
    comments = commentText,
    userId = reviewedBy,
    locationId = locationId,
    prisonerNumber = prisonerNumber,
    auditModuleName = SYSTEM_USERNAME,
  )

fun List<NextReviewDate>.toMapByBookingId(): Map<Long, LocalDate> {
  return associate { it.bookingId to it.nextReviewDate }
}
