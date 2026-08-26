-- Data dictionary for the incentives schema.
--
-- These comments are read by SchemaSpy (published to GitHub Pages) and by anything else that reads
-- pg_description, including the CSV export for the MOJ Data Catalogue / Glue. Keep them updated when
-- columns are added or their meaning changes - SchemaCommentsTest fails the build if a table or column
-- has no comment.
--
-- Every column comment ends with a sensitivity classification:
--
--   [Sensitivity: NONE]                - not personal data in itself (keys, timestamps, process flags)
--   [Sensitivity: PERSONAL]            - personal data about a prisoner: identifies or locates them
--   [Sensitivity: STAFF]               - personal data about a member of staff, typically the username
--                                        that performed an action
--   [Sensitivity: SPECIAL-CATEGORY]    - UK GDPR Article 9 data (health, sexuality, religion, race,
--                                        gender reassignment) or criminal offence data under Article 10
--   [Sensitivity: OFFICIAL-SENSITIVE]  - not personal data, but damaging if disclosed
--
-- STAFF is still personal data and still in scope for a staff member's own subject access request. It is
-- separated from PERSONAL so that an extract about prisoners can be reasoned about without staff columns
-- inflating the count, and so staff data can be dropped or pseudonymised independently.
--
-- Three things to understand before using these classifications:
--
--   1. They describe the column's own content, not the row's. Every row in prisoner_iep_level and
--      next_review_date belongs to a prisoner, so the whole record is personal data about that prisoner
--      whatever an individual column is marked - that is what matters for a subject access request.
--   2. Most of this schema is not about people at all. incentive_level, prison_incentive_level and kpi
--      are reference data, per-prison configuration and daily aggregates respectively, with no personal
--      data in them. The personal data is concentrated in exactly two tables.
--   3. Only one column here is special category: comment_text. The rest of an incentive review is a
--      level code and a timestamp, but the free-text comment is written by staff describing behaviour
--      in their own words, and in practice covers health, adjudications and third parties. It is
--      classified on that basis, not on what the field label asks for.
--
-- Naming trap: the table prisoner_iep_level is the prisoner incentive (IEP) *review history* - one row
-- per review, not one row per prisoner. The Kotlin entity is called IncentiveReview. "IEP" is the legacy
-- NOMIS name for what the service now calls an incentive level.
--
-- Two tables are infrastructure rather than business data and should be ignored by anything consuming
-- this schema: shedlock (distributed lock) and flyway_schema_history (migration metadata). They are
-- commented here anyway so the published report explains itself.

------------------------------------------------------------------------------------------------
-- prisoner_iep_level - the incentive review history
------------------------------------------------------------------------------------------------

COMMENT ON TABLE prisoner_iep_level IS 'One incentive (IEP) review of one prisoner, in an ordered history - not one row per prisoner. A prisoner accumulates a row per review over time and the one that counts today is flagged by current. Holds reviews conducted in DPS alongside the full history migrated from NOMIS, which is why reviewed_by and location_id are nullable and why review_type distinguishes MIGRATED rows. The Kotlin entity is IncentiveReview; the table keeps the legacy NOMIS name.';

COMMENT ON COLUMN prisoner_iep_level.id IS 'Primary key. Surrogate sequence value; carries no meaning and is not the NOMIS identifier. [Sensitivity: NONE]';
COMMENT ON COLUMN prisoner_iep_level.booking_id IS 'NOMIS booking id - the prisoner''s custodial period, not the person. A prisoner released and recalled has a new booking id, so this identifies one spell in custody for one prisoner. Used rather than prisoner_number as the join key for next_review_date. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN prisoner_iep_level.prisoner_number IS 'NOMIS offender number (noms id) of the prisoner reviewed. The link that makes every row here personal data about that prisoner. Updated in place when NOMIS merges two offender records. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN prisoner_iep_level.prison_id IS 'Agency (prison) code where the review took place. Read with prisoner_number it indicates where that prisoner was held at the time. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN prisoner_iep_level.location_id IS 'Internal location the prisoner was held at when reviewed - a cell or wing description rather than an id, despite the name, which is why it was widened to 240 characters. Nullable, and not populated by current write paths: the field is legacy and no longer mapped onto the Kotlin entity, so treat it as historical. Where set it locates the prisoner within the establishment. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN prisoner_iep_level.review_time IS 'When the review was conducted. For rows migrated from NOMIS this is the original review date, not the migration. This is the column that orders a prisoner''s history. [Sensitivity: NONE]';
COMMENT ON COLUMN prisoner_iep_level.iep_code IS 'The incentive level the prisoner was placed on by this review. Foreign key to incentive_level.code - BAS, STD, ENH, EN2, EN3 or the retired ENT. Column keeps the legacy NOMIS name; the Kotlin property is levelCode. [Sensitivity: NONE]';
COMMENT ON COLUMN prisoner_iep_level.comment_text IS 'The reviewer''s free-text note on the review. Unstructured and unbounded - in practice describes behaviour, adjudications, health and third parties, so treat as special category regardless of what any individual comment happens to say. The only special category column in this schema. [Sensitivity: SPECIAL-CATEGORY]';
COMMENT ON COLUMN prisoner_iep_level.reviewed_by IS 'DPS username of the member of staff who conducted the review, or the system user for reviews created by the service itself in response to a prisoner movement. Null on some rows migrated from NOMIS, which did not always record it. Identifies a member of staff. [Sensitivity: STAFF]';
COMMENT ON COLUMN prisoner_iep_level.current IS 'Whether this is the prisoner''s current incentive level. Exactly one row per booking_id may be true, enforced by a partial unique index; all earlier reviews are false. Filter on this rather than taking the latest review_time. [Sensitivity: NONE]';
COMMENT ON COLUMN prisoner_iep_level.when_created IS 'When the row was written to this service. For reviews migrated from NOMIS this is the migration run, not the review - use review_time for when the review actually happened. [Sensitivity: NONE]';
COMMENT ON COLUMN prisoner_iep_level.review_type IS 'What caused the review: INITIAL (on first entering custody), REVIEW (a normal scheduled or ad-hoc review), TRANSFER (level carried over on moving establishment), READMISSION (returning to custody) or MIGRATED (loaded from NOMIS, where the original cause was not recorded). Only REVIEW and MIGRATED count as real reviews for reporting - see IsRealReview. [Sensitivity: NONE]';

------------------------------------------------------------------------------------------------
-- next_review_date - derived, one row per booking
------------------------------------------------------------------------------------------------

COMMENT ON TABLE next_review_date IS 'When each prisoner''s next incentive review is due. Derived rather than entered: recalculated from the review history, the prisoner''s age and their current level whenever a review is recorded or a relevant prisoner event arrives, so it is frequently rewritten and is a cache rather than a source of truth. One row per booking, not per review.';

COMMENT ON COLUMN next_review_date.booking_id IS 'NOMIS booking id - the prisoner''s custodial period, not the person. Primary key here, so there is one row per booking. Joins to prisoner_iep_level.booking_id. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN next_review_date.next_review_date IS 'Date the prisoner''s next incentive review is due. Computed from the last review, the prisoner''s current level and whether they are a young person, so its value discloses something about their incentive history rather than being a plain scheduling field. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN next_review_date.when_created IS 'When the row was first written. [Sensitivity: NONE]';
COMMENT ON COLUMN next_review_date.when_updated IS 'When the date was last recalculated. Changes often - every review and every relevant prisoner event triggers a recalculation, whether or not the date itself moves. [Sensitivity: NONE]';

------------------------------------------------------------------------------------------------
-- incentive_level - national reference data
------------------------------------------------------------------------------------------------

COMMENT ON TABLE incentive_level IS 'The incentive levels available nationally, in display order. Reference data with roughly six rows, mastered here rather than in NOMIS. Levels are deactivated rather than deleted so historical reviews referencing them still resolve.';

COMMENT ON COLUMN incentive_level.code IS 'Short code for the level and the primary key: BAS (Basic), STD (Standard), ENH (Enhanced), EN2 (Enhanced 2), EN3 (Enhanced 3) or ENT (Entry, retired). Referenced by prisoner_iep_level.iep_code and prison_incentive_level.level_code. [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level.name IS 'Human-readable name of the level, for example "Enhanced 2". Renamed from description in V1_30 - the Kotlin property is name. [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level."sequence" IS 'Display order, lowest first. Deliberately not unique, so levels can be reordered without a constraint violation partway through the update. Retired levels sit at the end (ENT is 99). [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level.active IS 'Whether the level can be used for new reviews. Retired levels stay in the table with active false so historical reviews still resolve. [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level.required IS 'Whether every prison must offer this level. True for BAS, STD and ENH - the three levels the national policy framework mandates - and false for the rest, which a prison may choose to offer. A required level cannot be deactivated: a check constraint enforces active OR NOT required. [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level.when_created IS 'When the level was added. [Sensitivity: NONE]';
COMMENT ON COLUMN incentive_level.when_updated IS 'When the level was last changed, including being activated, deactivated or reordered. [Sensitivity: NONE]';

------------------------------------------------------------------------------------------------
-- prison_incentive_level - per-prison configuration
------------------------------------------------------------------------------------------------

COMMENT ON TABLE prison_incentive_level IS 'How one prison configures one incentive level: whether it is offered, whether new arrivals start on it, and the spending and visit entitlements that come with it. One row per prison and level. Configuration about establishments, not about people - there is no personal data in this table.';

COMMENT ON COLUMN prison_incentive_level.id IS 'Primary key. Surrogate sequence value; the meaningful key is the unique pair (level_code, prison_id). [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.level_code IS 'The incentive level being configured. Foreign key to incentive_level.code. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.prison_id IS 'Agency (prison) code the configuration applies to. Identifies an establishment, not a prisoner. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.active IS 'Whether this prison offers this level. A level active nationally can be inactive at a given prison. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.default_on_admission IS 'Whether new arrivals at this prison start on this level. Deliberately not constrained to one row per prison, so a misconfiguration is possible and the service picks a winner rather than the database rejecting it. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.when_created IS 'When the configuration row was added. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.when_updated IS 'When the configuration was last changed. Rows loaded in the initial migration carry the original NOMIS timestamp, which is why some are many years old. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.remand_transfer_limit_in_pence IS 'Most a remand prisoner on this level at this prison may transfer into their spends account per week, in pence. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.remand_spend_limit_in_pence IS 'Most a remand prisoner on this level at this prison may hold in their spends account, in pence. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.convicted_transfer_limit_in_pence IS 'Most a convicted prisoner on this level at this prison may transfer into their spends account per week, in pence. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.convicted_spend_limit_in_pence IS 'Most a convicted prisoner on this level at this prison may hold in their spends account, in pence. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.visit_orders IS 'Number of standard visit orders per period for a prisoner on this level at this prison. [Sensitivity: NONE]';
COMMENT ON COLUMN prison_incentive_level.privileged_visit_orders IS 'Number of privileged visit orders per period for a prisoner on this level at this prison, on top of the standard allowance. [Sensitivity: NONE]';

------------------------------------------------------------------------------------------------
-- kpi - daily aggregates
------------------------------------------------------------------------------------------------

COMMENT ON TABLE kpi IS 'Daily national aggregate counts about incentive reviews, one row per day, written by a scheduled task. Counts only - no prisoner is identifiable from this table, and it is not a source of truth for anything: the underlying reviews are in prisoner_iep_level.';

COMMENT ON COLUMN kpi.day IS 'The day these counts were calculated for. Primary key, so one row per day. [Sensitivity: NONE]';
COMMENT ON COLUMN kpi.overdue_reviews IS 'Number of prisoners whose next review date had passed as at this day, across the estate. [Sensitivity: NONE]';
COMMENT ON COLUMN kpi.previous_month_reviews_conducted IS 'Number of reviews conducted in the calendar month before this day. Counts reviews, so a prisoner reviewed twice counts twice - compare with previous_month_prisoners_reviewed. [Sensitivity: NONE]';
COMMENT ON COLUMN kpi.previous_month_prisoners_reviewed IS 'Number of distinct prisoners reviewed in the calendar month before this day. Always less than or equal to previous_month_reviews_conducted. [Sensitivity: NONE]';
COMMENT ON COLUMN kpi.when_created IS 'When the row was written. [Sensitivity: NONE]';
COMMENT ON COLUMN kpi.when_updated IS 'When the row was last recalculated. [Sensitivity: NONE]';

------------------------------------------------------------------------------------------------
-- shedlock - infrastructure, not business data
------------------------------------------------------------------------------------------------

COMMENT ON TABLE shedlock IS 'ShedLock distributed lock table. Infrastructure: it stops more than one pod running the same scheduled task at once. Contains no business or personal data and should be excluded from any data ingestion or catalogue entry, alongside flyway_schema_history.';

COMMENT ON COLUMN shedlock.name IS 'Name of the scheduled task being locked, for example the KPI update. Primary key. [Sensitivity: NONE]';
COMMENT ON COLUMN shedlock.lock_until IS 'When the lock expires, so a pod that dies mid-task does not hold it forever. [Sensitivity: NONE]';
COMMENT ON COLUMN shedlock.locked_at IS 'When the lock was taken. [Sensitivity: NONE]';
COMMENT ON COLUMN shedlock.locked_by IS 'Which instance holds the lock - a pod hostname, not a person. [Sensitivity: NONE]';
