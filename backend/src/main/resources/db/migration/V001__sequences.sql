-- ============================================================
-- V001: All application sequences (created before any tables)
-- ============================================================

-- Core entities
CREATE SEQUENCE IF NOT EXISTS user_seq              START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS employer_seq          START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS provider_seq          START WITH 1 INCREMENT BY 50;

-- Medical catalog
CREATE SEQUENCE IF NOT EXISTS medical_category_seq  START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS medical_service_seq   START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS medical_service_category_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS ent_service_alias_seq START WITH 1 INCREMENT BY 50;

-- Business entities
CREATE SEQUENCE IF NOT EXISTS member_seq            START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS provider_contract_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS benefit_policy_seq    START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS claim_seq             START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS preauth_seq           START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS settlement_batch_seq  START WITH 1 INCREMENT BY 50;

-- Payment reference (auto-incremented payment ref numbers)
CREATE SEQUENCE IF NOT EXISTS settlement_payment_reference_seq
    START WITH 10001 INCREMENT BY 1;
