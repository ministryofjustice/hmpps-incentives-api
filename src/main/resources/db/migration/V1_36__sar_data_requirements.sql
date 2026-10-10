-- Example values and subject access request (SAR) classification for every column (IR-2041).
--
-- These feed the SAR Data Requirements extract (scripts/generate-sar-data-requirements.sh), the document the
-- Offender SAR team review at the data review checkpoint of the SAR change control process. Incentives is
-- already in the SAR tool, but its report has never been through a data review, so it is being re-baselined
-- (IR-2042), and every element needs an example value and a statement of whether it reaches a prisoner's
-- report. Comments only - no schema or data change.
--
-- Each column comment gains two tags, between the description and the existing sensitivity tag:
--
--   '... description text. [Example: ENH] [SAR: Y] [Sensitivity: NONE]'
--
-- [SAR: Y] means the value reaches the report under the response proposed for the re-baseline (IR-2044),
-- whether it is rendered from this column or used to decode one that is. [SAR: N] is for internal keys, the
-- NOMIS booking id, bookkeeping timestamps, the legacy location_id the service no longer returns, the per-prison
-- configuration, the daily aggregates and the lock table. This is a proposal for the Offender SAR team to confirm
-- or change; their decision goes in a later migration rather than an edit to this one.
--
-- Two differences from the report as it stands, which the re-baseline is expected to make:
--
--   * prisoner_iep_level.id and booking_id are returned today but are internal and NOMIS identifiers, so N.
--   * incentive_level.name is Y: the report shows the level code (BAS, STD, ENH) today, and should show the
--     level's name instead.
--
-- Example values for code columns are real codes. Every other example is invented, and the free-text example
-- is deliberately bland: it illustrates the kind of content, not a real review.

DO $$
DECLARE
  rec      record;
  existing text;
BEGIN
  FOR rec IN
    SELECT * FROM (VALUES
      -- The review history. What level, when, where, why and by whom reaches the report.
      ('prisoner_iep_level', 'id', '123456', 'N'),
      ('prisoner_iep_level', 'booking_id', '1234567', 'N'),
      ('prisoner_iep_level', 'prisoner_number', 'A1234BC', 'Y'),
      ('prisoner_iep_level', 'prison_id', 'MDI', 'Y'),
      ('prisoner_iep_level', 'location_id', '1-2-003', 'N'),
      ('prisoner_iep_level', 'review_time', '2026-03-14T10:15:00', 'Y'),
      ('prisoner_iep_level', 'iep_code', 'ENH', 'Y'),
      ('prisoner_iep_level', 'comment_text', 'Good behaviour on the wing and in work. Enhanced supported.', 'Y'),
      ('prisoner_iep_level', 'reviewed_by', 'AUSER_GEN', 'Y'),
      ('prisoner_iep_level', 'current', 'true', 'Y'),
      ('prisoner_iep_level', 'when_created', '2026-03-14T10:15:02', 'N'),
      ('prisoner_iep_level', 'review_type', 'REVIEW', 'Y'),

      -- When the next review is due. Held about the prisoner, so it reaches the report.
      ('next_review_date', 'booking_id', '1234567', 'N'),
      ('next_review_date', 'next_review_date', '2026-06-14', 'Y'),
      ('next_review_date', 'when_created', '2026-03-14T10:15:02', 'N'),
      ('next_review_date', 'when_updated', '2026-03-14T10:15:02', 'N'),

      -- National reference data. Only the name reaches the report, to decode the level code.
      ('incentive_level', 'code', 'ENH', 'N'),
      ('incentive_level', 'name', 'Enhanced', 'Y'),
      ('incentive_level', 'sequence', '3', 'N'),
      ('incentive_level', 'active', 'true', 'N'),
      ('incentive_level', 'required', 'true', 'N'),
      ('incentive_level', 'when_created', '2022-11-01T09:00:00', 'N'),
      ('incentive_level', 'when_updated', '2022-11-01T09:00:00', 'N'),

      -- Per-prison configuration. About establishments, not people.
      ('prison_incentive_level', 'id', '42', 'N'),
      ('prison_incentive_level', 'level_code', 'ENH', 'N'),
      ('prison_incentive_level', 'prison_id', 'MDI', 'N'),
      ('prison_incentive_level', 'active', 'true', 'N'),
      ('prison_incentive_level', 'default_on_admission', 'false', 'N'),
      ('prison_incentive_level', 'when_created', '2022-11-01T09:00:00', 'N'),
      ('prison_incentive_level', 'when_updated', '2022-11-01T09:00:00', 'N'),
      ('prison_incentive_level', 'remand_transfer_limit_in_pence', '6600', 'N'),
      ('prison_incentive_level', 'remand_spend_limit_in_pence', '66000', 'N'),
      ('prison_incentive_level', 'convicted_transfer_limit_in_pence', '3300', 'N'),
      ('prison_incentive_level', 'convicted_spend_limit_in_pence', '33000', 'N'),
      ('prison_incentive_level', 'visit_orders', '2', 'N'),
      ('prison_incentive_level', 'privileged_visit_orders', '1', 'N'),

      -- Daily national counts. No prisoner is identifiable.
      ('kpi', 'day', '2026-03-01', 'N'),
      ('kpi', 'overdue_reviews', '1520', 'N'),
      ('kpi', 'previous_month_reviews_conducted', '41000', 'N'),
      ('kpi', 'previous_month_prisoners_reviewed', '39000', 'N'),
      ('kpi', 'when_created', '2026-03-01T02:00:00', 'N'),
      ('kpi', 'when_updated', '2026-03-01T02:00:00', 'N'),

      -- Infrastructure.
      ('shedlock', 'name', 'INC - Update KPIs', 'N'),
      ('shedlock', 'lock_until', '2026-03-01T02:10:00', 'N'),
      ('shedlock', 'locked_at', '2026-03-01T02:00:00', 'N'),
      ('shedlock', 'locked_by', 'hmpps-incentives-api-6f9c7d8b5-abcde', 'N')
    ) AS t(table_name, column_name, example, sar_impact)
  LOOP
    SELECT col_description(a.attrelid, a.attnum)
      INTO existing
      FROM pg_attribute a
     WHERE a.attrelid = format('%I', rec.table_name)::regclass
       AND a.attname = rec.column_name
       AND a.attnum > 0
       AND NOT a.attisdropped;

    IF existing IS NULL THEN
      RAISE EXCEPTION 'No comment on %.% - describe the column before classifying it',
        rec.table_name, rec.column_name;
    END IF;

    IF position(' [Sensitivity:' IN existing) = 0 THEN
      RAISE EXCEPTION 'Comment on %.% has no sensitivity tag to place the new tags before',
        rec.table_name, rec.column_name;
    END IF;

    IF position('[Example:' IN existing) > 0 OR position('[SAR:' IN existing) > 0 THEN
      RAISE EXCEPTION 'Comment on %.% is already tagged', rec.table_name, rec.column_name;
    END IF;

    IF position(']' IN rec.example) > 0 THEN
      RAISE EXCEPTION 'Example for %.% contains "]", which would end the tag early',
        rec.table_name, rec.column_name;
    END IF;

    EXECUTE format(
      'COMMENT ON COLUMN %I.%I IS %L',
      rec.table_name,
      rec.column_name,
      replace(
        existing,
        ' [Sensitivity:',
        ' [Example: ' || rec.example || '] [SAR: ' || rec.sar_impact || '] [Sensitivity:'
      )
    );
  END LOOP;
END $$;
