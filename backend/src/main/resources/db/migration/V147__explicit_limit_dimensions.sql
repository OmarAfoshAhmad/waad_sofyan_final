-- WAAD-FIN-1.0 finance-03.1: explicit, orthogonal limit dimensions.
-- Medical scope is persisted and never inferred from hierarchy depth.

ALTER TABLE benefit_limit_buckets
    ADD COLUMN benefit_scope_type VARCHAR(20),
    ADD COLUMN beneficiary_scope_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

-- One-time deterministic classification of pre-existing configuration:
-- * policy-general mirrors are not independent medical limits, so stay NULL;
-- * a BenefitDefinition-backed special benefit is a dedicated SERVICE;
-- * shared/hierarchical groups are GROUP accumulators;
-- * all remaining rules are CATEGORY rules (service rules were removed in V228).
UPDATE benefit_limit_buckets b
SET benefit_scope_type = CASE
    WHEN b.limit_role = 'POLICY_GENERAL_MIRROR' THEN NULL
    WHEN g.benefit_definition_id IS NOT NULL THEN 'SERVICE'
    WHEN g.aggregation_mode IN ('SHARED', 'HIERARCHICAL') THEN 'GROUP'
    ELSE 'CATEGORY'
END
FROM benefit_groups g
WHERE g.id = b.benefit_group_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM benefit_limit_buckets
        WHERE limit_role = 'STANDARD' AND benefit_scope_type IS NULL
    ) THEN
        RAISE EXCEPTION 'BUCKET_SCOPE_NOT_CLASSIFIED: one or more STANDARD buckets could not be classified';
    END IF;
END $$;

ALTER TABLE benefit_limit_buckets
    ADD CONSTRAINT chk_bucket_benefit_scope_type CHECK (
        (limit_role = 'POLICY_GENERAL_MIRROR' AND benefit_scope_type IS NULL)
        OR
        (limit_role = 'STANDARD' AND benefit_scope_type IN ('SERVICE', 'CATEGORY', 'GROUP'))
    ),
    ADD CONSTRAINT chk_bucket_beneficiary_scope_type
        CHECK (beneficiary_scope_type IN ('MEMBER'));

COMMENT ON COLUMN benefit_limit_buckets.benefit_scope_type IS
    'Explicit medical scope: SERVICE, CATEGORY, or GROUP. NULL only for POLICY_GENERAL_MIRROR, '
    'which is excluded from resolution. Never infer this value from hierarchy depth.';
COMMENT ON COLUMN benefit_limit_buckets.beneficiary_scope_type IS
    'Who shares consumption. MEMBER only until the family-sharing constitution is implemented.';

ALTER TABLE claim_line_limit_snapshots
    RENAME COLUMN limit_scope_type TO benefit_scope_type;

ALTER TABLE claim_line_limit_snapshots
    DROP CONSTRAINT chk_limit_snapshot_scope_type,
    DROP CONSTRAINT chk_limit_snapshot_policy_general_bucket;

ALTER TABLE claim_line_limit_snapshots
    ADD COLUMN beneficiary_scope_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    ADD CONSTRAINT chk_limit_snapshot_benefit_scope_type
        CHECK (benefit_scope_type IN ('SERVICE', 'CATEGORY', 'GROUP', 'POLICY_GENERAL')),
    ADD CONSTRAINT chk_limit_snapshot_beneficiary_scope_type
        CHECK (beneficiary_scope_type IN ('MEMBER')),
    ADD CONSTRAINT chk_limit_snapshot_policy_general_bucket CHECK (
        (benefit_scope_type = 'POLICY_GENERAL' AND bucket_id IS NULL)
        OR (benefit_scope_type <> 'POLICY_GENERAL' AND bucket_id IS NOT NULL)
    );

COMMENT ON COLUMN claim_line_limit_snapshots.benefit_scope_type IS
    'Medical scope at adjudication: SERVICE, CATEGORY, GROUP, or synthetic POLICY_GENERAL. '
    'FAMILY belongs to beneficiary_scope_type; LIFETIME belongs to period_type.';
COMMENT ON COLUMN claim_line_limit_snapshots.beneficiary_scope_type IS
    'Who shared the accumulator at adjudication. MEMBER only until family policy is implemented.';
