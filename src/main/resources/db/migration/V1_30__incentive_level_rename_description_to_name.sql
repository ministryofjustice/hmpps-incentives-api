-- Rename Incentive levels' `description` to `name`
ALTER TABLE incentive_level
RENAME COLUMN description TO name;
