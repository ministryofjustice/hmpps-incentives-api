-- Drop `sequence` column (and associated indexes/unique constraints)
ALTER TABLE prisoner_iep_level
DROP COLUMN sequence;
