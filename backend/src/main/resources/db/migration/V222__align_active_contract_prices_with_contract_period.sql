-- Active provider price rows are the only rows claim entry can use. When a
-- contract period is corrected but the active prices keep their previous
-- effective_from/effective_to, the contract resolves successfully while the
-- claim service list is empty for earlier service dates.
--
-- Align only simple contracts where every active pricing row shares one
-- identical period. Contracts with multiple active price periods are left for
-- explicit financial review because those periods may be intentional.
WITH active_periods AS (
    SELECT
        p.contract_id,
        COUNT(*) AS active_items,
        COUNT(DISTINCT (p.effective_from, p.effective_to)) AS period_count,
        MIN(p.effective_from) AS only_effective_from,
        MIN(p.effective_to) AS only_effective_to
    FROM provider_contract_pricing_items p
    WHERE p.active = true
    GROUP BY p.contract_id
),
eligible_contracts AS (
    SELECT
        c.id,
        c.start_date,
        CASE WHEN c.end_date IS NULL THEN NULL ELSE c.end_date + INTERVAL '1 day' END::date AS price_effective_to,
        ap.only_effective_from,
        ap.only_effective_to,
        ap.active_items
    FROM provider_contracts c
    JOIN active_periods ap ON ap.contract_id = c.id
    WHERE c.active = true
      AND ap.active_items > 0
      AND ap.period_count = 1
      AND c.start_date IS NOT NULL
      AND (
          ap.only_effective_from IS DISTINCT FROM c.start_date
          OR ap.only_effective_to IS DISTINCT FROM
             CASE WHEN c.end_date IS NULL THEN NULL ELSE c.end_date + INTERVAL '1 day' END::date
      )
)
UPDATE provider_contract_pricing_items p
SET effective_from = ec.start_date,
    effective_to = ec.price_effective_to,
    updated_at = NOW()
FROM eligible_contracts ec
WHERE p.contract_id = ec.id
  AND p.active = true
  AND p.effective_from IS NOT DISTINCT FROM ec.only_effective_from
  AND p.effective_to IS NOT DISTINCT FROM ec.only_effective_to;

DO $$
DECLARE
    remaining_contracts integer;
BEGIN
    SELECT COUNT(*) INTO remaining_contracts
    FROM (
        SELECT c.id
        FROM provider_contracts c
        JOIN provider_contract_pricing_items p ON p.contract_id = c.id AND p.active = true
        WHERE c.active = true
          AND c.start_date IS NOT NULL
        GROUP BY c.id, c.start_date, c.end_date
        HAVING MIN(p.effective_from) > c.start_date
            OR MAX(p.effective_from) > c.start_date
    ) gaps;

    IF remaining_contracts > 0 THEN
        RAISE NOTICE 'V222: % active contracts still have pricing periods requiring manual review', remaining_contracts;
    END IF;
END $$;
