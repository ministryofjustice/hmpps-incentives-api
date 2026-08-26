# HMPPS Incentives API

[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-incentives-api)
[![Runbook](https://img.shields.io/badge/runbook-view-172B4D.svg?logo=confluence)](https://dsdmoj.atlassian.net/wiki/spaces/NOM/pages/1739325587/DPS+Runbook)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://incentives-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-incentives-api/main/async-api.yml&readOnly)

This application is the REST api and database that owns incentive level information for prisons
and prisoner incentive reviews.

## Running locally

For running locally against docker instances of the following services:
- [hmpps-auth](https://github.com/ministryofjustice/hmpps-auth)
- [prison-api](https://github.com/ministryofjustice/prison-api)
- run this application independently e.g. in IntelliJ

```shell
docker compose up --scale hmpps-incentives-api=0
```

### Running all services including this service

```shell
docker compose up
```

### Running locally against dev/T3 services

This is straight-forward as authentication is delegated down to the calling services in `dev` environment.

Use all environment variables starting with `API_BASE_URL_` from [helm chart values](./helm_deploy/values-dev.yaml).
Choose a suitable hmpps-auth oauth client, for instance from kubernetes `hmpps-incentives-api` secret and add
`INCENTIVES_API_CLIENT_ID` and `INCENTIVES_API_CLIENT_SECRET`.

Start the database and other required services in docker with:

```shell
docker compose -f docker-compose-local.yml up
```

## Testing and linting

Run unit and integration tests with:

```shell
./gradlew test
```

Run automatic lint fixes:

```shell
./gradlew ktlintformat
```

## Publishing a received message to your local instance

This assumes you have the [AWS CLI](https://aws.amazon.com/cli/) installed

1. Follow [Running Locally](#running-locally) to bring up the service and docker containers
2. Find the ARN of the Domain Events topic created in your localstack instance and update the `topic-arn` parameter in the command below
    ```shell
    aws --endpoint-url=http://localhost:4566 sns publish \
        --topic-arn arn:aws:sns:eu-west-2:000000000000:11111111-2222-3333-4444-555555555555 \
        --message-attributes '{
          "eventType": { "DataType": "String", "StringValue": "prisoner-offender-search.prisoner.received" }
        }' \
        --message '{
          "version": "1.0",
          "occurredAt": "2020-02-12T15:14:24.125533+00:00",
          "publishedAt": "2020-02-12T15:15:09.902048716+00:00",
          "description": "A prisoner has been received into a prison with reason: admission on new charges",
          "additionalInformation": {
            "nomsNumber": "A0289IR",
            "prisonId": "MDI",
            "reason": "NEW_ADMISSION"
          }
        }'
    ```
3. Paste the command into your terminal

**NOTE**: If you get a `Topic does not exist` error, it may mean your default AWS profile points to a different region,
be sure it points to `eu-west-2` either by changing your default profile or by passing `--region eu-west-1` to the
command above.

## Connecting to AWS resources from a local port

There are custom gradle tasks that make it easier to connect to AWS resources (RDS and ElastiCache Redis)
in Cloud Platform from a local port:

```shell
./gradlew portForwardRDS
# and
./gradlew portForwardRedis
```

These could be useful to, for instance, clear out a development database or edit data live.

They require `kubectl` to already be set up to access the kubernetes cluster;
essentially these tasks are just convenience wrappers.

Both accept the `--environment` argument to select between `dev`, `preprod` and `prod` namespaces
or prompt for user input when run.

Both also accept the `--port` argument to choose a different local port, other than the resource’s default.

## Database schema

A browsable schema report is published from `main` to
[ministryofjustice.github.io/hmpps-incentives-api/schema-spy-report](https://ministryofjustice.github.io/hmpps-incentives-api/schema-spy-report/),
along with two CSV exports for the MOJ Data Catalogue:

| File | Contents |
|------|----------|
| `data-dictionary.csv` | Every table and column, with its description, sensitivity classification, type, nullability, PK and FK. Excludes `shedlock` and `flyway_schema_history` |
| `reference-data.csv` | The code lists that exist only in Kotlin. Most reference data here is a real table (`incentive_level`), so this covers only `review_type` |

The report shows every table and column, with types, nullability, primary and foreign keys, and ER
diagrams. Share it rather than a hand-written description when explaining the schema — to the Data Hub
transition team, or when working out what a subject access request covers. It supersedes the
hand-maintained table list in [the Data Hub assessment](doc/HMPPS%20Data%20Hub%20Data%20Assessment%20Incentives.md),
which cannot help going stale.

It is generated from a database built by Flyway, so it cannot drift from the migrations. This service
reads through R2DBC at runtime, but Flyway still migrates over JDBC on startup, so booting the context
is all that is needed. To regenerate it locally:

```shell
docker compose -f docker-compose-schema-spy.yml up -d --wait
./gradlew -Pinit-db=true test --tests '*InitialiseDatabase' --tests '*ExportReferenceData'
docker run --rm --network host -v /tmp/schemaspy:/output schemaspy/schemaspy:6.2.4 \
  -t pgsql -host localhost -port 5432 -db incentives -s public \
  -u incentives -p incentives -vizjs
scripts/generate-data-dictionary.sh
```

If you change `V1_35__schema_comments.sql` while the compose database is still up, Flyway will refuse to
start with a checksum mismatch — the container persists between runs. Recreate it with
`docker compose -f docker-compose-schema-spy.yml down -v` before re-running.

### Table and column descriptions

Descriptions live in the database as `COMMENT ON` statements, applied by
`db/migration/V1_35__schema_comments.sql`, so SchemaSpy and any Glue crawl read the same source of
truth. Each column description ends with a sensitivity classification:

| Tag | Meaning |
| --- | --- |
| `[Sensitivity: NONE]` | Not personal data in itself |
| `[Sensitivity: PERSONAL]` | Personal data about a prisoner — identifies or locates them |
| `[Sensitivity: STAFF]` | Personal data about a member of staff, typically the username that acted |
| `[Sensitivity: SPECIAL-CATEGORY]` | UK GDPR Article 9 data, or offence data under Article 10 |
| `[Sensitivity: OFFICIAL-SENSITIVE]` | Not personal data, but damaging if disclosed |

`STAFF` is still personal data and still in scope for a staff member's own subject access request. It
is separated from `PERSONAL` so an extract about prisoners can be reasoned about without staff columns
inflating the count.

The tags describe **the column's own content, not the row's** — every row in `prisoner_iep_level` and
`next_review_date` belongs to a prisoner, so the whole record is personal data about them whatever an
individual column is marked.

Most of this schema is not about people at all: `incentive_level`, `prison_incentive_level` and `kpi`
are reference data, per-prison configuration and daily aggregates, with no personal data in them. Only
one column is special category — `prisoner_iep_level.comment_text`, the free-text review note, which in
practice covers behaviour, adjudications, health and third parties. `shedlock` and
`flyway_schema_history` are infrastructure and should be excluded from any ingestion or catalogue entry.

The tag is split into its own `sensitivity` column in `data-dictionary.csv`, and stripped from the
description there so the text reads cleanly.

**Any new table or column needs a `COMMENT ON`** in a migration — `SchemaCommentsTest` fails the build
otherwise. A later migration can add to or replace any comment at any time. Likewise a new `ReviewType`
value needs a description in `ExportReferenceData`, which fails rather than exporting a blank row.

Note that the compose database binds host port 5432 deliberately: `PostgresContainer.isPostgresRunning()`
defers to an already-running database, so `InitialiseDatabase` migrates that container and SchemaSpy can
read the same schema afterwards. Left to Testcontainers the schema would die with the JVM.

## Architecture

Architecture decision records start [here](doc/architecture/decisions/0001-use-adr.md)
