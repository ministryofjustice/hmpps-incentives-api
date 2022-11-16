package uk.gov.justice.digital.hmpps.incentivesapi.service

import uk.gov.justice.digital.hmpps.incentivesapi.dto.IepDetail
import uk.gov.justice.digital.hmpps.incentivesapi.dto.prisonapi.IepLevel
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.PrisonerIepLevel
import java.time.LocalDate

fun PrisonerIepLevel.translate(incentiveLevels: Map<String, IepLevel>) =
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
    auditModuleName = "INCENTIVES_API",
  )

fun List<PrisonerIepLevel>.toIepDetails(iepLevels: Map<String, IepLevel>): List<IepDetail> {
  return this.map { review -> review.translate(iepLevels) }
}

fun List<NextReviewDate>.toMap(): Map<Long, LocalDate> {
  return this.associate { it.bookingId to it.nextReviewDate }
}
