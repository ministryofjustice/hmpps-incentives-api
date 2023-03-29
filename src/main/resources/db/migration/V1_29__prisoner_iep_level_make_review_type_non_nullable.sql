-- Make review_type mandatory
ALTER TABLE prisoner_iep_level
ALTER COLUMN review_type SET NOT NULL;
