ALTER TABLE incentive_level
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE incentive_level
SET active = TRUE, required = TRUE
WHERE code IN ('BAS', 'STD', 'ENH');

ALTER TABLE incentive_level
    ADD CONSTRAINT incentive_level_active_if_required CHECK (active OR NOT required);

UPDATE prison_incentive_level
SET active = TRUE
WHERE level_code IN (SELECT code FROM incentive_level WHERE required IS TRUE);
