package uk.gov.justice.digital.hmpps.incentivesapi.integration

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient

/**
 * Guards the data dictionary published to GitHub Pages (see db/migration/V1_35__schema_comments.sql).
 *
 * Descriptions live in the database as COMMENT ON statements so SchemaSpy, the CSV export and any Glue
 * crawl share one source of truth. Nothing else would notice a new column arriving undocumented.
 *
 * Queries through [DatabaseClient] rather than JdbcTemplate: this service is R2DBC, spring-boot-starter-jdbc
 * is a runtime-only dependency for Flyway and there is no DataSource bean to autowire.
 *
 * Extends [SqsIntegrationTestBase] rather than [IntegrationTestBase] even though it needs nothing from
 * SQS: only that base starts LocalStack, and the application context wires HmppsQueueService.
 */
class SchemaCommentsTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var databaseClient: DatabaseClient

  @Test
  fun `every table has a description`() = runTest {
    val undocumented = databaseClient.sql(
      """
      SELECT c.relname AS name
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relkind = 'r'
        AND c.relname <> 'flyway_schema_history'
        AND obj_description(c.oid) IS NULL
      ORDER BY c.relname
      """.trimIndent(),
    ).map { row -> row.get("name", String::class.java) }.all().collectList().awaitSingle()

    assertThat(undocumented)
      .describedAs("tables with no COMMENT ON - add one in a new migration")
      .isEmpty()
  }

  @Test
  fun `every column has a description`() = runTest {
    assertThat(columnComments().filter { it.comment == null }.map { it.name })
      .describedAs("columns with no COMMENT ON - add one in a new migration")
      .isEmpty()
  }

  @Test
  fun `every column description carries a sensitivity classification`() = runTest {
    val misclassified = columnComments()
      .filter { it.comment != null && !SENSITIVITY.containsMatchIn(it.comment) }
      .map { it.name }

    assertThat(misclassified)
      .describedAs("column comments must end with one of $SENSITIVITY - see V1_35__schema_comments.sql")
      .isEmpty()
  }

  private data class ColumnComment(
    val name: String,
    val comment: String?,
  )

  private suspend fun columnComments(): List<ColumnComment> = databaseClient.sql(
    """
    SELECT c.table_name || '.' || c.column_name        AS name,
           col_description(pc.oid, c.ordinal_position) AS comment
    FROM information_schema.columns c
    JOIN pg_class pc
      ON pc.relname = c.table_name
     AND pc.relnamespace = 'public'::regnamespace
     AND pc.relkind = 'r'
    WHERE c.table_schema = 'public'
      AND c.table_name <> 'flyway_schema_history'
    ORDER BY c.table_name, c.ordinal_position
    """.trimIndent(),
  ).map { row ->
    ColumnComment(
      row.get("name", String::class.java)!!,
      row.get("comment", String::class.java),
    )
  }.all().collectList().awaitSingle()

  private companion object {
    val SENSITIVITY = Regex("""\[Sensitivity: (NONE|PERSONAL|STAFF|SPECIAL-CATEGORY|OFFICIAL-SENSITIVE)]$""")
  }
}
