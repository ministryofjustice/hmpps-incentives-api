-- Increased 'review_type' length to be able to fit 'READMISSION'
ALTER TABLE prisoner_iep_level ALTER COLUMN review_type TYPE VARCHAR(16);
