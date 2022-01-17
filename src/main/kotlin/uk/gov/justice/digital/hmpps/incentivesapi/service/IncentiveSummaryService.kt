package uk.gov.justice.digital.hmpps.incentivesapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.incentivesapi.data.BehaviourSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.IncentiveLevelSummary
import uk.gov.justice.digital.hmpps.incentivesapi.data.PrisonerIncentiveSummary

@Service
class IncentiveSummaryService(
  private val prisonApiService: PrisonApiService
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
  fun getIncentivesSummaryByLocation(prisonId: String, locationId: String): BehaviourSummary {

    return dummyData()
  }
}

fun dummyData() = BehaviourSummary(
  prisonId = "LEI",
  locationId = "A",
  locationDescription = "A WING",
  totalIncentiveEncouragements = 6,
  totalIncentiveWarnings = 14,
  totalNegativeBehaviours = 35,
  totalPositiveBehaviours = 20,
  incentiveLevelSummary = listOf(
    IncentiveLevelSummary(
      level = "BAS",
      levelDescription = "Basic",
      numberAtThisLevel = 2,
      prisonerBehaviours = listOf(
        PrisonerIncentiveSummary(
          prisonerNumber = "A1234DG",
          bookingId = 2322121,
          imageId = 112121,
          firstName = "John",
          lastName = "Smith",
          daysOnLevel = 10,
          daysSinceLastReview = 50,
          positiveBehaviours = 6,
          incentiveEncouragements = 1,
          negativeBehaviours = 3,
          incentiveWarnings = 1,
          provenAdjudications = 2,
        ),
        PrisonerIncentiveSummary(
          prisonerNumber = "A1234DY",
          bookingId = 2322122,
          imageId = 112122,
          firstName = "David",
          lastName = "Jones",
          daysOnLevel = 4,
          daysSinceLastReview = 23,
          positiveBehaviours = 2,
          incentiveEncouragements = 0,
          negativeBehaviours = 10,
          incentiveWarnings = 6,
          provenAdjudications = 1,
        )
      )
    ),
    IncentiveLevelSummary(
      level = "STD",
      levelDescription = "Standard",
      numberAtThisLevel = 3,
      prisonerBehaviours = listOf(
        PrisonerIncentiveSummary(
          prisonerNumber = "A1334DG",
          bookingId = 2342121,
          imageId = 142121,
          firstName = "Trevor",
          lastName = "White",
          daysOnLevel = 2,
          daysSinceLastReview = 46,
          positiveBehaviours = 1,
          incentiveEncouragements = 0,
          negativeBehaviours = 20,
          incentiveWarnings = 14,
          provenAdjudications = 6,
        ),
        PrisonerIncentiveSummary(
          prisonerNumber = "A1234BB",
          bookingId = 2326122,
          imageId = 112622,
          firstName = "Peter",
          lastName = "Jones",
          daysOnLevel = 100,
          daysSinceLastReview = 4,
          positiveBehaviours = 7,
          incentiveEncouragements = 3,
          negativeBehaviours = 4,
          incentiveWarnings = 1,
          provenAdjudications = 2,
        ),
        PrisonerIncentiveSummary(
          prisonerNumber = "A1234UB",
          bookingId = 5326122,
          imageId = 512622,
          firstName = "Paul",
          lastName = "Phillips",
          daysOnLevel = 65,
          daysSinceLastReview = 43,
          positiveBehaviours = 14,
          incentiveEncouragements = 6,
          negativeBehaviours = 1,
          incentiveWarnings = 0,
          provenAdjudications = 3,
        )
      )
    )
  )
)
