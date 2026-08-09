ALTER TABLE claim_pending_services
    ALTER COLUMN proposed_category_id DROP NOT NULL,
    ADD COLUMN proposed_category_code VARCHAR(50),
    ADD COLUMN proposed_category_name VARCHAR(200),
    ADD COLUMN new_category_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE claim_pending_services DROP CONSTRAINT chk_pending_service_decision_shape;
ALTER TABLE claim_pending_services ADD CONSTRAINT chk_pending_service_category_proposal CHECK (
    (new_category_requested = FALSE AND proposed_category_id IS NOT NULL)
    OR
    (new_category_requested = TRUE AND proposed_category_id IS NULL
        AND NULLIF(BTRIM(proposed_category_name), '') IS NOT NULL)
);
ALTER TABLE claim_pending_services ADD CONSTRAINT chk_pending_service_decision_shape CHECK (
    (status IN ('PRELIMINARY','NEEDS_INFO','SPLIT_REQUIRED','CATEGORY_CREATED_PENDING_COVERAGE')
        AND decided_by IS NULL AND decided_at IS NULL)
    OR
    (status IN ('APPROVED_CLAIM_ONLY','APPROVED_FOR_CONTRACT','LINKED_EXISTING','REJECTED')
        AND decided_by IS NOT NULL AND decided_at IS NOT NULL
        AND NULLIF(BTRIM(decision_reason), '') IS NOT NULL)
);
ALTER TABLE claim_pending_services DROP CONSTRAINT claim_pending_services_status_check;
ALTER TABLE claim_pending_services ADD CONSTRAINT claim_pending_services_status_check CHECK (status IN (
    'PRELIMINARY','NEEDS_INFO','SPLIT_REQUIRED','CATEGORY_CREATED_PENDING_COVERAGE',
    'APPROVED_CLAIM_ONLY','APPROVED_FOR_CONTRACT','LINKED_EXISTING','REJECTED'
));
