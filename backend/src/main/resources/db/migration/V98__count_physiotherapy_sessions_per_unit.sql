-- Physiotherapy is sold and claimed as sessions. Quantity therefore consumes
-- the times ceiling unit-by-unit; EACH_LINE incorrectly counted quantity 15 as
-- one session and allowed the ceiling to be bypassed.
UPDATE benefit_limit_buckets
   SET counting_method = 'EACH_UNIT',
       updated_at = CURRENT_TIMESTAMP
 WHERE code = 'B-CAT027'
   AND times_limit IS NOT NULL
   AND counting_method <> 'EACH_UNIT';
