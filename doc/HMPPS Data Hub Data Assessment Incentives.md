# Data Assessment – Incentives

---

## Contacts

| Role | Name           |
|------|----------------|
| Service Owner | Greg Smith     |
| Product Manager | Aparna Majumder |
| Business Analyst | Steve Houlden  |
| Technical Lead/Architect | Michael Willis |
| Data Steward/Owner | Unknown        |

---

## Service details

| Field | Value                                                                                                                                                                |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Service Name | Incentives                                                                                                                               |
| Service Status (beta/live) | Live                                                                                                                                                                 |
| Service Availability (what % of the estate) | 100% of the prison estate (all public-sector establishments in England & Wales). Incentive levels and prisoner incentive (IEP) reviews are mastered here for every prison. |
| Slack Channel | `#incentives`                                                                                                                                                   |

---

## Code Repositories

| Repository | Notes |
|------------|-------|
| https://github.com/ministryofjustice/hmpps-incentives-api | This service: REST API, R2DBC/Postgres DDL via Flyway, domain-event publishing |
| https://github.com/ministryofjustice/hmpps-incentives-ui | Primary consumer UI |
| https://github.com/ministryofjustice/cloud-platform-environments | Namespaces `hmpps-incentives-dev/-preprod/-prod`: defines the RDS Postgres instance, SQS/SNS, secrets |

---

## Upstream Data Dependencies

| Dependent System | Dependencies | Details |
|------------------|--------------|---------|
| NOMIS (via HMPPS Prison API) | prison-api: `GET /api/bookings/{bookingId}` (prisoner/booking detail); `GET /api/agencies/prison` (active prisons). Base URL `prison-api.prison.service.justice.gov.uk` | Enriches incentive reviews with prisoner/booking information and enumerates active prisons. NOMIS is the legacy source system from which historical IEP data was migrated. |
| HMPPS Prisoner Search (`hmpps-prisoner-search`) | Search prisoners by prison/cell location; get prisoner by NOMS number. Base URL `prisoner-search.prison.service.justice.gov.uk` | Finds prisoners at a location and resolves prisoner details for reviews and KPI calculation. |
| Offender Case Notes (offender-case-notes API) | Case-note counts by from-date. Base URL `offender-case-notes.service.justice.gov.uk` | Aggregates supporting case-note counts used in review/reporting context. |
| Locations Inside Prison API (`locations-inside-prison-api`) | `GET` location by key. Base URL `locations-inside-prison-api.hmpps.service.justice.gov.uk` | Resolves internal location / cell details. |
| HMPPS Domain Events (SNS→SQS inbound queue `incentives` / prisoner-event-queue) | Consumes domain events: prisoner-received, prisoner-merged, prisoner alerts-updated, booking moved. | Drives `PrisonerIncentiveReviewService` / `NextReviewDateUpdaterService` to keep review records and next-review dates consistent when prisoners move/merge. |

---

## Downstream Data Dependencies

| Dependent System | Details |
|------------------|---------|
| `hmpps-incentives-ui` | Primary consumer. Reads incentive levels, per-prison level config and prisoner incentive reviews via this service's REST API. |
| HMPPS domain-event subscribers (via `domainevents` SNS topic) | Any HMPPS service subscribing to events: `incentives.iep-review.inserted/updated/deleted`, `incentives.prisoner.next-review-date-changed`, `incentives.level.changed`, `incentives.levels.reordered`, `incentives.prison-level.changed`. |
| HMPPS Audit service | Mutating operations publish audit records to the HMPPS audit queue. |
| Digital Prison Reporting / Reporting Hub | Target of this assessment - will ingest incentives data via CDC for reporting. |

---

## Database Details

| Field | Value |
|-------|-------|
| Database Name | `incentives` (logical DB name is environment-specific, injected at runtime from k8s secret `dps-rds-instance-output` key `database_name`) |
| Database Vendor & Version | PostgreSQL on AWS RDS (engine version managed by the Cloud Platform RDS Terraform module - confirm exact version in cloud-platform-environments). Drivers: r2dbc-postgresql at runtime, JDBC postgresql 42.x for Flyway migrations. |
| Running in MOJ Cloud Platform? | Yes |
| Running in RDS? | Yes - AWS RDS provisioned via the Cloud Platform `rds-instance` Terraform module in every environment (dev, preprod, prod). |
| Database availability | The RDS database is available 24/7 in all environments. Note: the *application* pods are scaled down out-of-hours in dev & preprod (startup 06:49 UTC, shutdown 21:58 UTC, Mon-Fri); prod has no scheduled downtime. This affects the app, not DB availability. |

### Environments

| Field | Value |
|-------|-------|
| Database Name | `incentives` |
| Environments | dev, preprod, prod (production). No separate 'test' DB - integration tests use ephemeral Testcontainers Postgres. K8s namespaces: `hmpps-incentives-dev` / `hmpps-incentives-preprod` / `hmpps-incentives-prod`. |

---

## Database Tables

| Table | Nested Data | Primary Key | Table/View | Sensitivity | Truncation | Volume | Stability |
|-------|-------------|-------------|------------|-------------|------------|--------|-----------|
| `prisoner_iep_level` | None (no JSON) | Y - `id` (SERIAL). Unique `(booking_id)` for current review | T | restricted | No - full review history retained | 6 | Stable schema; append-mostly data. NB this is the prisoner incentive (IEP) review history table. |
| `incentive_level` | None | Y - `code` | T | internal | No | 1 | Stable reference/config data (~6 rows). |
| `prison_incentive_level` | None | Y - `id` (SERIAL); unique `(level_code, prison_id)` | T | internal | No | 3 | Stable per-prison config (spend/transfer limits, visit orders). |
| `next_review_date` | None | Y - `booking_id` | T | restricted | No | 5 | Derived/cached per active booking; recomputed on events/reviews (frequently updated). |
| `kpi` | None | Y - `day` | T | internal | No | 3 | Daily aggregated KPI metrics; one row per day; no personal data. |

> Internal tables intentionally **excluded** from the list: `shedlock` (ShedLock distributed-lock table) and `flyway_schema_history` (migration metadata).
>
> Volume = order of magnitude (10^n). `prisoner_iep_level` ≈ 6 is an estimate (~millions, incl. historical NOMIS-migrated reviews across the whole estate).

---

## Database Usage Statistics

- Reactive WebFlux + R2DBC service; low-to-moderate OLTP workload. Prod runs 4 replicas.
- Write activity is driven by (a) inbound prisoner domain events (received / merged / alerts-updated / booking-moved) and (b) IEP review submissions from incentives-ui - both correlate with prison working hours (weekday daytime UK).
- `next_review_date` recomputation is the most frequent write pattern (per event / per review).
- Scheduled KPI task (`UpdateKpis`) runs monthly (first weekday 10:00) doing heavier read aggregation; guarded by ShedLock so only one pod runs it.
- No large annual/EOY batch peaks. Live operational metrics available in Application Insights.

---

## Database Policies

| Table | Propagation Policy | Data Retention Policy | Permission Policy | Other Policy |
|-------|--------------------|-----------------------|-------------------|--------------|
| `prisoner_iep_level` | incremental (CDC) | Per HMPPS Data Retention Policy - prisoner review history retained long-term; no row-level truncation. | Available to staff per HMPPS/DPR DPIA. Contains prisoner personal data - treat as restricted. | `comment_text` is free text and may contain sensitive narrative - consider masking. |
| `incentive_level` | incremental (CDC) | Reference data - retain; no special policy. | All staff. | None. |
| `prison_incentive_level` | incremental (CDC) | Reference/config data - retain; no special policy. | All staff. | None. |
| `next_review_date` | incremental (CDC) | Derived/cached; follows booking retention. | Per DPIA; prisoner-linked - restricted. | Recomputed frequently. |
| `kpi` | `1.day` (batch) or incremental | Aggregate metrics - retain. | All staff. | None. |

---

## Sensitive data

| Table | Field | Sensitivity | Permission Type |
|-------|-------|-------------|-----------------|
| `prisoner_iep_level` | `prisoner_number` | restricted | Prisoner PII (NOMS number) - access controlled. |
| `prisoner_iep_level` | `comment_text` | restricted | Free-text; may contain sensitive personal narrative - field-level masking may apply. |
| `prisoner_iep_level` | `reviewed_by` | confidential | Staff username (staff personal data). |


---

## Other Information

- Fully reactive Spring Boot WebFlux service, Kotlin coroutines, R2DBC at runtime; Flyway (JDBC) migrations applied at startup; JDK 25. ProductId DPS020.
- Part of the "Getting off NOMIS" programme - masters IEP/incentives data that historically lived in NOMIS.
- Domain events emitted on the `domainevents` SNS topic: `incentives.iep-review.inserted`, `.updated`, `.deleted`; `incentives.prisoner.next-review-date-changed`; `incentives.level.changed`; `incentives.levels.reordered`; `incentives.prison-level.changed`.
- Internal tables intentionally EXCLUDED from the table list: `shedlock` (ShedLock distributed-lock table) and `flyway_schema_history` (migration metadata).
- Naming note: DB table `prisoner_iep_level` = prisoner incentive (IEP) review history (Kotlin entity `IncentiveReview`).
