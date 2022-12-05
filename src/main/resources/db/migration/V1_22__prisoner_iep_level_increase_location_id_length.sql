-- Increased 'location_id' length to be able to fit locations descriptions. These can be 240 chars
ALTER TABLE prisoner_iep_level ALTER COLUMN location_id TYPE VARCHAR(240);
