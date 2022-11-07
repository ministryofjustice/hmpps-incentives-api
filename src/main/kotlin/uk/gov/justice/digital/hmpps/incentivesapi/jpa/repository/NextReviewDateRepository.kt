package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.NextReviewDate

@Repository
interface NextReviewDateRepository : CoroutineCrudRepository<NextReviewDate, Long>
