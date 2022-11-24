package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate

fun PrisonerIepLevel.toIepDetail(incentiveLevels: Map<String, IepLevel>) =
  IepDetail(
    id = id,
    bookingId = bookingId,
    iepDate = reviewTime.toLocalDate(),
    iepTime = reviewTime,
    agencyId = prisonId,
    iepLevel = incentiveLevels[iepCode]?.iepDescription ?: "Unmapped",
    iepCode = iepCode,
    reviewType = reviewType,
    comments = commentText,
    userId = reviewedBy,
    locationId = locationId,
    prisonerNumber = prisonerNumber,
    auditModuleName = SYSTEM_USERNAME,
  )

fun List<PrisonerIepLevel>.toIepDetails(iepLevels: Map<String, IepLevel>): List<IepDetail> {
  return map { review -> review.toIepDetail(iepLevels) }
}

fun List<NextReviewDate>.toMapByBookingId(): Map<Long, LocalDate> {
  return associate { it.bookingId to it.nextReviewDate }
}
