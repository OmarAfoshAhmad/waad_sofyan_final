-- Align claims status CHECK constraint with current ClaimStatus enum
-- Fixes 500 during split-phase approval when setting APPROVAL_IN_PROGRESS

-- Normalize legacy statuses before applying strict check
UPDATE claims
SET status = 'NEEDS_CORRECTION'
WHERE status IN ('RETURNED_FOR_INFO', 'PENDING_DOCUMENTS');

-- Replace outdated status check constraint from V4
ALTER TABLE claims
DROP CONSTRAINT IF EXISTS claims_status_check;

ALTER TABLE claims
ADD CONSTRAINT claims_status_check
CHECK (
    status IN (
        'DRAFT',
        'SUBMITTED',
        'UNDER_REVIEW',
        'NEEDS_CORRECTION',
        'APPROVAL_IN_PROGRESS',
        'APPROVED',
        'REJECTED',
        'BATCHED',
        'SETTLED'
    )
);
