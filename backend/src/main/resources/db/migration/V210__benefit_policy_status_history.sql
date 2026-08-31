-- ============================================================
-- V201: dated status history for benefit_policies.
--
-- MemberPolicyResolver.resolveFor(member, serviceDate) was answering
-- "was this policy in force on serviceDate?" by reading BenefitPolicy's
-- CURRENT status column -- the same defect the member/employer temporal
-- resolvers were built to remove for "current employer" and "current
-- policy assignment". Suspending or cancelling a policy today silently
-- invalidated every historical claim/eligibility read for a service date
-- when the policy genuinely was ACTIVE, because "is it ACTIVE" and "was
-- it ACTIVE on that date" shared one column.
--
-- This table is the dated source of truth for the second question, kept
-- separate from the policy's own configured start_date/end_date window
-- (which states when the policy is MEANT to run, not when its lifecycle
-- status actually was what). Half-open intervals: [valid_from, valid_to),
-- so a status change on day D closes the previous row at D (exclusive)
-- and opens the new one at D (inclusive) -- a service on D itself sees
-- the NEW status, never both or neither.
-- ============================================================

CREATE TABLE benefit_policy_status_history (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    valid_from  DATE        NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_policy_status_history_policy FOREIGN KEY (policy_id)
        REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_status_history_status
        CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','SUSPENDED','CANCELLED')),
    CONSTRAINT chk_policy_status_history_range
        CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_policy_status_history_policy ON benefit_policy_status_history(policy_id);

-- Only one open (valid_to IS NULL) row per policy at a time -- otherwise
-- "the current status" would be ambiguous.
CREATE UNIQUE INDEX uk_policy_status_history_one_open
    ON benefit_policy_status_history(policy_id) WHERE valid_to IS NULL;

-- Backfill: every existing policy gets one open row at its current status.
-- This is an honest approximation, not a reconstruction -- the actual
-- transition dates before this migration were never recorded anywhere,
-- so every pre-existing policy is treated as having held its current
-- status since it was created. A policy that was, say, activated and
-- later suspended before this migration ran will report ACTIVE for its
-- entire pre-migration history, which is wrong for exactly the dates
-- between those two events -- but it is the same "wrong" the system
-- already lived with, not a new one this migration introduces, and no
-- worse than the single-current-status model it replaces. Anything
-- transitioning through BenefitPolicyService from this point forward is
-- recorded exactly.
INSERT INTO benefit_policy_status_history (policy_id, status, valid_from, valid_to)
SELECT id, status, COALESCE(created_at::date, start_date), NULL
FROM benefit_policies;
