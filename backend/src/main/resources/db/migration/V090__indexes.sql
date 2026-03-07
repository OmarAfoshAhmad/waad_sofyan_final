-- ============================================================
-- V090: Performance indexes
-- ============================================================
-- All compound / partial indexes for dashboard and reporting queries.
-- Depends on: all schema files V001–V081.

-- ----------------------------------------------------------
-- Settlement & financial reconciliation
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_claims_provider_status_approved
    ON claims(provider_id, status, approved_amount)
    WHERE status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_settlement_batches_provider_date_status
    ON settlement_batches(provider_id, created_at, status);

CREATE INDEX IF NOT EXISTS idx_account_transactions_provider_date
    ON account_transactions(provider_account_id, transaction_date);

-- ----------------------------------------------------------
-- Claims workflow & review queue
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_claims_reviewer_status_date
    ON claims(reviewer_id, status, service_date DESC);

CREATE INDEX IF NOT EXISTS idx_claims_member_date_status_reporting
    ON claims(member_id, service_date DESC, status);

CREATE INDEX IF NOT EXISTS idx_claims_provider_date_status
    ON claims(provider_id, service_date DESC, status);

-- ----------------------------------------------------------
-- Pre-authorization workflow
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_preauth_member_status_date
    ON preauthorization_requests(member_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_preauth_expiring
    ON preauthorization_requests(valid_until)
    WHERE status = 'APPROVED' AND valid_until IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_preauth_provider_date_status
    ON preauthorization_requests(provider_id, created_at DESC, status);

-- ----------------------------------------------------------
-- Member & policy management
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_members_employer_active_report
    ON members(employer_id)
    WHERE active = true;

-- ----------------------------------------------------------
-- Provider & contract management
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_provider_contracts_active
    ON provider_contracts(active);

CREATE INDEX IF NOT EXISTS idx_provider_contracts_expiring
    ON provider_contracts(contract_end_date)
    WHERE active = true AND contract_end_date IS NOT NULL;

-- ----------------------------------------------------------
-- Login security
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed_window
    ON user_login_attempts(username, attempted_at DESC)
    WHERE success = false;

-- ----------------------------------------------------------
-- Attachment management
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type_date
    ON claim_attachments(attachment_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit_date
    ON visit_attachments(visit_id, created_at DESC);

-- ----------------------------------------------------------
-- Financial reporting
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_claims_monthly_reporting_full
    ON claims(status, service_date, provider_id, approved_amount);

CREATE INDEX IF NOT EXISTS idx_settlement_batch_payment
    ON settlement_batches(status, paid_at, total_amount);

CREATE INDEX IF NOT EXISTS idx_transactions_reporting_full
    ON account_transactions(transaction_date, transaction_type, amount);

-- ----------------------------------------------------------
-- Dashboard performance
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_settlements_active
    ON settlement_batches(status, created_at DESC)
    WHERE status IN ('DRAFT','CONFIRMED');

CREATE INDEX IF NOT EXISTS idx_claims_approval_metrics_full
    ON claims(status, reviewed_at, approved_amount)
    WHERE status IN ('APPROVED','REJECTED');

-- ----------------------------------------------------------
-- Medical service catalog
-- ----------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_package_services_service
    ON medical_service_categories(service_id);
