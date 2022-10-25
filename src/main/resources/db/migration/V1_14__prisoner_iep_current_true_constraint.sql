-- fix existing data before applying this constraint
-- it's ok to update the data because currently data in the Incentives DB is not used and will soon be truncated with INC-756

-- explanation of the query which should ensure only the latest (by time) iep level for each bookingId has current=true
-- 1. get prisoner_iep_level where we have more than 1 record for a bookingId where current=true
-- 2. rank these by review_time descending
-- 3. filter out rows with the highest timestamp (which are rank 1) for that bookingId
-- 4. update the others
WITH summary
         AS (SELECT p.id,
                    -- step 2
                    row_number()
                        OVER(
                            partition BY p.booking_id
                            ORDER BY p.review_time DESC)
                        AS rank
             FROM   prisoner_iep_level p
             WHERE  p.booking_id IN (SELECT booking_id
                                     FROM   prisoner_iep_level
                                     GROUP  BY booking_id
                                     -- step 1
                                     HAVING count(CASE WHEN current = true THEN 1 ELSE NULL END)
                                        > 1
                                     )
            )
-- step 4
UPDATE prisoner_iep_level
SET    current = false
WHERE  id IN (SELECT id
              FROM   summary
              -- step 3
              WHERE  rank <> 1
             )
;

COMMIT;

-- create index
CREATE UNIQUE INDEX one_booking_id_with_current_true_idx ON prisoner_iep_level (booking_id) WHERE current = true;
