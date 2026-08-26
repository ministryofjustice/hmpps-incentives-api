package uk.gov.justice.digital.hmpps.incentivesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.incentivesapi.dto.ReviewType
import java.io.File

/**
 * Writes reference-data.csv, the companion to the SchemaSpy report and data-dictionary.csv.
 *
 * Only covers the code lists that exist solely in Kotlin. Unlike the sibling services, most reference
 * data here is a real table: incentive_level holds the level codes and their names, so the schema report
 * and any ingestion already resolve iep_code and level_code without help. review_type is the exception -
 * a varchar(16) whose permitted values live only in the ReviewType enum.
 *
 * Needs no database: the values come from the enum itself, so the list cannot drift from the code.
 * A new enum value with no description fails the test rather than exporting a blank row.
 *
 * Excluded from normal test runs; run with `./gradlew -Pinit-db=true test` (see build.gradle.kts).
 */
class ExportReferenceData {

  @Test
  fun `exports reference data`() {
    val rows = mutableListOf<Row>()

    rows += enumRows(
      "prisoner_iep_level.review_type",
      ReviewType.entries,
      mapOf(
        ReviewType.INITIAL to
          "The prisoner's first incentive level on entering custody, rather than a review of an " +
          "existing one.",
        ReviewType.REVIEW to
          "A normal incentive review, scheduled or ad hoc. The only type conducted by a member of " +
          "staff in DPS.",
        ReviewType.TRANSFER to
          "The level was carried over automatically when the prisoner moved establishment. " +
          "Created by the service in response to a prisoner movement, not by a reviewer.",
        ReviewType.READMISSION to
          "The level was set automatically when the prisoner returned to custody. " +
          "Created by the service in response to a prisoner event, not by a reviewer.",
        ReviewType.MIGRATED to
          "Loaded from NOMIS, which did not record what prompted the review. " +
          "The bulk of the historical data has this type.",
      ),
      notes = {
        if (it == ReviewType.REVIEW || it == ReviewType.MIGRATED) {
          "counts as a real review for reporting (see IsRealReview)"
        } else {
          "not counted as a real review for reporting (see IsRealReview)"
        }
      },
    )

    val output = File(System.getProperty("referenceDataOutput") ?: "reference-data.csv")
    output.bufferedWriter().use { writer ->
      writer.write("column_ref,code,description,notes\n")
      rows.forEach { writer.write("${it.toCsv()}\n") }
    }
    println("Wrote ${rows.size} reference data rows to ${output.absolutePath}")
  }

  /**
   * Every value of the enum, with its description. Fails rather than exporting a blank row when a value
   * has no description - a new enum value is exactly the thing a consumer would otherwise not be able to
   * decode.
   */
  private fun <T : Enum<T>> enumRows(
    columnRef: String,
    values: List<T>,
    descriptions: Map<T, String>,
    notes: (T) -> String = { "" },
  ): List<Row> {
    assertThat(values.filterNot(descriptions::containsKey))
      .describedAs("$columnRef values with no description - add one in ExportReferenceData")
      .isEmpty()

    return values.map { Row(columnRef, it.name, descriptions.getValue(it), notes(it)) }
  }

  private data class Row(
    val columnRef: String,
    val code: String,
    val description: String,
    val notes: String = "",
  ) {
    fun toCsv() = listOf(columnRef, code, description, notes).joinToString(",") { escape(it) }

    private fun escape(value: String) = "\"${value.replace("\"", "\"\"")}\""
  }
}
