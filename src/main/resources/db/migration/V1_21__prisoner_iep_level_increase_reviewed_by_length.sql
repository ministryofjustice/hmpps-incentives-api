-- Increased 'reviewed_by' length to be able to fit all users
ALTER TABLE prisoner_iep_level ALTER COLUMN reviewed_by TYPE VARCHAR(32);
