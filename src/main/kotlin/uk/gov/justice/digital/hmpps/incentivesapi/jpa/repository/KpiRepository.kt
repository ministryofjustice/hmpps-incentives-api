package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.Kpi
import java.time.LocalDate

@Repository
interface KpiRepository : CoroutineCrudRepository<Kpi, LocalDate>
