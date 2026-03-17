-- ============================================================
-- V13: جدول الأعضاء — بناء نظيف مطابق لـ Member.java entity
-- المصدر الوحيد: com.waad.tba.modules.member.entity.Member
-- ============================================================

-- تسلسل توليد الباركود (مستخدم مباشرةً بواسطة BarcodeGeneratorService)
CREATE SEQUENCE IF NOT EXISTS member_barcode_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- ────────────────────────────────────────────────────────────
-- الجدول الرئيسي: members
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS members (

    -- @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    id                      BIGSERIAL PRIMARY KEY,

    -- @Version
    version                 BIGINT          DEFAULT 0,

    -- @ManyToOne parent  →  @JoinColumn(name = "parent_id")
    parent_id               BIGINT,

    -- @Enumerated @Column(length = 20, name = "relationship")
    relationship            VARCHAR(20)
                            CHECK (relationship IN ('WIFE','HUSBAND','SON','DAUGHTER','FATHER','MOTHER','BROTHER','SISTER')),

    -- @ManyToOne employer  →  @JoinColumn(name = "employer_id", nullable = false)
    employer_id             BIGINT          NOT NULL,

    -- @ManyToOne benefitPolicy  →  @JoinColumn(name = "benefit_policy_id")
    benefit_policy_id       BIGINT,

    -- @Column(nullable = false, length = 200, name = "full_name")
    full_name               VARCHAR(200)    NOT NULL,

    -- @Column(length = 50, name = "civil_id")  — deprecated, kept for legacy data
    civil_id                VARCHAR(50),

    -- @Column(length = 50, name = "national_number")
    national_number         VARCHAR(50),

    -- @Column(length = 50, name = "card_number")  + @UniqueConstraint uk_member_card_number
    card_number             VARCHAR(50),

    -- @Column(unique = true, length = 100, name = "barcode")  + @UniqueConstraint uk_member_barcode
    barcode                 VARCHAR(100),

    -- @Column(name = "birth_date")
    birth_date              DATE,

    -- @Enumerated @Column(length = 10)  →  column name = "gender"
    gender                  VARCHAR(10)
                            CHECK (gender IN ('MALE','FEMALE')),

    -- @Enumerated @Column(length = 20, name = "marital_status")
    marital_status          VARCHAR(20)
                            CHECK (marital_status IN ('SINGLE','MARRIED','DIVORCED','WIDOWED')),

    -- @Column(length = 20)  →  column name = "phone"
    phone                   VARCHAR(20),

    -- @Email @Column(length = 255)  →  column name = "email"
    email                   VARCHAR(255),

    -- @Column(length = 500)  →  column name = "address"
    address                 VARCHAR(500),

    -- @Column(length = 100)  →  column name = "nationality"
    nationality             VARCHAR(100),

    -- @Column(length = 100, name = "policy_number")
    policy_number           VARCHAR(100),

    -- @Column(length = 100, name = "employee_number")
    employee_number         VARCHAR(100),

    -- @Column(name = "join_date")
    join_date               DATE,

    -- @Column(length = 100)  →  column name = "occupation"
    occupation              VARCHAR(100),

    -- @Column(nullable = false, length = 20)  default ACTIVE  →  column name = "status"
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED','PENDING')),

    -- @Column(name = "start_date")
    start_date              DATE,

    -- @Column(name = "end_date")
    end_date                DATE,

    -- @Column(nullable = false, length = 20, name = "card_status")  default ACTIVE
    card_status             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (card_status IN ('ACTIVE','INACTIVE','BLOCKED','EXPIRED')),

    -- @Column(length = 500, name = "blocked_reason")
    blocked_reason          VARCHAR(500),

    -- @Column(nullable = false)  →  column name = "active"
    active                  BOOLEAN         NOT NULL DEFAULT true,

    -- @Column(nullable = false, name = "eligibility_status")  — Boolean
    eligibility_status      BOOLEAN         NOT NULL DEFAULT true,

    -- @Column(name = "eligibility_updated_at")
    eligibility_updated_at  TIMESTAMP,

    -- @Column(length = 500, name = "photo_url")
    photo_url               VARCHAR(500),

    -- @Column(length = 500, name = "profile_photo_path")
    profile_photo_path      VARCHAR(500),

    -- @Column(length = 2000)  →  column name = "notes"
    notes                   TEXT,

    -- @Column(name = "card_activated_at")
    card_activated_at       TIMESTAMP,

    -- @Column(name = "is_smart_card")
    is_smart_card           BOOLEAN,

    -- @Column(name = "is_vip")
    is_vip                  BOOLEAN,

    -- @Column(name = "is_urgent")
    is_urgent               BOOLEAN,

    -- @Column(length = 1000, name = "emergency_notes")
    emergency_notes         TEXT,

    -- Audit  (@CreatedDate / @LastModifiedDate / @Column created_by / updated_by)
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP,

    -- ── Foreign Keys ──────────────────────────────────────────
    CONSTRAINT fk_member_employer    FOREIGN KEY (employer_id)       REFERENCES employers(id)        ON DELETE RESTRICT,
    CONSTRAINT fk_member_policy      FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_parent      FOREIGN KEY (parent_id)         REFERENCES members(id)          ON DELETE SET NULL,

    -- ── Unique Constraints (@Table uniqueConstraints) ─────────
    CONSTRAINT uk_member_card_number UNIQUE (card_number),
    CONSTRAINT uk_member_barcode     UNIQUE (barcode)
);

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_members_employer        ON members(employer_id);
CREATE INDEX IF NOT EXISTS idx_members_active          ON members(active);
CREATE INDEX IF NOT EXISTS idx_members_status          ON members(status);
CREATE INDEX IF NOT EXISTS idx_members_parent_id       ON members(parent_id);
CREATE INDEX IF NOT EXISTS idx_members_barcode         ON members(barcode);
CREATE INDEX IF NOT EXISTS idx_members_card_number     ON members(card_number);
CREATE INDEX IF NOT EXISTS idx_members_civil_id        ON members(civil_id);
CREATE INDEX IF NOT EXISTS idx_members_national_number ON members(national_number);
CREATE INDEX IF NOT EXISTS idx_members_benefit_policy  ON members(benefit_policy_id);
CREATE INDEX IF NOT EXISTS idx_members_employer_active ON members(employer_id, active)
    WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_members_employer_search ON members(employer_id, civil_id, full_name)
    WHERE active = true;

-- ────────────────────────────────────────────────────────────
-- جدول الخصائص المرنة: member_attributes
-- المصدر: com.waad.tba.modules.member.entity.MemberAttribute
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS member_attributes (

    -- @Id @GeneratedValue(IDENTITY)
    id                BIGSERIAL       PRIMARY KEY,

    -- @ManyToOne member  →  @JoinColumn(name = "member_id", nullable = false)
    member_id         BIGINT          NOT NULL,

    -- @Column(name = "attribute_code", nullable = false, length = 100)
    attribute_code    VARCHAR(100)    NOT NULL,

    -- @Column(name = "attribute_value", columnDefinition = "TEXT")
    attribute_value   TEXT,

    -- @Enumerated @Column(length = 50)  →  column name = "source"
    source            VARCHAR(50)     DEFAULT 'MANUAL'
                      CHECK (source IN ('MANUAL','IMPORT','ODOO','API')),

    -- @Column(name = "source_reference", length = 200)
    source_reference  VARCHAR(200),

    -- Audit
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,

    CONSTRAINT fk_member_attrs_member  FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT uk_member_attribute_code UNIQUE (member_id, attribute_code)
);

CREATE INDEX IF NOT EXISTS idx_member_attributes_member ON member_attributes(member_id);
CREATE INDEX IF NOT EXISTS idx_member_attributes_code   ON member_attributes(attribute_code);

-- ────────────────────────────────────────────────────────────
-- جدول التحملات: member_deductibles
-- لا يوجد JPA entity — يُستخدم عبر JDBC فقط في UnifiedMemberService
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS member_deductibles (
    id                    BIGSERIAL       PRIMARY KEY,
    member_id             BIGINT          NOT NULL,
    deductible_year       INTEGER         NOT NULL,
    total_deductible      NUMERIC(10,2)   DEFAULT 0.00,
    deductible_used       NUMERIC(10,2)   DEFAULT 0.00,
    deductible_remaining  NUMERIC(10,2)   DEFAULT 0.00,
    version               BIGINT          DEFAULT 0,
    updated_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(255),

    CONSTRAINT fk_deductible_member      FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT uq_member_deductible_year UNIQUE (member_id, deductible_year),
    CONSTRAINT chk_deductible_non_negative CHECK (deductible_used >= 0 AND deductible_remaining >= 0)
);

CREATE INDEX IF NOT EXISTS idx_deductibles_member ON member_deductibles(member_id);
CREATE INDEX IF NOT EXISTS idx_deductibles_year   ON member_deductibles(deductible_year);

-- ────────────────────────────────────────────────────────────
-- جدول تعيينات السياسات: member_policy_assignments
-- لا يوجد JPA entity — يُستخدم عبر JDBC فقط في UnifiedMemberService
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS member_policy_assignments (
    id                    BIGSERIAL       PRIMARY KEY,
    member_id             BIGINT          NOT NULL,
    policy_id             BIGINT          NOT NULL,
    assignment_start_date DATE            NOT NULL,
    assignment_end_date   DATE,
    created_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(255),

    CONSTRAINT fk_assignment_member  FOREIGN KEY (member_id) REFERENCES members(id)          ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_policy  FOREIGN KEY (policy_id) REFERENCES benefit_policies(id)  ON DELETE RESTRICT,
    CONSTRAINT chk_assignment_dates  CHECK (assignment_end_date IS NULL OR assignment_end_date >= assignment_start_date)
);

CREATE INDEX IF NOT EXISTS idx_policy_assignments_member ON member_policy_assignments(member_id);
CREATE INDEX IF NOT EXISTS idx_policy_assignments_policy ON member_policy_assignments(policy_id);
