-- ============================================================
-- Table: members
-- Depends on: employers, benefit_policies
-- ============================================================
CREATE TABLE IF NOT EXISTS members (
    id                   BIGINT PRIMARY KEY DEFAULT nextval('member_seq'),
    member_card_id       VARCHAR(100) NOT NULL UNIQUE,
    full_name            VARCHAR(255) NOT NULL,
    full_name_ar         VARCHAR(255),
    date_of_birth        DATE NOT NULL,
    gender               VARCHAR(20) CHECK (gender IN ('MALE','FEMALE')),
    national_id          VARCHAR(50),

    employer_id          BIGINT,
    employee_id          VARCHAR(100),
    employee_number      VARCHAR(100),
    membership_type      VARCHAR(50) CHECK (membership_type IN ('PRIMARY','DEPENDENT')),
    relation_to_employee VARCHAR(50),
    relationship         VARCHAR(50),
    parent_id            BIGINT,

    email                VARCHAR(255),
    phone                VARCHAR(50),
    address              TEXT,
    coverage_start_date  DATE,
    coverage_end_date    DATE,
    policy_number        VARCHAR(100),
    start_date           DATE,
    end_date             DATE,
    join_date            DATE,

    benefit_policy_id    BIGINT,

    barcode              VARCHAR(100),
    birth_date           DATE,
    card_number          VARCHAR(50),
    card_status          VARCHAR(30),
    card_activated_at    TIMESTAMP,
    is_smart_card        BOOLEAN DEFAULT false,
    civil_id             VARCHAR(50),
    national_number      VARCHAR(50),

    photo_url            VARCHAR(500),
    profile_photo_path   VARCHAR(500),
    marital_status       VARCHAR(20),
    nationality          VARCHAR(100),
    occupation           VARCHAR(100),
    notes                TEXT,
    emergency_notes      TEXT,

    is_vip               BOOLEAN DEFAULT false,
    is_urgent            BOOLEAN DEFAULT false,
    blocked_reason       VARCHAR(500),

    status               VARCHAR(30) DEFAULT 'ACTIVE',
    eligibility_status   VARCHAR(30),
    eligibility_updated_at TIMESTAMP,

    version              BIGINT DEFAULT 0,

    active               BOOLEAN NOT NULL DEFAULT true,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),

    CONSTRAINT fk_member_employer FOREIGN KEY (employer_id)       REFERENCES employers(id)       ON DELETE RESTRICT,
    CONSTRAINT fk_member_policy   FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_parent   FOREIGN KEY (parent_id)         REFERENCES members(id)          ON DELETE SET NULL,
    CONSTRAINT chk_coverage_dates CHECK (coverage_end_date IS NULL OR coverage_end_date >= coverage_start_date)
);

CREATE INDEX IF NOT EXISTS idx_members_card_id              ON members(member_card_id);
CREATE INDEX IF NOT EXISTS idx_members_employer             ON members(employer_id);
CREATE INDEX IF NOT EXISTS idx_members_national_id          ON members(national_id);
CREATE INDEX IF NOT EXISTS idx_members_active               ON members(active);
CREATE INDEX IF NOT EXISTS idx_members_status               ON members(status);
CREATE INDEX IF NOT EXISTS idx_members_parent_id            ON members(parent_id);
CREATE INDEX IF NOT EXISTS idx_members_barcode              ON members(barcode);
CREATE INDEX IF NOT EXISTS idx_members_card_number          ON members(card_number);
CREATE INDEX IF NOT EXISTS idx_members_civil_id             ON members(civil_id);
CREATE INDEX IF NOT EXISTS idx_members_benefit_policy       ON members(benefit_policy_id);
CREATE INDEX IF NOT EXISTS idx_members_employer_active      ON members(employer_id, active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_members_employer_search      ON members(employer_id, civil_id, full_name) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_members_employer_active_report ON members(employer_id) WHERE active = true;
