package uk.gov.justice.digital.hmpps.incentivesapi.dto

interface PrisonerBasicInfo {
  val bookingId: Long
  val prisonerNumber: String
  val prisonId: String
}
