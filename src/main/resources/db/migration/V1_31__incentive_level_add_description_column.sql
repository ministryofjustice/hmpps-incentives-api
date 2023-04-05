-- Add `description` column to Incentive levels
ALTER TABLE incentive_level
ADD COLUMN description TEXT NOT NULL DEFAULT '';
