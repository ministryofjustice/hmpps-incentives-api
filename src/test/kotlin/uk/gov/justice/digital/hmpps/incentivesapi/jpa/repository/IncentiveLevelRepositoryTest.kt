package uk.gov.justice.digital.hmpps.incentivesapi.jpa.repository

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.incentivesapi.helper.TestBase
import uk.gov.justice.digital.hmpps.incentivesapi.jpa.IncentiveLevel

@DataR2dbcTest
@ActiveProfiles("test")
@WithMockUser
class IncentiveLevelRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: IncentiveLevelRepository

  @Test
  fun `incentive levels properly set up`(): Unit = runBlocking {
    repository.findAllByOrderBySequence().map { it.sequence }.reduce { previous, sequence ->
      assertThat(sequence).isGreaterThan(previous)
      sequence
    }
    repository.findAllByActiveIsTrueOrderBySequence().reduce { previous, level ->
      assertThat(level.active).isTrue
      assertThat(level.sequence).isGreaterThan(previous.sequence)
      level
    }
  }

  private fun assertThatLevelCannotBeSaved(incentiveLevel: IncentiveLevel) {
    assertThatThrownBy {
      runBlocking { repository.save(incentiveLevel) }
    }.isInstanceOf(DataIntegrityViolationException::class.java)
  }

  @Test
  fun `level code cannot be blank`(): Unit = assertThatLevelCannotBeSaved(
    IncentiveLevel(
      code = "",
      description = "Blank",
      sequence = 10,
      new = true,
    )
  )

  @Test
  fun `level description cannot be blank`(): Unit = assertThatLevelCannotBeSaved(
    IncentiveLevel(
      code = "ABC",
      description = "",
      sequence = 10,
      new = true,
    )
  )
}
