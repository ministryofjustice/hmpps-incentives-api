-- Drop constraint on FK iep_code on iep_prison.iep_code
ALTER TABLE prisoner_iep_level
DROP CONSTRAINT fk_iep_prison;
