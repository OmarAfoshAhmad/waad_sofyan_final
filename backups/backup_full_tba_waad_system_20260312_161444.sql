--
-- PostgreSQL database dump
--

\restrict UUoleM3y8krhcEoQFe28M9ZC9ed4Ym3wvK7EqnAXDxIAWFa3bQONzQNJsbkKnjo

-- Dumped from database version 16.13 (Debian 16.13-1.pgdg13+1)
-- Dumped by pg_dump version 16.13 (Debian 16.13-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: trg_sync_login_attempt_result_fn(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.trg_sync_login_attempt_result_fn() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.attempt_result IS NOT NULL AND NEW.success IS NULL THEN
        NEW.success := (NEW.attempt_result = 'SUCCESS');
    ELSIF NEW.success IS NOT NULL AND NEW.attempt_result IS NULL THEN
        NEW.attempt_result := CASE WHEN NEW.success THEN 'SUCCESS' ELSE 'FAILURE' END;
    END IF;
    IF NEW.attempted_at IS NULL THEN NEW.attempted_at := COALESCE(NEW.created_at, CURRENT_TIMESTAMP); END IF;
    IF NEW.created_at   IS NULL THEN NEW.created_at   := NEW.attempted_at; END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.trg_sync_login_attempt_result_fn() OWNER TO postgres;

--
-- Name: trg_sync_user_audit_log_fn(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.trg_sync_user_audit_log_fn() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.action IS NULL THEN NEW.action := NEW.action_type; END IF;
    IF NEW.action_type IS NULL THEN NEW.action_type := NEW.action; END IF;
    IF NEW.details IS NULL THEN NEW.details := NEW.action_description; END IF;
    IF NEW.action_description IS NULL THEN NEW.action_description := NEW.details; END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.trg_sync_user_audit_log_fn() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: account_transactions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.account_transactions (
    id bigint NOT NULL,
    provider_account_id bigint NOT NULL,
    transaction_type character varying(50),
    amount numeric(12,2) NOT NULL,
    balance_before numeric(14,2) NOT NULL,
    balance_after numeric(14,2) NOT NULL,
    reference_type character varying(50),
    reference_id bigint,
    reference_number character varying(100),
    description text,
    transaction_date date NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    CONSTRAINT account_transactions_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT account_transactions_reference_type_check CHECK (((reference_type)::text = ANY ((ARRAY['CLAIM_APPROVAL'::character varying, 'SETTLEMENT_PAYMENT'::character varying, 'ADJUSTMENT'::character varying])::text[]))),
    CONSTRAINT account_transactions_transaction_type_check CHECK (((transaction_type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[]))),
    CONSTRAINT chk_balance_credit CHECK ((((transaction_type)::text <> 'CREDIT'::text) OR (balance_after = (balance_before + amount)))),
    CONSTRAINT chk_balance_debit CHECK ((((transaction_type)::text <> 'DEBIT'::text) OR (balance_after = (balance_before - amount)))),
    CONSTRAINT chk_balance_non_negative CHECK (((balance_before >= (0)::numeric) AND (balance_after >= (0)::numeric)))
);


ALTER TABLE public.account_transactions OWNER TO postgres;

--
-- Name: account_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.account_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.account_transactions_id_seq OWNER TO postgres;

--
-- Name: account_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.account_transactions_id_seq OWNED BY public.account_transactions.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id bigint,
    username character varying(50),
    action character varying(100) NOT NULL,
    entity_type character varying(50),
    entity_id bigint,
    details text,
    ip_address character varying(45),
    user_agent text
);


ALTER TABLE public.audit_logs OWNER TO postgres;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.audit_logs_id_seq OWNER TO postgres;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: benefit_policy_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.benefit_policy_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.benefit_policy_seq OWNER TO postgres;

--
-- Name: benefit_policies; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.benefit_policies (
    id bigint DEFAULT nextval('public.benefit_policy_seq'::regclass) NOT NULL,
    policy_name character varying(255) NOT NULL,
    policy_code character varying(50) NOT NULL,
    employer_id bigint NOT NULL,
    name character varying(255),
    annual_limit numeric(12,2),
    per_visit_limit numeric(10,2),
    deductible_amount numeric(10,2),
    copay_percentage numeric(5,2),
    annual_deductible numeric(15,2) DEFAULT 0.00,
    out_of_pocket_max numeric(15,2) DEFAULT 0.00,
    per_member_limit numeric(15,2),
    per_family_limit numeric(15,2),
    policy_type character varying(50),
    description text,
    notes character varying(1000),
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    start_date date,
    end_date date,
    effective_date date NOT NULL,
    expiry_date date,
    default_coverage_percent integer DEFAULT 80,
    default_waiting_period_days integer DEFAULT 0,
    covered_members_count integer DEFAULT 0,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT benefit_policies_policy_type_check CHECK (((policy_type)::text = ANY ((ARRAY['BASIC'::character varying, 'PREMIUM'::character varying, 'EXECUTIVE'::character varying, 'CUSTOM'::character varying])::text[]))),
    CONSTRAINT benefit_policies_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying])::text[]))),
    CONSTRAINT chk_policy_dates CHECK (((expiry_date IS NULL) OR (expiry_date >= effective_date)))
);


ALTER TABLE public.benefit_policies OWNER TO postgres;

--
-- Name: benefit_policy_rules; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.benefit_policy_rules (
    id bigint NOT NULL,
    policy_id bigint NOT NULL,
    service_category character varying(100),
    medical_category_id bigint,
    medical_service_id bigint,
    coverage_percentage numeric(5,2),
    coverage_percent integer,
    max_sessions_per_year integer,
    times_limit integer,
    requires_preauth boolean DEFAULT false,
    requires_pre_approval boolean DEFAULT false,
    waiting_period_days integer,
    max_amount_per_session numeric(10,2),
    max_amount_per_year numeric(12,2),
    amount_limit numeric(15,2),
    notes character varying(500),
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    created_by character varying(255)
);


ALTER TABLE public.benefit_policy_rules OWNER TO postgres;

--
-- Name: benefit_policy_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.benefit_policy_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.benefit_policy_rules_id_seq OWNER TO postgres;

--
-- Name: benefit_policy_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.benefit_policy_rules_id_seq OWNED BY public.benefit_policy_rules.id;


--
-- Name: claim_attachments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.claim_attachments (
    id bigint NOT NULL,
    claim_id bigint NOT NULL,
    file_name character varying(500) NOT NULL,
    file_path character varying(500),
    created_at timestamp without time zone NOT NULL,
    file_url character varying(1000),
    original_file_name character varying(500),
    file_key character varying(500),
    file_type character varying(100),
    file_size bigint,
    attachment_type character varying(50),
    uploaded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    uploaded_by character varying(255),
    CONSTRAINT claim_attachments_attachment_type_check CHECK (((attachment_type)::text = ANY ((ARRAY['PRESCRIPTION'::character varying, 'LAB_RESULT'::character varying, 'XRAY'::character varying, 'REFERRAL_LETTER'::character varying, 'DISCHARGE_SUMMARY'::character varying, 'OTHER'::character varying])::text[])))
);


ALTER TABLE public.claim_attachments OWNER TO postgres;

--
-- Name: claim_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.claim_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.claim_attachments_id_seq OWNER TO postgres;

--
-- Name: claim_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.claim_attachments_id_seq OWNED BY public.claim_attachments.id;


--
-- Name: claim_audit_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.claim_audit_logs (
    id bigint NOT NULL,
    claim_id bigint NOT NULL,
    change_type character varying(50) NOT NULL,
    previous_status character varying(30),
    new_status character varying(30),
    previous_requested_amount numeric(15,2),
    new_requested_amount numeric(15,2),
    previous_approved_amount numeric(15,2),
    new_approved_amount numeric(15,2),
    actor_user_id bigint NOT NULL,
    actor_username character varying(100) NOT NULL,
    actor_role character varying(50) NOT NULL,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    comment text,
    ip_address character varying(45),
    before_snapshot text,
    after_snapshot text
);


ALTER TABLE public.claim_audit_logs OWNER TO postgres;

--
-- Name: claim_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.claim_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.claim_audit_logs_id_seq OWNER TO postgres;

--
-- Name: claim_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.claim_audit_logs_id_seq OWNED BY public.claim_audit_logs.id;


--
-- Name: claim_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.claim_history (
    id bigint NOT NULL,
    claim_id bigint NOT NULL,
    old_status character varying(50),
    new_status character varying(50),
    changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    changed_by character varying(255),
    reason text
);


ALTER TABLE public.claim_history OWNER TO postgres;

--
-- Name: claim_history_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.claim_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.claim_history_id_seq OWNER TO postgres;

--
-- Name: claim_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.claim_history_id_seq OWNED BY public.claim_history.id;


--
-- Name: claim_lines; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.claim_lines (
    id bigint NOT NULL,
    claim_id bigint NOT NULL,
    service_code character varying(50) NOT NULL,
    service_description character varying(255),
    quantity integer,
    unit_price numeric(15,2),
    total_amount numeric(15,2),
    total_price numeric(15,2) NOT NULL,
    medical_service_id bigint,
    service_name character varying(255),
    service_category_id bigint,
    service_category_name character varying(200),
    requires_pa boolean DEFAULT false NOT NULL,
    line_number integer,
    approved_amount numeric(15,2),
    approved_units integer,
    approval_notes text,
    coverage_percent_snapshot integer,
    patient_copay_percent_snapshot integer,
    times_limit_snapshot integer,
    amount_limit_snapshot numeric(15,2),
    refused_amount numeric(15,2) DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    version bigint DEFAULT 0 NOT NULL,
    rejection_reason character varying(500),
    rejection_reason_code character varying(50),
    reviewer_notes text,
    rejected boolean DEFAULT false,
    requested_unit_price numeric(15,2),
    approved_unit_price numeric(15,2),
    requested_quantity integer,
    approved_quantity integer,
    CONSTRAINT claim_lines_total_amount_check CHECK ((total_amount >= (0)::numeric)),
    CONSTRAINT claim_lines_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


ALTER TABLE public.claim_lines OWNER TO postgres;

--
-- Name: claim_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.claim_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.claim_lines_id_seq OWNER TO postgres;

--
-- Name: claim_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.claim_lines_id_seq OWNED BY public.claim_lines.id;


--
-- Name: claim_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.claim_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.claim_seq OWNER TO postgres;

--
-- Name: claims; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.claims (
    id bigint DEFAULT nextval('public.claim_seq'::regclass) NOT NULL,
    claim_number character varying(100) NOT NULL,
    external_claim_ref character varying(100),
    member_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    provider_name character varying(255),
    visit_id bigint,
    service_date date NOT NULL,
    diagnosis_code character varying(50),
    diagnosis_description text,
    requested_amount numeric(15,2) NOT NULL,
    approved_amount numeric(15,2),
    paid_amount numeric(15,2),
    patient_share numeric(15,2),
    refused_amount numeric(15,2) DEFAULT 0,
    difference_amount numeric(15,2),
    patient_copay numeric(15,2),
    net_provider_amount numeric(15,2),
    copay_percent numeric(5,2),
    deductible_applied numeric(15,2),
    status character varying(50) NOT NULL,
    submitted_at timestamp without time zone,
    reviewer_id bigint,
    reviewed_at timestamp without time zone,
    approval_reason text,
    reviewer_comment text,
    doctor_name character varying(255),
    pre_authorization_id bigint,
    payment_reference character varying(100),
    settled_at timestamp without time zone,
    settlement_notes text,
    expected_completion_date date,
    actual_completion_date date,
    within_sla boolean,
    business_days_taken integer,
    sla_days_configured integer,
    service_count integer,
    attachments_count integer,
    is_backlog boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255),
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_claim_date CHECK (((service_date <= CURRENT_DATE) AND (service_date >= (CURRENT_DATE - '10 years'::interval)))),
    CONSTRAINT claims_requested_amount_check CHECK ((requested_amount >= (0)::numeric)),
    CONSTRAINT claims_status_check CHECK (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'BATCHED'::character varying, 'SETTLED'::character varying, 'REJECTED'::character varying, 'SUBMITTED'::character varying, 'UNDER_REVIEW'::character varying, 'RETURNED_FOR_INFO'::character varying, 'PENDING_APPROVAL'::character varying, 'BACKLOG_IMPORT'::character varying])::text[])))
);


ALTER TABLE public.claims OWNER TO postgres;

--
-- Name: cpt_codes; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.cpt_codes (
    id bigint NOT NULL,
    code character varying(20) NOT NULL,
    description character varying(500) NOT NULL,
    category character varying(100),
    sub_category character varying(100),
    procedure_type character varying(20),
    standard_price numeric(15,2),
    max_allowed_price numeric(15,2),
    min_allowed_price numeric(15,2),
    covered boolean DEFAULT true,
    co_payment_percentage numeric(5,2),
    requires_pre_auth boolean DEFAULT false,
    notes character varying(2000),
    active boolean DEFAULT true,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


ALTER TABLE public.cpt_codes OWNER TO postgres;

--
-- Name: cpt_codes_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.cpt_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.cpt_codes_id_seq OWNER TO postgres;

--
-- Name: cpt_codes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.cpt_codes_id_seq OWNED BY public.cpt_codes.id;


--
-- Name: eligibility_checks; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.eligibility_checks (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    check_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_eligible boolean NOT NULL,
    eligibility_reason text,
    policy_id bigint,
    coverage_status character varying(50),
    visit_id bigint,
    checked_by character varying(255),
    request_id character varying(36) NOT NULL,
    check_timestamp timestamp without time zone NOT NULL,
    provider_id bigint,
    service_date date NOT NULL,
    service_code character varying(50),
    eligible boolean NOT NULL,
    status character varying(50) NOT NULL,
    reasons text,
    member_name character varying(255),
    member_civil_id character varying(50),
    member_status character varying(30),
    policy_number character varying(100),
    policy_status character varying(30),
    policy_start_date date,
    policy_end_date date,
    employer_id bigint,
    employer_name character varying(255),
    checked_by_user_id bigint,
    checked_by_username character varying(100),
    company_scope_id bigint,
    ip_address character varying(45),
    user_agent character varying(500),
    processing_time_ms integer,
    rules_evaluated integer,
    created_at timestamp without time zone NOT NULL
);


ALTER TABLE public.eligibility_checks OWNER TO postgres;

--
-- Name: eligibility_checks_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.eligibility_checks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.eligibility_checks_id_seq OWNER TO postgres;

--
-- Name: eligibility_checks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.eligibility_checks_id_seq OWNED BY public.eligibility_checks.id;


--
-- Name: email_verification_tokens; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.email_verification_tokens (
    id bigint NOT NULL,
    token character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    expiry_date timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    verified boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.email_verification_tokens OWNER TO postgres;

--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.email_verification_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.email_verification_tokens_id_seq OWNER TO postgres;

--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.email_verification_tokens_id_seq OWNED BY public.email_verification_tokens.id;


--
-- Name: employer_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.employer_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.employer_seq OWNER TO postgres;

--
-- Name: employers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employers (
    id bigint DEFAULT nextval('public.employer_seq'::regclass) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    address text,
    phone character varying(50),
    email character varying(255),
    logo_url character varying(500),
    website character varying(200),
    business_type character varying(100),
    tax_number character varying(50),
    commercial_registration_number character varying(50),
    active boolean DEFAULT true,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    cr_number character varying(50),
    contract_start_date date,
    contract_end_date date,
    max_member_limit integer
);


ALTER TABLE public.employers OWNER TO postgres;

--
-- Name: ent_service_alias_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ent_service_alias_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ent_service_alias_seq OWNER TO postgres;

--
-- Name: ent_service_aliases; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ent_service_aliases (
    id bigint DEFAULT nextval('public.ent_service_alias_seq'::regclass) NOT NULL,
    medical_service_id bigint NOT NULL,
    alias_text character varying(255) NOT NULL,
    locale character varying(10) DEFAULT 'ar'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(255)
);


ALTER TABLE public.ent_service_aliases OWNER TO postgres;

--
-- Name: feature_flags; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.feature_flags (
    id bigint NOT NULL,
    flag_key character varying(100) NOT NULL,
    flag_name character varying(255) NOT NULL,
    description text,
    enabled boolean DEFAULT true,
    role_filters json,
    created_by character varying(50),
    updated_by character varying(50),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


ALTER TABLE public.feature_flags OWNER TO postgres;

--
-- Name: feature_flags_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.feature_flags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.feature_flags_id_seq OWNER TO postgres;

--
-- Name: feature_flags_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.feature_flags_id_seq OWNED BY public.feature_flags.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO postgres;

--
-- Name: icd_codes; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.icd_codes (
    id bigint NOT NULL,
    code character varying(20) NOT NULL,
    description character varying(500) NOT NULL,
    category character varying(50),
    sub_category character varying(100),
    version character varying(20),
    notes character varying(2000),
    active boolean DEFAULT true,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


ALTER TABLE public.icd_codes OWNER TO postgres;

--
-- Name: icd_codes_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.icd_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.icd_codes_id_seq OWNER TO postgres;

--
-- Name: icd_codes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.icd_codes_id_seq OWNED BY public.icd_codes.id;


--
-- Name: legacy_provider_contracts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.legacy_provider_contracts (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    service_code character varying(50) NOT NULL,
    contract_price numeric(10,2) NOT NULL,
    currency character varying(3) DEFAULT 'LYD'::character varying,
    effective_from date NOT NULL,
    effective_to date,
    active boolean DEFAULT true,
    notes character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    created_by character varying(100),
    updated_by character varying(100)
);


ALTER TABLE public.legacy_provider_contracts OWNER TO postgres;

--
-- Name: legacy_provider_contracts_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.legacy_provider_contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.legacy_provider_contracts_id_seq OWNER TO postgres;

--
-- Name: legacy_provider_contracts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.legacy_provider_contracts_id_seq OWNED BY public.legacy_provider_contracts.id;


--
-- Name: medical_category_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_category_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_category_seq OWNER TO postgres;

--
-- Name: medical_categories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_categories (
    id bigint DEFAULT nextval('public.medical_category_seq'::regclass) NOT NULL,
    category_name character varying(255) NOT NULL,
    category_name_ar character varying(255),
    category_code character varying(50) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    name_ar character varying(200),
    name_en character varying(200),
    parent_id bigint,
    context character varying(20) DEFAULT 'ANY'::character varying NOT NULL,
    description text,
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT medical_categories_context_check CHECK (((context)::text = ANY ((ARRAY['INPATIENT'::character varying, 'OUTPATIENT'::character varying, 'OPERATING_ROOM'::character varying, 'EMERGENCY'::character varying, 'SPECIAL'::character varying, 'ANY'::character varying])::text[])))
);


ALTER TABLE public.medical_categories OWNER TO postgres;

--
-- Name: medical_reviewer_providers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_reviewer_providers (
    id bigint NOT NULL,
    reviewer_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(255),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(255)
);


ALTER TABLE public.medical_reviewer_providers OWNER TO postgres;

--
-- Name: medical_reviewer_providers_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_reviewer_providers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_reviewer_providers_id_seq OWNER TO postgres;

--
-- Name: medical_reviewer_providers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.medical_reviewer_providers_id_seq OWNED BY public.medical_reviewer_providers.id;


--
-- Name: medical_service_category_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_service_category_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_service_category_seq OWNER TO postgres;

--
-- Name: medical_service_categories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_service_categories (
    id bigint DEFAULT nextval('public.medical_service_category_seq'::regclass) NOT NULL,
    service_id bigint NOT NULL,
    category_id bigint NOT NULL,
    context character varying(20) DEFAULT 'ANY'::character varying NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(255),
    CONSTRAINT medical_service_categories_context_check CHECK (((context)::text = ANY ((ARRAY['OUTPATIENT'::character varying, 'INPATIENT'::character varying, 'EMERGENCY'::character varying, 'ANY'::character varying])::text[])))
);


ALTER TABLE public.medical_service_categories OWNER TO postgres;

--
-- Name: medical_service_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_service_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_service_seq OWNER TO postgres;

--
-- Name: medical_services; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_services (
    id bigint DEFAULT nextval('public.medical_service_seq'::regclass) NOT NULL,
    category_id bigint NOT NULL,
    service_name character varying(255) NOT NULL,
    service_name_ar character varying(255),
    service_code character varying(50) NOT NULL,
    name character varying(255),
    name_ar character varying(255),
    name_en character varying(255),
    code character varying(50),
    cost numeric(15,2),
    is_master boolean DEFAULT false NOT NULL,
    requires_pa boolean DEFAULT false NOT NULL,
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    description text,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255)
);


ALTER TABLE public.medical_services OWNER TO postgres;

--
-- Name: medical_specialties; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_specialties (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name_ar character varying(255) NOT NULL,
    name_en character varying(255),
    deleted boolean DEFAULT false NOT NULL
);


ALTER TABLE public.medical_specialties OWNER TO postgres;

--
-- Name: medical_specialties_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_specialties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_specialties_id_seq OWNER TO postgres;

--
-- Name: medical_specialties_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.medical_specialties_id_seq OWNED BY public.medical_specialties.id;


--
-- Name: member_attributes; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.member_attributes (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    attribute_code character varying(100) NOT NULL,
    attribute_value text,
    source character varying(50),
    source_reference character varying(200),
    created_by character varying(100),
    updated_by character varying(100),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


ALTER TABLE public.member_attributes OWNER TO postgres;

--
-- Name: member_attributes_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_attributes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_attributes_id_seq OWNER TO postgres;

--
-- Name: member_attributes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.member_attributes_id_seq OWNED BY public.member_attributes.id;


--
-- Name: member_deductibles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.member_deductibles (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    deductible_year integer NOT NULL,
    total_deductible numeric(10,2) DEFAULT 0.00,
    deductible_used numeric(10,2) DEFAULT 0.00,
    deductible_remaining numeric(10,2) DEFAULT 0.00,
    version bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(255),
    CONSTRAINT chk_deductible_math CHECK ((deductible_remaining = (total_deductible - deductible_used))),
    CONSTRAINT chk_deductible_non_negative CHECK (((deductible_used >= (0)::numeric) AND (deductible_remaining >= (0)::numeric)))
);


ALTER TABLE public.member_deductibles OWNER TO postgres;

--
-- Name: member_deductibles_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_deductibles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_deductibles_id_seq OWNER TO postgres;

--
-- Name: member_deductibles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.member_deductibles_id_seq OWNED BY public.member_deductibles.id;


--
-- Name: member_import_errors; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.member_import_errors (
    id bigint NOT NULL,
    import_log_id bigint NOT NULL,
    row_number integer NOT NULL,
    row_data jsonb,
    error_type character varying(50),
    error_field character varying(100),
    error_message text,
    created_at timestamp without time zone
);


ALTER TABLE public.member_import_errors OWNER TO postgres;

--
-- Name: member_import_errors_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_import_errors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_import_errors_id_seq OWNER TO postgres;

--
-- Name: member_import_errors_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.member_import_errors_id_seq OWNED BY public.member_import_errors.id;


--
-- Name: member_import_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.member_import_logs (
    id bigint NOT NULL,
    import_batch_id character varying(64) NOT NULL,
    file_name character varying(500),
    file_size_bytes bigint,
    total_rows integer DEFAULT 0,
    created_count integer DEFAULT 0,
    updated_count integer DEFAULT 0,
    skipped_count integer DEFAULT 0,
    error_count integer DEFAULT 0,
    status character varying(30) DEFAULT 'PENDING'::character varying,
    error_message text,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    processing_time_ms bigint,
    imported_by_user_id bigint,
    imported_by_username character varying(100),
    company_scope_id bigint,
    ip_address character varying(45),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT member_import_logs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VALIDATING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying])::text[])))
);


ALTER TABLE public.member_import_logs OWNER TO postgres;

--
-- Name: member_import_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_import_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_import_logs_id_seq OWNER TO postgres;

--
-- Name: member_import_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.member_import_logs_id_seq OWNED BY public.member_import_logs.id;


--
-- Name: member_policy_assignments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.member_policy_assignments (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    policy_id bigint NOT NULL,
    assignment_start_date date NOT NULL,
    assignment_end_date date,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    CONSTRAINT chk_assignment_dates CHECK (((assignment_end_date IS NULL) OR (assignment_end_date >= assignment_start_date)))
);


ALTER TABLE public.member_policy_assignments OWNER TO postgres;

--
-- Name: member_policy_assignments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_policy_assignments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_policy_assignments_id_seq OWNER TO postgres;

--
-- Name: member_policy_assignments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.member_policy_assignments_id_seq OWNED BY public.member_policy_assignments.id;


--
-- Name: member_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.member_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.member_seq OWNER TO postgres;

--
-- Name: members; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.members (
    id bigint DEFAULT nextval('public.member_seq'::regclass) NOT NULL,
    member_card_id character varying(100) NOT NULL,
    full_name character varying(255) NOT NULL,
    full_name_ar character varying(255),
    date_of_birth date NOT NULL,
    gender character varying(20),
    national_id character varying(50),
    employer_id bigint,
    employee_id character varying(100),
    employee_number character varying(100),
    membership_type character varying(50),
    relation_to_employee character varying(50),
    relationship character varying(50),
    parent_id bigint,
    email character varying(255),
    phone character varying(50),
    address text,
    coverage_start_date date,
    coverage_end_date date,
    policy_number character varying(100),
    start_date date,
    end_date date,
    join_date date,
    benefit_policy_id bigint,
    barcode character varying(100),
    birth_date date,
    card_number character varying(50),
    card_status character varying(30),
    card_activated_at timestamp without time zone,
    is_smart_card boolean DEFAULT false,
    civil_id character varying(50),
    national_number character varying(50),
    photo_url character varying(500),
    profile_photo_path character varying(500),
    marital_status character varying(20),
    nationality character varying(100),
    occupation character varying(100),
    notes text,
    emergency_notes text,
    is_vip boolean DEFAULT false,
    is_urgent boolean DEFAULT false,
    blocked_reason character varying(500),
    status character varying(30) DEFAULT 'ACTIVE'::character varying,
    eligibility_status character varying(30),
    eligibility_updated_at timestamp without time zone,
    version bigint DEFAULT 0,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_coverage_dates CHECK (((coverage_end_date IS NULL) OR (coverage_end_date >= coverage_start_date))),
    CONSTRAINT members_gender_check CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying])::text[]))),
    CONSTRAINT members_membership_type_check CHECK (((membership_type)::text = ANY ((ARRAY['PRIMARY'::character varying, 'DEPENDENT'::character varying])::text[])))
);


ALTER TABLE public.members OWNER TO postgres;

--
-- Name: module_access; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.module_access (
    id bigint NOT NULL,
    module_name character varying(100) NOT NULL,
    module_key character varying(100) NOT NULL,
    description text,
    allowed_roles json NOT NULL,
    required_permissions json,
    feature_flag_key character varying(100),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


ALTER TABLE public.module_access OWNER TO postgres;

--
-- Name: module_access_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.module_access_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.module_access_id_seq OWNER TO postgres;

--
-- Name: module_access_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.module_access_id_seq OWNED BY public.module_access.id;


--
-- Name: network_providers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.network_providers (
    id bigint NOT NULL,
    employer_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    network_tier character varying(50),
    effective_date date NOT NULL,
    expiry_date date,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    CONSTRAINT network_providers_network_tier_check CHECK (((network_tier)::text = ANY ((ARRAY['TIER_1'::character varying, 'TIER_2'::character varying, 'TIER_3'::character varying, 'OUT_OF_NETWORK'::character varying])::text[])))
);


ALTER TABLE public.network_providers OWNER TO postgres;

--
-- Name: network_providers_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.network_providers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.network_providers_id_seq OWNER TO postgres;

--
-- Name: network_providers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.network_providers_id_seq OWNED BY public.network_providers.id;


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.password_reset_tokens (
    id bigint NOT NULL,
    token character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    expiry_date timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.password_reset_tokens OWNER TO postgres;

--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.password_reset_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.password_reset_tokens_id_seq OWNER TO postgres;

--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.password_reset_tokens_id_seq OWNED BY public.password_reset_tokens.id;


--
-- Name: pdf_company_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pdf_company_settings (
    id bigint NOT NULL,
    company_name character varying(255) NOT NULL,
    logo_url character varying(512),
    logo_data bytea,
    address text,
    phone character varying(50),
    email character varying(100),
    website character varying(255),
    footer_text text,
    footer_text_en text,
    header_color character varying(7),
    footer_color character varying(7),
    page_size character varying(20),
    margin_top integer,
    margin_bottom integer,
    margin_left integer,
    margin_right integer,
    is_active boolean,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(100),
    updated_by character varying(100)
);


ALTER TABLE public.pdf_company_settings OWNER TO postgres;

--
-- Name: pdf_company_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.pdf_company_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pdf_company_settings_id_seq OWNER TO postgres;

--
-- Name: pdf_company_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.pdf_company_settings_id_seq OWNED BY public.pdf_company_settings.id;


--
-- Name: pre_authorization_attachments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pre_authorization_attachments (
    id bigint NOT NULL,
    preauthorization_request_id bigint NOT NULL,
    file_name character varying(500) NOT NULL,
    file_path character varying(500),
    file_type character varying(100),
    file_size bigint,
    attachment_type character varying(50),
    uploaded_by character varying(255),
    uploaded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.pre_authorization_attachments OWNER TO postgres;

--
-- Name: pre_authorization_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.pre_authorization_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pre_authorization_attachments_id_seq OWNER TO postgres;

--
-- Name: pre_authorization_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.pre_authorization_attachments_id_seq OWNED BY public.pre_authorization_attachments.id;


--
-- Name: pre_authorization_audit; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pre_authorization_audit (
    id bigint NOT NULL,
    pre_authorization_id bigint NOT NULL,
    reference_number character varying(50),
    changed_by character varying(100) NOT NULL,
    change_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    action character varying(20) NOT NULL,
    field_name character varying(50),
    old_value text,
    new_value text,
    notes character varying(500),
    ip_address character varying(45)
);


ALTER TABLE public.pre_authorization_audit OWNER TO postgres;

--
-- Name: pre_authorization_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.pre_authorization_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pre_authorization_audit_id_seq OWNER TO postgres;

--
-- Name: pre_authorization_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.pre_authorization_audit_id_seq OWNED BY public.pre_authorization_audit.id;


--
-- Name: pre_authorizations; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pre_authorizations (
    id bigint NOT NULL,
    active boolean DEFAULT true,
    approved_amount numeric(15,2),
    approved_at timestamp without time zone,
    approved_by character varying(255),
    contract_price numeric(15,2),
    copay_amount numeric(15,2),
    copay_percentage numeric(10,2),
    coverage_percent_snapshot integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    currency character varying(20),
    diagnosis_code character varying(100),
    diagnosis_description text,
    expected_service_date date,
    expiry_date date,
    insurance_covered_amount numeric(15,2),
    medical_service_id bigint,
    member_id bigint,
    notes text,
    patient_copay_percent_snapshot integer,
    pre_auth_number character varying(100),
    priority character varying(50),
    provider_id bigint,
    reference_number character varying(100),
    rejection_reason text,
    request_date timestamp without time zone,
    requires_pa boolean,
    reserved_amount numeric(15,2),
    service_category_id bigint,
    service_category_name character varying(255),
    service_code character varying(100),
    service_name character varying(255),
    service_type character varying(100),
    status character varying(50),
    updated_at timestamp without time zone,
    updated_by character varying(255),
    version bigint,
    visit_id bigint
);


ALTER TABLE public.pre_authorizations OWNER TO postgres;

--
-- Name: pre_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.pre_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pre_authorizations_id_seq OWNER TO postgres;

--
-- Name: pre_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.pre_authorizations_id_seq OWNED BY public.pre_authorizations.id;


--
-- Name: preauth_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.preauth_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.preauth_seq OWNER TO postgres;

--
-- Name: preauthorization_requests; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.preauthorization_requests (
    id bigint NOT NULL,
    request_number character varying(100),
    provider_id bigint NOT NULL,
    member_id bigint NOT NULL,
    service_date date,
    requested_service_date date,
    diagnosis_code character varying(50),
    diagnosis_description text,
    requested_amount numeric(15,2),
    approved_amount numeric(15,2),
    status character varying(50),
    valid_from timestamp without time zone,
    valid_until timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    approved_at timestamp without time zone,
    created_by character varying(255),
    approved_by character varying(255),
    CONSTRAINT preauthorization_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.preauthorization_requests OWNER TO postgres;

--
-- Name: preauthorization_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.preauthorization_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.preauthorization_requests_id_seq OWNER TO postgres;

--
-- Name: preauthorization_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.preauthorization_requests_id_seq OWNED BY public.preauthorization_requests.id;


--
-- Name: provider_accounts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_accounts (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    account_type character varying(50),
    currency character varying(3) DEFAULT 'LYD'::character varying,
    current_balance numeric(14,2) DEFAULT 0.00,
    total_payable numeric(14,2) DEFAULT 0.00,
    version bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balance_non_negative CHECK ((current_balance >= (0)::numeric))
);


ALTER TABLE public.provider_accounts OWNER TO postgres;

--
-- Name: provider_accounts_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_accounts_id_seq OWNER TO postgres;

--
-- Name: provider_accounts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_accounts_id_seq OWNED BY public.provider_accounts.id;


--
-- Name: provider_admin_documents; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_admin_documents (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    document_name character varying(255) NOT NULL,
    document_type character varying(100) NOT NULL,
    file_path character varying(500) NOT NULL,
    file_size bigint,
    uploaded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    uploaded_by character varying(255)
);


ALTER TABLE public.provider_admin_documents OWNER TO postgres;

--
-- Name: provider_admin_documents_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_admin_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_admin_documents_id_seq OWNER TO postgres;

--
-- Name: provider_admin_documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_admin_documents_id_seq OWNED BY public.provider_admin_documents.id;


--
-- Name: provider_allowed_employers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_allowed_employers (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    employer_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255)
);


ALTER TABLE public.provider_allowed_employers OWNER TO postgres;

--
-- Name: provider_allowed_employers_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_allowed_employers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_allowed_employers_id_seq OWNER TO postgres;

--
-- Name: provider_allowed_employers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_allowed_employers_id_seq OWNED BY public.provider_allowed_employers.id;


--
-- Name: provider_contract_pricing_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_contract_pricing_items (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    medical_service_id bigint,
    service_category character varying(100),
    unit_price numeric(15,2) NOT NULL,
    effective_from date,
    effective_to date,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    created_by character varying(255)
);


ALTER TABLE public.provider_contract_pricing_items OWNER TO postgres;

--
-- Name: provider_contract_pricing_items_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_contract_pricing_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_contract_pricing_items_id_seq OWNER TO postgres;

--
-- Name: provider_contract_pricing_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_contract_pricing_items_id_seq OWNED BY public.provider_contract_pricing_items.id;


--
-- Name: provider_contract_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_contract_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_contract_seq OWNER TO postgres;

--
-- Name: provider_contracts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_contracts (
    id bigint DEFAULT nextval('public.provider_contract_seq'::regclass) NOT NULL,
    provider_id bigint NOT NULL,
    employer_id bigint NOT NULL,
    contract_number character varying(100) NOT NULL,
    contract_start_date date NOT NULL,
    contract_end_date date,
    discount_percent numeric(5,2),
    payment_terms character varying(100),
    max_sessions_per_service integer,
    requires_preauthorization boolean DEFAULT false,
    contract_status character varying(50),
    active boolean DEFAULT true,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_contract_dates CHECK (((contract_end_date IS NULL) OR (contract_end_date >= contract_start_date))),
    CONSTRAINT provider_contracts_contract_status_check CHECK (((contract_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'TERMINATED'::character varying])::text[])))
);


ALTER TABLE public.provider_contracts OWNER TO postgres;

--
-- Name: provider_mapping_audit; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_mapping_audit (
    id bigint NOT NULL,
    provider_raw_service_id bigint,
    action character varying(50) NOT NULL,
    old_value text,
    new_value text,
    performed_by bigint,
    performed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.provider_mapping_audit OWNER TO postgres;

--
-- Name: provider_mapping_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_mapping_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_mapping_audit_id_seq OWNER TO postgres;

--
-- Name: provider_mapping_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_mapping_audit_id_seq OWNED BY public.provider_mapping_audit.id;


--
-- Name: provider_payments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_payments (
    id bigint NOT NULL,
    settlement_batch_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_reference character varying(100) NOT NULL,
    payment_method character varying(50),
    payment_date timestamp without time zone NOT NULL,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.provider_payments OWNER TO postgres;

--
-- Name: provider_payments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_payments_id_seq OWNER TO postgres;

--
-- Name: provider_payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_payments_id_seq OWNED BY public.provider_payments.id;


--
-- Name: provider_raw_services; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_raw_services (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    raw_name character varying(500) NOT NULL,
    normalized_name character varying(500),
    code character varying(100),
    encounter_type character varying(20),
    source character varying(50),
    import_batch_id bigint,
    status character varying(30) DEFAULT 'PENDING'::character varying,
    confidence_score numeric(5,2),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT provider_raw_services_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'AUTO_MATCHED'::character varying, 'MANUAL_CONFIRMED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.provider_raw_services OWNER TO postgres;

--
-- Name: provider_raw_services_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_raw_services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_raw_services_id_seq OWNER TO postgres;

--
-- Name: provider_raw_services_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_raw_services_id_seq OWNED BY public.provider_raw_services.id;


--
-- Name: provider_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_seq OWNER TO postgres;

--
-- Name: provider_service_mappings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_service_mappings (
    id bigint NOT NULL,
    provider_raw_service_id bigint NOT NULL,
    medical_service_id bigint NOT NULL,
    mapping_status character varying(30) NOT NULL,
    mapped_by bigint,
    mapped_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    confidence_score numeric(5,2)
);


ALTER TABLE public.provider_service_mappings OWNER TO postgres;

--
-- Name: provider_service_mappings_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_service_mappings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_service_mappings_id_seq OWNER TO postgres;

--
-- Name: provider_service_mappings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_service_mappings_id_seq OWNED BY public.provider_service_mappings.id;


--
-- Name: provider_service_price_import_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_service_price_import_logs (
    id bigint NOT NULL,
    import_batch_id character varying(64) NOT NULL,
    provider_id bigint NOT NULL,
    provider_code character varying(100) NOT NULL,
    provider_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_size_bytes bigint,
    import_mode character varying(20) DEFAULT 'REPLACE'::character varying NOT NULL,
    total_rows integer DEFAULT 0,
    success_count integer DEFAULT 0,
    error_count integer DEFAULT 0,
    skipped_count integer DEFAULT 0,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    error_details jsonb,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    processing_time_ms bigint,
    imported_by_user_id bigint,
    imported_by_username character varying(100),
    created_at timestamp without time zone
);


ALTER TABLE public.provider_service_price_import_logs OWNER TO postgres;

--
-- Name: provider_service_price_import_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_service_price_import_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_service_price_import_logs_id_seq OWNER TO postgres;

--
-- Name: provider_service_price_import_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_service_price_import_logs_id_seq OWNED BY public.provider_service_price_import_logs.id;


--
-- Name: provider_services; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.provider_services (
    id bigint NOT NULL,
    provider_id bigint NOT NULL,
    service_code character varying(50) NOT NULL,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.provider_services OWNER TO postgres;

--
-- Name: provider_services_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.provider_services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.provider_services_id_seq OWNER TO postgres;

--
-- Name: provider_services_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.provider_services_id_seq OWNED BY public.provider_services.id;


--
-- Name: providers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.providers (
    id bigint DEFAULT nextval('public.provider_seq'::regclass) NOT NULL,
    provider_name character varying(255) NOT NULL,
    provider_name_ar character varying(255),
    license_number character varying(100) NOT NULL,
    provider_type character varying(50) NOT NULL,
    contact_person character varying(255),
    contact_email character varying(255),
    contact_phone character varying(50),
    address text,
    city character varying(100),
    region character varying(100),
    bank_name character varying(255),
    bank_account_number character varying(100),
    iban character varying(50),
    allow_all_employers boolean DEFAULT false,
    tax_company_code character varying(50),
    principal_name character varying(255),
    principal_phone character varying(50),
    principal_email character varying(255),
    principal_mobile character varying(50),
    principal_address text,
    secondary_contact character varying(255),
    secondary_contact_phone character varying(50),
    secondary_contact_email character varying(255),
    accounting_person character varying(255),
    accounting_phone character varying(50),
    accounting_email character varying(255),
    provider_status character varying(50),
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT providers_provider_type_check CHECK (((provider_type)::text = ANY ((ARRAY['HOSPITAL'::character varying, 'CLINIC'::character varying, 'PHARMACY'::character varying, 'LAB'::character varying, 'RADIOLOGY'::character varying, 'OTHER'::character varying])::text[])))
);


ALTER TABLE public.providers OWNER TO postgres;

--
-- Name: settlement_batch_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.settlement_batch_items (
    id bigint NOT NULL,
    batch_id bigint NOT NULL,
    claim_id bigint NOT NULL,
    claim_amount numeric(12,2) NOT NULL,
    added_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    added_by character varying(255)
);


ALTER TABLE public.settlement_batch_items OWNER TO postgres;

--
-- Name: settlement_batch_items_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.settlement_batch_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.settlement_batch_items_id_seq OWNER TO postgres;

--
-- Name: settlement_batch_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.settlement_batch_items_id_seq OWNED BY public.settlement_batch_items.id;


--
-- Name: settlement_batch_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.settlement_batch_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.settlement_batch_seq OWNER TO postgres;

--
-- Name: settlement_batches; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.settlement_batches (
    id bigint DEFAULT nextval('public.settlement_batch_seq'::regclass) NOT NULL,
    batch_number character varying(100) NOT NULL,
    provider_id bigint NOT NULL,
    total_claims integer DEFAULT 0,
    total_amount numeric(14,2) NOT NULL,
    status character varying(50),
    version bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    confirmed_at timestamp without time zone,
    paid_at timestamp without time zone,
    created_by character varying(255),
    confirmed_by character varying(255),
    paid_by character varying(255),
    CONSTRAINT settlement_batches_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT settlement_batches_total_amount_check CHECK ((total_amount > (0)::numeric))
);


ALTER TABLE public.settlement_batches OWNER TO postgres;

--
-- Name: settlement_payment_reference_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.settlement_payment_reference_seq
    START WITH 10001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.settlement_payment_reference_seq OWNER TO postgres;

--
-- Name: system_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.system_settings (
    id bigint NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value text,
    value_type character varying(20),
    description character varying(500),
    category character varying(50),
    is_editable boolean DEFAULT true,
    default_value text,
    validation_rules text,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(255)
);


ALTER TABLE public.system_settings OWNER TO postgres;

--
-- Name: system_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.system_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.system_settings_id_seq OWNER TO postgres;

--
-- Name: system_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.system_settings_id_seq OWNED BY public.system_settings.id;


--
-- Name: user_audit_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_audit_log (
    id bigint NOT NULL,
    user_id bigint,
    username character varying(255) DEFAULT 'SYSTEM'::character varying NOT NULL,
    action_type character varying(100) DEFAULT 'GENERIC'::character varying NOT NULL,
    action_description text,
    action character varying(100),
    details text,
    performed_by bigint,
    entity_type character varying(100),
    entity_id bigint,
    old_value text,
    new_value text,
    ip_address character varying(50),
    user_agent text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.user_audit_log OWNER TO postgres;

--
-- Name: user_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.user_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_audit_log_id_seq OWNER TO postgres;

--
-- Name: user_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.user_audit_log_id_seq OWNED BY public.user_audit_log.id;


--
-- Name: user_login_attempts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_login_attempts (
    id bigint NOT NULL,
    username character varying(255) NOT NULL,
    ip_address character varying(50),
    user_agent text,
    attempt_result character varying(20) DEFAULT 'SUCCESS'::character varying,
    failure_reason character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    user_id bigint,
    success boolean DEFAULT false,
    failed_reason character varying(255),
    attempted_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT user_login_attempts_attempt_result_check CHECK (((attempt_result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying, 'LOCKED'::character varying])::text[])))
);


ALTER TABLE public.user_login_attempts OWNER TO postgres;

--
-- Name: user_login_attempts_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.user_login_attempts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_login_attempts_id_seq OWNER TO postgres;

--
-- Name: user_login_attempts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.user_login_attempts_id_seq OWNED BY public.user_login_attempts.id;


--
-- Name: user_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.user_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_seq OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id bigint DEFAULT nextval('public.user_seq'::regclass) NOT NULL,
    username character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    user_type character varying(50) DEFAULT 'DATA_ENTRY'::character varying NOT NULL,
    employer_id bigint,
    provider_id bigint,
    company_id bigint,
    enabled boolean DEFAULT true,
    account_non_expired boolean DEFAULT true,
    account_non_locked boolean DEFAULT true,
    credentials_non_expired boolean DEFAULT true,
    is_active boolean DEFAULT true,
    email_verified boolean DEFAULT false,
    can_view_claims boolean DEFAULT true,
    can_view_visits boolean DEFAULT true,
    can_view_reports boolean DEFAULT true,
    can_view_members boolean DEFAULT true,
    can_view_benefit_policies boolean DEFAULT true,
    identity_verified boolean DEFAULT false,
    identity_verified_at timestamp without time zone,
    identity_verified_by character varying(255),
    phone character varying(50),
    profile_image_url character varying(500),
    password_changed_at timestamp without time zone,
    failed_login_count integer DEFAULT 0,
    locked_until timestamp without time zone,
    last_login_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_login timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT users_user_type_check CHECK (((user_type)::text = ANY ((ARRAY['SUPER_ADMIN'::character varying, 'EMPLOYER_ADMIN'::character varying, 'MEDICAL_REVIEWER'::character varying, 'PROVIDER_STAFF'::character varying, 'ACCOUNTANT'::character varying, 'FINANCE_VIEWER'::character varying, 'DATA_ENTRY'::character varying])::text[])))
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: visit_attachments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.visit_attachments (
    id bigint NOT NULL,
    visit_id bigint NOT NULL,
    file_name character varying(500) NOT NULL,
    original_file_name character varying(500),
    file_key character varying(500),
    file_type character varying(100),
    file_size bigint,
    attachment_type character varying(50),
    description text,
    uploaded_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT visit_attachments_attachment_type_check CHECK (((attachment_type)::text = ANY ((ARRAY['XRAY'::character varying, 'MRI'::character varying, 'CT_SCAN'::character varying, 'LAB_RESULT'::character varying, 'PRESCRIPTION'::character varying, 'MEDICAL_REPORT'::character varying, 'OTHER'::character varying])::text[])))
);


ALTER TABLE public.visit_attachments OWNER TO postgres;

--
-- Name: visit_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.visit_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.visit_attachments_id_seq OWNER TO postgres;

--
-- Name: visit_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.visit_attachments_id_seq OWNED BY public.visit_attachments.id;


--
-- Name: visits; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.visits (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    employer_id bigint,
    provider_id bigint,
    medical_category_id bigint,
    medical_category_name character varying(200),
    medical_service_id bigint,
    medical_service_code character varying(50),
    medical_service_name character varying(200),
    doctor_name character varying(255),
    specialty character varying(100),
    visit_date date NOT NULL,
    diagnosis text,
    treatment text,
    total_amount numeric(10,2),
    notes text,
    visit_type character varying(30) DEFAULT 'OUTPATIENT'::character varying,
    status character varying(30) DEFAULT 'REGISTERED'::character varying,
    eligibility_check_id bigint,
    version bigint DEFAULT 0,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_visit_date_reasonable CHECK (((visit_date <= CURRENT_DATE) AND (visit_date >= (CURRENT_DATE - '10 years'::interval)))),
    CONSTRAINT visits_status_check CHECK (((status)::text = ANY ((ARRAY['REGISTERED'::character varying, 'IN_PROGRESS'::character varying, 'PENDING_PREAUTH'::character varying, 'CLAIM_SUBMITTED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT visits_visit_type_check CHECK (((visit_type)::text = ANY ((ARRAY['EMERGENCY'::character varying, 'INPATIENT'::character varying, 'OUTPATIENT'::character varying, 'ROUTINE'::character varying, 'FOLLOW_UP'::character varying, 'PREVENTIVE'::character varying, 'SPECIALIZED'::character varying, 'HOME_CARE'::character varying, 'TELECONSULTATION'::character varying, 'DAY_SURGERY'::character varying, 'LEGACY_BACKLOG'::character varying])::text[])))
);


ALTER TABLE public.visits OWNER TO postgres;

--
-- Name: visits_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.visits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.visits_id_seq OWNER TO postgres;

--
-- Name: visits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.visits_id_seq OWNED BY public.visits.id;


--
-- Name: account_transactions id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_transactions ALTER COLUMN id SET DEFAULT nextval('public.account_transactions_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: benefit_policy_rules id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policy_rules ALTER COLUMN id SET DEFAULT nextval('public.benefit_policy_rules_id_seq'::regclass);


--
-- Name: claim_attachments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_attachments ALTER COLUMN id SET DEFAULT nextval('public.claim_attachments_id_seq'::regclass);


--
-- Name: claim_audit_logs id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.claim_audit_logs_id_seq'::regclass);


--
-- Name: claim_history id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_history ALTER COLUMN id SET DEFAULT nextval('public.claim_history_id_seq'::regclass);


--
-- Name: claim_lines id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_lines ALTER COLUMN id SET DEFAULT nextval('public.claim_lines_id_seq'::regclass);


--
-- Name: cpt_codes id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cpt_codes ALTER COLUMN id SET DEFAULT nextval('public.cpt_codes_id_seq'::regclass);


--
-- Name: eligibility_checks id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.eligibility_checks ALTER COLUMN id SET DEFAULT nextval('public.eligibility_checks_id_seq'::regclass);


--
-- Name: email_verification_tokens id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.email_verification_tokens ALTER COLUMN id SET DEFAULT nextval('public.email_verification_tokens_id_seq'::regclass);


--
-- Name: feature_flags id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.feature_flags ALTER COLUMN id SET DEFAULT nextval('public.feature_flags_id_seq'::regclass);


--
-- Name: icd_codes id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.icd_codes ALTER COLUMN id SET DEFAULT nextval('public.icd_codes_id_seq'::regclass);


--
-- Name: legacy_provider_contracts id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.legacy_provider_contracts ALTER COLUMN id SET DEFAULT nextval('public.legacy_provider_contracts_id_seq'::regclass);


--
-- Name: medical_reviewer_providers id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_reviewer_providers ALTER COLUMN id SET DEFAULT nextval('public.medical_reviewer_providers_id_seq'::regclass);


--
-- Name: medical_specialties id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_specialties ALTER COLUMN id SET DEFAULT nextval('public.medical_specialties_id_seq'::regclass);


--
-- Name: member_attributes id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_attributes ALTER COLUMN id SET DEFAULT nextval('public.member_attributes_id_seq'::regclass);


--
-- Name: member_deductibles id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_deductibles ALTER COLUMN id SET DEFAULT nextval('public.member_deductibles_id_seq'::regclass);


--
-- Name: member_import_errors id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_errors ALTER COLUMN id SET DEFAULT nextval('public.member_import_errors_id_seq'::regclass);


--
-- Name: member_import_logs id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_logs ALTER COLUMN id SET DEFAULT nextval('public.member_import_logs_id_seq'::regclass);


--
-- Name: member_policy_assignments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_policy_assignments ALTER COLUMN id SET DEFAULT nextval('public.member_policy_assignments_id_seq'::regclass);


--
-- Name: module_access id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.module_access ALTER COLUMN id SET DEFAULT nextval('public.module_access_id_seq'::regclass);


--
-- Name: network_providers id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.network_providers ALTER COLUMN id SET DEFAULT nextval('public.network_providers_id_seq'::regclass);


--
-- Name: password_reset_tokens id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.password_reset_tokens ALTER COLUMN id SET DEFAULT nextval('public.password_reset_tokens_id_seq'::regclass);


--
-- Name: pdf_company_settings id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pdf_company_settings ALTER COLUMN id SET DEFAULT nextval('public.pdf_company_settings_id_seq'::regclass);


--
-- Name: pre_authorization_attachments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorization_attachments ALTER COLUMN id SET DEFAULT nextval('public.pre_authorization_attachments_id_seq'::regclass);


--
-- Name: pre_authorization_audit id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorization_audit ALTER COLUMN id SET DEFAULT nextval('public.pre_authorization_audit_id_seq'::regclass);


--
-- Name: pre_authorizations id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorizations ALTER COLUMN id SET DEFAULT nextval('public.pre_authorizations_id_seq'::regclass);


--
-- Name: preauthorization_requests id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.preauthorization_requests ALTER COLUMN id SET DEFAULT nextval('public.preauthorization_requests_id_seq'::regclass);


--
-- Name: provider_accounts id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_accounts ALTER COLUMN id SET DEFAULT nextval('public.provider_accounts_id_seq'::regclass);


--
-- Name: provider_admin_documents id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_admin_documents ALTER COLUMN id SET DEFAULT nextval('public.provider_admin_documents_id_seq'::regclass);


--
-- Name: provider_allowed_employers id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_allowed_employers ALTER COLUMN id SET DEFAULT nextval('public.provider_allowed_employers_id_seq'::regclass);


--
-- Name: provider_contract_pricing_items id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contract_pricing_items ALTER COLUMN id SET DEFAULT nextval('public.provider_contract_pricing_items_id_seq'::regclass);


--
-- Name: provider_mapping_audit id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_mapping_audit ALTER COLUMN id SET DEFAULT nextval('public.provider_mapping_audit_id_seq'::regclass);


--
-- Name: provider_payments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments ALTER COLUMN id SET DEFAULT nextval('public.provider_payments_id_seq'::regclass);


--
-- Name: provider_raw_services id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_raw_services ALTER COLUMN id SET DEFAULT nextval('public.provider_raw_services_id_seq'::regclass);


--
-- Name: provider_service_mappings id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings ALTER COLUMN id SET DEFAULT nextval('public.provider_service_mappings_id_seq'::regclass);


--
-- Name: provider_service_price_import_logs id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_price_import_logs ALTER COLUMN id SET DEFAULT nextval('public.provider_service_price_import_logs_id_seq'::regclass);


--
-- Name: provider_services id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_services ALTER COLUMN id SET DEFAULT nextval('public.provider_services_id_seq'::regclass);


--
-- Name: settlement_batch_items id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batch_items ALTER COLUMN id SET DEFAULT nextval('public.settlement_batch_items_id_seq'::regclass);


--
-- Name: system_settings id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.system_settings ALTER COLUMN id SET DEFAULT nextval('public.system_settings_id_seq'::regclass);


--
-- Name: user_audit_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_audit_log ALTER COLUMN id SET DEFAULT nextval('public.user_audit_log_id_seq'::regclass);


--
-- Name: user_login_attempts id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_login_attempts ALTER COLUMN id SET DEFAULT nextval('public.user_login_attempts_id_seq'::regclass);


--
-- Name: visit_attachments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visit_attachments ALTER COLUMN id SET DEFAULT nextval('public.visit_attachments_id_seq'::regclass);


--
-- Name: visits id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visits ALTER COLUMN id SET DEFAULT nextval('public.visits_id_seq'::regclass);


--
-- Data for Name: account_transactions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.account_transactions (id, provider_account_id, transaction_type, amount, balance_before, balance_after, reference_type, reference_id, reference_number, description, transaction_date, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.audit_logs (id, "timestamp", user_id, username, action, entity_type, entity_id, details, ip_address, user_agent) FROM stdin;
\.


--
-- Data for Name: benefit_policies; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.benefit_policies (id, policy_name, policy_code, employer_id, name, annual_limit, per_visit_limit, deductible_amount, copay_percentage, annual_deductible, out_of_pocket_max, per_member_limit, per_family_limit, policy_type, description, notes, status, start_date, end_date, effective_date, expiry_date, default_coverage_percent, default_waiting_period_days, covered_members_count, active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: benefit_policy_rules; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.benefit_policy_rules (id, policy_id, service_category, medical_category_id, medical_service_id, coverage_percentage, coverage_percent, max_sessions_per_year, times_limit, requires_preauth, requires_pre_approval, waiting_period_days, max_amount_per_session, max_amount_per_year, amount_limit, notes, active, created_at, updated_at, created_by) FROM stdin;
\.


--
-- Data for Name: claim_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.claim_attachments (id, claim_id, file_name, file_path, created_at, file_url, original_file_name, file_key, file_type, file_size, attachment_type, uploaded_at, uploaded_by) FROM stdin;
\.


--
-- Data for Name: claim_audit_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.claim_audit_logs (id, claim_id, change_type, previous_status, new_status, previous_requested_amount, new_requested_amount, previous_approved_amount, new_approved_amount, actor_user_id, actor_username, actor_role, "timestamp", comment, ip_address, before_snapshot, after_snapshot) FROM stdin;
\.


--
-- Data for Name: claim_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.claim_history (id, claim_id, old_status, new_status, changed_at, changed_by, reason) FROM stdin;
\.


--
-- Data for Name: claim_lines; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.claim_lines (id, claim_id, service_code, service_description, quantity, unit_price, total_amount, total_price, medical_service_id, service_name, service_category_id, service_category_name, requires_pa, line_number, approved_amount, approved_units, approval_notes, coverage_percent_snapshot, patient_copay_percent_snapshot, times_limit_snapshot, amount_limit_snapshot, refused_amount, created_at, created_by, version, rejection_reason, rejection_reason_code, reviewer_notes, rejected, requested_unit_price, approved_unit_price, requested_quantity, approved_quantity) FROM stdin;
\.


--
-- Data for Name: claims; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.claims (id, claim_number, external_claim_ref, member_id, provider_id, provider_name, visit_id, service_date, diagnosis_code, diagnosis_description, requested_amount, approved_amount, paid_amount, patient_share, refused_amount, difference_amount, patient_copay, net_provider_amount, copay_percent, deductible_applied, status, submitted_at, reviewer_id, reviewed_at, approval_reason, reviewer_comment, doctor_name, pre_authorization_id, payment_reference, settled_at, settlement_notes, expected_completion_date, actual_completion_date, within_sla, business_days_taken, sla_days_configured, service_count, attachments_count, is_backlog, created_at, updated_at, created_by, updated_by, active, version) FROM stdin;
\.


--
-- Data for Name: cpt_codes; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.cpt_codes (id, code, description, category, sub_category, procedure_type, standard_price, max_allowed_price, min_allowed_price, covered, co_payment_percentage, requires_pre_auth, notes, active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: eligibility_checks; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.eligibility_checks (id, member_id, check_date, is_eligible, eligibility_reason, policy_id, coverage_status, visit_id, checked_by, request_id, check_timestamp, provider_id, service_date, service_code, eligible, status, reasons, member_name, member_civil_id, member_status, policy_number, policy_status, policy_start_date, policy_end_date, employer_id, employer_name, checked_by_user_id, checked_by_username, company_scope_id, ip_address, user_agent, processing_time_ms, rules_evaluated, created_at) FROM stdin;
\.


--
-- Data for Name: email_verification_tokens; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.email_verification_tokens (id, token, user_id, expiry_date, expires_at, verified, created_at) FROM stdin;
\.


--
-- Data for Name: employers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employers (id, code, name, address, phone, email, logo_url, website, business_type, tax_number, commercial_registration_number, active, is_default, created_at, updated_at, created_by, updated_by, cr_number, contract_start_date, contract_end_date, max_member_limit) FROM stdin;
\.


--
-- Data for Name: ent_service_aliases; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ent_service_aliases (id, medical_service_id, alias_text, locale, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: feature_flags; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.feature_flags (id, flag_key, flag_name, description, enabled, role_filters, created_by, updated_by, created_at, updated_at) FROM stdin;
1	PROVIDER_PORTAL_ENABLED	بوابة الخدمة المباشرة	تفعيل بوابة إدخال المطالبات المباشرة عبر مزودي الخدمة. عند التعطيل يعمل النظام في وضع الدفعات الشهرية فقط.	f	\N	SYSTEM	\N	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907
2	DIRECT_CLAIM_SUBMISSION_ENABLED	التقديم المباشر للمطالبات	السماح بإنشاء مطالبات فردية مباشرة من بوابة المزود. يتطلب تفعيل PROVIDER_PORTAL_ENABLED أيضاً.	f	\N	SYSTEM	\N	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907
3	BATCH_CLAIMS_ENABLED	نظام الدفعات الشهرية	تفعيل إدخال المطالبات عبر الدفعات الشهرية. هذا هو المسار الأساسي الحالي لإدخال المطالبات.	t	\N	SYSTEM	\N	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	001	sequences	SQL	V001__sequences.sql	422857321	postgres	2026-03-07 21:50:50.341838	11	t
2	005	schema employers	SQL	V005__schema_employers.sql	-1582502973	postgres	2026-03-07 21:50:50.383871	24	t
3	006	schema providers	SQL	V006__schema_providers.sql	-868603061	postgres	2026-03-07 21:50:50.419624	46	t
4	010	schema users	SQL	V010__schema_users.sql	583524572	postgres	2026-03-07 21:50:50.478785	29	t
5	011	schema auth tokens	SQL	V011__schema_auth_tokens.sql	1442819301	postgres	2026-03-07 21:50:50.516099	33	t
6	012	schema login audit	SQL	V012__schema_login_audit.sql	532733667	postgres	2026-03-07 21:50:50.555657	55	t
7	015	schema system config	SQL	V015__schema_system_config.sql	544798756	postgres	2026-03-07 21:50:50.621657	53	t
8	020	schema medical categories	SQL	V020__schema_medical_categories.sql	1688003485	postgres	2026-03-07 21:50:50.685576	19	t
9	021	schema medical services	SQL	V021__schema_medical_services.sql	-507708258	postgres	2026-03-07 21:50:50.711175	45	t
10	022	schema medical specialties	SQL	V022__schema_medical_specialties.sql	-1828102982	postgres	2026-03-07 21:50:50.763271	7	t
11	023	schema medical codes	SQL	V023__schema_medical_codes.sql	1526189267	postgres	2026-03-07 21:50:50.775427	21	t
12	030	schema provider services	SQL	V030__schema_provider_services.sql	-1951623012	postgres	2026-03-07 21:50:50.802303	36	t
13	031	schema provider mapping	SQL	V031__schema_provider_mapping.sql	930657646	postgres	2026-03-07 21:50:50.846809	27	t
14	040	schema benefit policies	SQL	V040__schema_benefit_policies.sql	-1088735389	postgres	2026-03-07 21:50:50.881065	37	t
15	045	schema provider contracts	SQL	V045__schema_provider_contracts.sql	406907110	postgres	2026-03-07 21:50:50.923879	51	t
16	050	schema members	SQL	V050__schema_members.sql	454344578	postgres	2026-03-07 21:50:50.981331	71	t
17	051	schema member import	SQL	V051__schema_member_import.sql	1869405255	postgres	2026-03-07 21:50:51.069781	27	t
18	060	schema visits	SQL	V060__schema_visits.sql	-1635864118	postgres	2026-03-07 21:50:51.10109	44	t
19	061	schema eligibility checks	SQL	V061__schema_eligibility_checks.sql	114896004	postgres	2026-03-07 21:50:51.151837	30	t
20	065	schema pre authorization	SQL	V065__schema_pre_authorization.sql	397108841	postgres	2026-03-07 21:50:51.190805	53	t
21	070	schema claims	SQL	V070__schema_claims.sql	181329393	postgres	2026-03-07 21:50:51.250572	36	t
22	071	schema claim lines	SQL	V071__schema_claim_lines.sql	-1401062828	postgres	2026-03-07 21:50:51.293535	48	t
23	080	schema financial	SQL	V080__schema_financial.sql	-1989012658	postgres	2026-03-07 21:50:51.347066	47	t
24	081	schema settlement	SQL	V081__schema_settlement.sql	1910470033	postgres	2026-03-07 21:50:51.399558	45	t
25	090	indexes	SQL	V090__indexes.sql	-292980596	postgres	2026-03-07 21:50:51.452804	45	t
26	095	seed feature flags	SQL	V095__seed_feature_flags.sql	459012115	postgres	2026-03-07 21:50:51.50252	5	t
27	096	add missing columns	SQL	V096__add_missing_columns.sql	-77707459	postgres	2026-03-07 21:50:51.513778	2	t
28	097	claim lines missing columns	SQL	V097__claim_lines_missing_columns.sql	133045648	postgres	2026-03-07 21:50:51.520022	2	t
29	098	employer financial contract fields	SQL	V098__employer_financial_contract_fields.sql	-1812757281	postgres	2026-03-07 21:50:51.530697	2	t
\.


--
-- Data for Name: icd_codes; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.icd_codes (id, code, description, category, sub_category, version, notes, active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: legacy_provider_contracts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.legacy_provider_contracts (id, provider_id, service_code, contract_price, currency, effective_from, effective_to, active, notes, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: medical_categories; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_categories (id, category_name, category_name_ar, category_code, code, name, name_ar, name_en, parent_id, context, description, deleted, deleted_at, deleted_by, active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: medical_reviewer_providers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_reviewer_providers (id, reviewer_id, provider_id, active, created_at, created_by, updated_at, updated_by) FROM stdin;
\.


--
-- Data for Name: medical_service_categories; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_service_categories (id, service_id, category_id, context, is_primary, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: medical_services; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_services (id, category_id, service_name, service_name_ar, service_code, name, name_ar, name_en, code, cost, is_master, requires_pa, deleted, deleted_at, deleted_by, description, active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: medical_specialties; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_specialties (id, code, name_ar, name_en, deleted) FROM stdin;
\.


--
-- Data for Name: member_attributes; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.member_attributes (id, member_id, attribute_code, attribute_value, source, source_reference, created_by, updated_by, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: member_deductibles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.member_deductibles (id, member_id, deductible_year, total_deductible, deductible_used, deductible_remaining, version, updated_at, updated_by) FROM stdin;
\.


--
-- Data for Name: member_import_errors; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.member_import_errors (id, import_log_id, row_number, row_data, error_type, error_field, error_message, created_at) FROM stdin;
\.


--
-- Data for Name: member_import_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.member_import_logs (id, import_batch_id, file_name, file_size_bytes, total_rows, created_count, updated_count, skipped_count, error_count, status, error_message, started_at, completed_at, processing_time_ms, imported_by_user_id, imported_by_username, company_scope_id, ip_address, created_at) FROM stdin;
\.


--
-- Data for Name: member_policy_assignments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.member_policy_assignments (id, member_id, policy_id, assignment_start_date, assignment_end_date, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: members; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.members (id, member_card_id, full_name, full_name_ar, date_of_birth, gender, national_id, employer_id, employee_id, employee_number, membership_type, relation_to_employee, relationship, parent_id, email, phone, address, coverage_start_date, coverage_end_date, policy_number, start_date, end_date, join_date, benefit_policy_id, barcode, birth_date, card_number, card_status, card_activated_at, is_smart_card, civil_id, national_number, photo_url, profile_photo_path, marital_status, nationality, occupation, notes, emergency_notes, is_vip, is_urgent, blocked_reason, status, eligibility_status, eligibility_updated_at, version, active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: module_access; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.module_access (id, module_name, module_key, description, allowed_roles, required_permissions, feature_flag_key, active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: network_providers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.network_providers (id, employer_id, provider_id, network_tier, effective_date, expiry_date, active, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: password_reset_tokens; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.password_reset_tokens (id, token, user_id, expiry_date, expires_at, used, created_at) FROM stdin;
\.


--
-- Data for Name: pdf_company_settings; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pdf_company_settings (id, company_name, logo_url, logo_data, address, phone, email, website, footer_text, footer_text_en, header_color, footer_color, page_size, margin_top, margin_bottom, margin_left, margin_right, is_active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: pre_authorization_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pre_authorization_attachments (id, preauthorization_request_id, file_name, file_path, file_type, file_size, attachment_type, uploaded_by, uploaded_at) FROM stdin;
\.


--
-- Data for Name: pre_authorization_audit; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pre_authorization_audit (id, pre_authorization_id, reference_number, changed_by, change_date, action, field_name, old_value, new_value, notes, ip_address) FROM stdin;
\.


--
-- Data for Name: pre_authorizations; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pre_authorizations (id, active, approved_amount, approved_at, approved_by, contract_price, copay_amount, copay_percentage, coverage_percent_snapshot, created_at, created_by, currency, diagnosis_code, diagnosis_description, expected_service_date, expiry_date, insurance_covered_amount, medical_service_id, member_id, notes, patient_copay_percent_snapshot, pre_auth_number, priority, provider_id, reference_number, rejection_reason, request_date, requires_pa, reserved_amount, service_category_id, service_category_name, service_code, service_name, service_type, status, updated_at, updated_by, version, visit_id) FROM stdin;
\.


--
-- Data for Name: preauthorization_requests; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.preauthorization_requests (id, request_number, provider_id, member_id, service_date, requested_service_date, diagnosis_code, diagnosis_description, requested_amount, approved_amount, status, valid_from, valid_until, created_at, updated_at, approved_at, created_by, approved_by) FROM stdin;
\.


--
-- Data for Name: provider_accounts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_accounts (id, provider_id, account_type, currency, current_balance, total_payable, version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: provider_admin_documents; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_admin_documents (id, provider_id, document_name, document_type, file_path, file_size, uploaded_at, uploaded_by) FROM stdin;
\.


--
-- Data for Name: provider_allowed_employers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_allowed_employers (id, provider_id, employer_id, created_at, created_by) FROM stdin;
\.


--
-- Data for Name: provider_contract_pricing_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_contract_pricing_items (id, contract_id, medical_service_id, service_category, unit_price, effective_from, effective_to, active, created_at, updated_at, created_by) FROM stdin;
\.


--
-- Data for Name: provider_contracts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_contracts (id, provider_id, employer_id, contract_number, contract_start_date, contract_end_date, discount_percent, payment_terms, max_sessions_per_service, requires_preauthorization, contract_status, active, status, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: provider_mapping_audit; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_mapping_audit (id, provider_raw_service_id, action, old_value, new_value, performed_by, performed_at) FROM stdin;
\.


--
-- Data for Name: provider_payments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_payments (id, settlement_batch_id, provider_id, amount, payment_reference, payment_method, payment_date, notes, created_by, created_at) FROM stdin;
\.


--
-- Data for Name: provider_raw_services; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_raw_services (id, provider_id, raw_name, normalized_name, code, encounter_type, source, import_batch_id, status, confidence_score, created_at) FROM stdin;
\.


--
-- Data for Name: provider_service_mappings; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_service_mappings (id, provider_raw_service_id, medical_service_id, mapping_status, mapped_by, mapped_at, confidence_score) FROM stdin;
\.


--
-- Data for Name: provider_service_price_import_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_service_price_import_logs (id, import_batch_id, provider_id, provider_code, provider_name, file_name, file_size_bytes, import_mode, total_rows, success_count, error_count, skipped_count, status, error_details, started_at, completed_at, processing_time_ms, imported_by_user_id, imported_by_username, created_at) FROM stdin;
\.


--
-- Data for Name: provider_services; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.provider_services (id, provider_id, service_code, active, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: providers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.providers (id, provider_name, provider_name_ar, license_number, provider_type, contact_person, contact_email, contact_phone, address, city, region, bank_name, bank_account_number, iban, allow_all_employers, tax_company_code, principal_name, principal_phone, principal_email, principal_mobile, principal_address, secondary_contact, secondary_contact_phone, secondary_contact_email, accounting_person, accounting_phone, accounting_email, provider_status, active, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: settlement_batch_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.settlement_batch_items (id, batch_id, claim_id, claim_amount, added_at, added_by) FROM stdin;
\.


--
-- Data for Name: settlement_batches; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.settlement_batches (id, batch_number, provider_id, total_claims, total_amount, status, version, created_at, updated_at, confirmed_at, paid_at, created_by, confirmed_by, paid_by) FROM stdin;
\.


--
-- Data for Name: system_settings; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.system_settings (id, setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at, updated_by) FROM stdin;
1	LOGO_URL		STRING	رابط شعار النظام. اتركه فارغاً للشعار الافتراضي.	UI	t		\N	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
2	FONT_FAMILY	Tajawal	STRING	نوع الخط الأساسي للنظام.	UI	t	Tajawal	allowed:Tajawal,Cairo,Almarai,Noto Naskh Arabic	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
3	FONT_SIZE_BASE	14	INTEGER	حجم الخط الأساسي بالبكسل.	UI	t	14	min:12,max:18	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
4	SYSTEM_NAME_AR	نظام واعد الطبي	STRING	اسم النظام باللغة العربية — يظهر في العنوان والتقارير.	UI	t	نظام واعد الطبي	maxlength:60	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
5	SYSTEM_NAME_EN	TBA WAAD System	STRING	System name in English — appears in reports and API responses.	UI	t	TBA WAAD System	maxlength:60	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
6	BENEFICIARY_NUMBER_FORMAT	PREFIX_SEQUENCE	STRING	صيغة ترقيم المستفيدين: PREFIX_SEQUENCE | YEAR_SEQUENCE | SEQUENTIAL.	MEMBERS	t	PREFIX_SEQUENCE	allowed:PREFIX_SEQUENCE,YEAR_SEQUENCE,SEQUENTIAL	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
7	BENEFICIARY_NUMBER_PREFIX	MEM	STRING	البادئة في رقم المستفيد (مع PREFIX_SEQUENCE).	MEMBERS	t	MEM	maxlength:10	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
8	BENEFICIARY_NUMBER_DIGITS	6	INTEGER	عدد أرقام الجزء التسلسلي في رقم المستفيد.	MEMBERS	t	6	min:4,max:10	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
9	ELIGIBILITY_STRICT_MODE	false	BOOLEAN	الوضع الصارم: رفض تلقائي لأي طلب خارج نطاق التغطية.	ELIGIBILITY	t	false	\N	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
10	WAITING_PERIOD_DAYS_DEFAULT	30	INTEGER	فترة الانتظار الافتراضية بالأيام عند إضافة مستفيد لوثيقة.	ELIGIBILITY	t	30	min:0,max:365	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
11	ELIGIBILITY_GRACE_PERIOD_DAYS	7	INTEGER	فترة السماح بالأيام بعد انتهاء صلاحية الوثيقة.	ELIGIBILITY	t	7	min:0,max:30	t	2026-03-07 21:50:51.505907	2026-03-07 21:50:51.505907	\N
\.


--
-- Data for Name: user_audit_log; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_audit_log (id, user_id, username, action_type, action_description, action, details, performed_by, entity_type, entity_id, old_value, new_value, ip_address, user_agent, created_at) FROM stdin;
\.


--
-- Data for Name: user_login_attempts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_login_attempts (id, username, ip_address, user_agent, attempt_result, failure_reason, created_at, user_id, success, failed_reason, attempted_at) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, username, email, password, full_name, user_type, employer_id, provider_id, company_id, enabled, account_non_expired, account_non_locked, credentials_non_expired, is_active, email_verified, can_view_claims, can_view_visits, can_view_reports, can_view_members, can_view_benefit_policies, identity_verified, identity_verified_at, identity_verified_by, phone, profile_image_url, password_changed_at, failed_login_count, locked_until, last_login_at, created_at, updated_at, last_login, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: visit_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.visit_attachments (id, visit_id, file_name, original_file_name, file_key, file_type, file_size, attachment_type, description, uploaded_by, created_at) FROM stdin;
\.


--
-- Data for Name: visits; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.visits (id, member_id, employer_id, provider_id, medical_category_id, medical_category_name, medical_service_id, medical_service_code, medical_service_name, doctor_name, specialty, visit_date, diagnosis, treatment, total_amount, notes, visit_type, status, eligibility_check_id, version, active, created_at, updated_at) FROM stdin;
\.


--
-- Name: account_transactions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.account_transactions_id_seq', 1, false);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.audit_logs_id_seq', 1, false);


--
-- Name: benefit_policy_rules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.benefit_policy_rules_id_seq', 1, false);


--
-- Name: benefit_policy_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.benefit_policy_seq', 1, false);


--
-- Name: claim_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_attachments_id_seq', 1, false);


--
-- Name: claim_audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_audit_logs_id_seq', 1, false);


--
-- Name: claim_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_history_id_seq', 1, false);


--
-- Name: claim_lines_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_lines_id_seq', 1, false);


--
-- Name: claim_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_seq', 1, false);


--
-- Name: cpt_codes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.cpt_codes_id_seq', 1, false);


--
-- Name: eligibility_checks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.eligibility_checks_id_seq', 1, false);


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.email_verification_tokens_id_seq', 1, false);


--
-- Name: employer_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employer_seq', 1, false);


--
-- Name: ent_service_alias_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ent_service_alias_seq', 1, false);


--
-- Name: feature_flags_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.feature_flags_id_seq', 3, true);


--
-- Name: icd_codes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.icd_codes_id_seq', 1, false);


--
-- Name: legacy_provider_contracts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.legacy_provider_contracts_id_seq', 1, false);


--
-- Name: medical_category_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_category_seq', 1, false);


--
-- Name: medical_reviewer_providers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_reviewer_providers_id_seq', 1, false);


--
-- Name: medical_service_category_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_service_category_seq', 1, false);


--
-- Name: medical_service_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_service_seq', 1, false);


--
-- Name: medical_specialties_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_specialties_id_seq', 1, false);


--
-- Name: member_attributes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_attributes_id_seq', 1, false);


--
-- Name: member_deductibles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_deductibles_id_seq', 1, false);


--
-- Name: member_import_errors_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_import_errors_id_seq', 1, false);


--
-- Name: member_import_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_import_logs_id_seq', 1, false);


--
-- Name: member_policy_assignments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_policy_assignments_id_seq', 1, false);


--
-- Name: member_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_seq', 1, false);


--
-- Name: module_access_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.module_access_id_seq', 1, false);


--
-- Name: network_providers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.network_providers_id_seq', 1, false);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.password_reset_tokens_id_seq', 1, false);


--
-- Name: pdf_company_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pdf_company_settings_id_seq', 1, false);


--
-- Name: pre_authorization_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorization_attachments_id_seq', 1, false);


--
-- Name: pre_authorization_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorization_audit_id_seq', 1, false);


--
-- Name: pre_authorizations_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorizations_id_seq', 1, false);


--
-- Name: preauth_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.preauth_seq', 1, false);


--
-- Name: preauthorization_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.preauthorization_requests_id_seq', 1, false);


--
-- Name: provider_accounts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_accounts_id_seq', 1, false);


--
-- Name: provider_admin_documents_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_admin_documents_id_seq', 1, false);


--
-- Name: provider_allowed_employers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_allowed_employers_id_seq', 1, false);


--
-- Name: provider_contract_pricing_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_contract_pricing_items_id_seq', 1, false);


--
-- Name: provider_contract_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_contract_seq', 1, false);


--
-- Name: provider_mapping_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_mapping_audit_id_seq', 1, false);


--
-- Name: provider_payments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_payments_id_seq', 1, false);


--
-- Name: provider_raw_services_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_raw_services_id_seq', 1, false);


--
-- Name: provider_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_seq', 1, false);


--
-- Name: provider_service_mappings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_service_mappings_id_seq', 1, false);


--
-- Name: provider_service_price_import_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_service_price_import_logs_id_seq', 1, false);


--
-- Name: provider_services_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_services_id_seq', 1, false);


--
-- Name: settlement_batch_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_batch_items_id_seq', 1, false);


--
-- Name: settlement_batch_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_batch_seq', 1, false);


--
-- Name: settlement_payment_reference_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_payment_reference_seq', 10001, false);


--
-- Name: system_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.system_settings_id_seq', 11, true);


--
-- Name: user_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_audit_log_id_seq', 1, false);


--
-- Name: user_login_attempts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_login_attempts_id_seq', 1, false);


--
-- Name: user_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_seq', 1, false);


--
-- Name: visit_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.visit_attachments_id_seq', 1, false);


--
-- Name: visits_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.visits_id_seq', 1, false);


--
-- Name: account_transactions account_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_transactions
    ADD CONSTRAINT account_transactions_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: benefit_policies benefit_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policies
    ADD CONSTRAINT benefit_policies_pkey PRIMARY KEY (id);


--
-- Name: benefit_policies benefit_policies_policy_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policies
    ADD CONSTRAINT benefit_policies_policy_code_key UNIQUE (policy_code);


--
-- Name: benefit_policy_rules benefit_policy_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policy_rules
    ADD CONSTRAINT benefit_policy_rules_pkey PRIMARY KEY (id);


--
-- Name: claim_attachments claim_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_attachments
    ADD CONSTRAINT claim_attachments_pkey PRIMARY KEY (id);


--
-- Name: claim_audit_logs claim_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_audit_logs
    ADD CONSTRAINT claim_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: claim_history claim_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_history
    ADD CONSTRAINT claim_history_pkey PRIMARY KEY (id);


--
-- Name: claim_lines claim_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_lines
    ADD CONSTRAINT claim_lines_pkey PRIMARY KEY (id);


--
-- Name: claims claims_claim_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT claims_claim_number_key UNIQUE (claim_number);


--
-- Name: claims claims_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT claims_pkey PRIMARY KEY (id);


--
-- Name: cpt_codes cpt_codes_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cpt_codes
    ADD CONSTRAINT cpt_codes_code_key UNIQUE (code);


--
-- Name: cpt_codes cpt_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cpt_codes
    ADD CONSTRAINT cpt_codes_pkey PRIMARY KEY (id);


--
-- Name: eligibility_checks eligibility_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.eligibility_checks
    ADD CONSTRAINT eligibility_checks_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);


--
-- Name: email_verification_tokens email_verification_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_token_key UNIQUE (token);


--
-- Name: employers employers_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employers
    ADD CONSTRAINT employers_code_key UNIQUE (code);


--
-- Name: employers employers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employers
    ADD CONSTRAINT employers_pkey PRIMARY KEY (id);


--
-- Name: ent_service_aliases ent_service_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ent_service_aliases
    ADD CONSTRAINT ent_service_aliases_pkey PRIMARY KEY (id);


--
-- Name: feature_flags feature_flags_flag_key_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_flag_key_key UNIQUE (flag_key);


--
-- Name: feature_flags feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: icd_codes icd_codes_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.icd_codes
    ADD CONSTRAINT icd_codes_code_key UNIQUE (code);


--
-- Name: icd_codes icd_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.icd_codes
    ADD CONSTRAINT icd_codes_pkey PRIMARY KEY (id);


--
-- Name: legacy_provider_contracts legacy_provider_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.legacy_provider_contracts
    ADD CONSTRAINT legacy_provider_contracts_pkey PRIMARY KEY (id);


--
-- Name: medical_categories medical_categories_category_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_categories
    ADD CONSTRAINT medical_categories_category_code_key UNIQUE (category_code);


--
-- Name: medical_categories medical_categories_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_categories
    ADD CONSTRAINT medical_categories_code_key UNIQUE (code);


--
-- Name: medical_categories medical_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_categories
    ADD CONSTRAINT medical_categories_pkey PRIMARY KEY (id);


--
-- Name: medical_reviewer_providers medical_reviewer_providers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_reviewer_providers
    ADD CONSTRAINT medical_reviewer_providers_pkey PRIMARY KEY (id);


--
-- Name: medical_service_categories medical_service_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_service_categories
    ADD CONSTRAINT medical_service_categories_pkey PRIMARY KEY (id);


--
-- Name: medical_services medical_services_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_services
    ADD CONSTRAINT medical_services_pkey PRIMARY KEY (id);


--
-- Name: medical_services medical_services_service_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_services
    ADD CONSTRAINT medical_services_service_code_key UNIQUE (service_code);


--
-- Name: medical_specialties medical_specialties_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_specialties
    ADD CONSTRAINT medical_specialties_code_key UNIQUE (code);


--
-- Name: medical_specialties medical_specialties_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_specialties
    ADD CONSTRAINT medical_specialties_pkey PRIMARY KEY (id);


--
-- Name: member_attributes member_attributes_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_attributes
    ADD CONSTRAINT member_attributes_pkey PRIMARY KEY (id);


--
-- Name: member_deductibles member_deductibles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_deductibles
    ADD CONSTRAINT member_deductibles_pkey PRIMARY KEY (id);


--
-- Name: member_import_errors member_import_errors_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_errors
    ADD CONSTRAINT member_import_errors_pkey PRIMARY KEY (id);


--
-- Name: member_import_logs member_import_logs_import_batch_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_logs
    ADD CONSTRAINT member_import_logs_import_batch_id_key UNIQUE (import_batch_id);


--
-- Name: member_import_logs member_import_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_logs
    ADD CONSTRAINT member_import_logs_pkey PRIMARY KEY (id);


--
-- Name: member_policy_assignments member_policy_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_policy_assignments
    ADD CONSTRAINT member_policy_assignments_pkey PRIMARY KEY (id);


--
-- Name: members members_member_card_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT members_member_card_id_key UNIQUE (member_card_id);


--
-- Name: members members_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT members_pkey PRIMARY KEY (id);


--
-- Name: module_access module_access_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.module_access
    ADD CONSTRAINT module_access_pkey PRIMARY KEY (id);


--
-- Name: network_providers network_providers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.network_providers
    ADD CONSTRAINT network_providers_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_key UNIQUE (token);


--
-- Name: pdf_company_settings pdf_company_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pdf_company_settings
    ADD CONSTRAINT pdf_company_settings_pkey PRIMARY KEY (id);


--
-- Name: pre_authorization_attachments pre_authorization_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorization_attachments
    ADD CONSTRAINT pre_authorization_attachments_pkey PRIMARY KEY (id);


--
-- Name: pre_authorization_audit pre_authorization_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorization_audit
    ADD CONSTRAINT pre_authorization_audit_pkey PRIMARY KEY (id);


--
-- Name: pre_authorizations pre_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorizations
    ADD CONSTRAINT pre_authorizations_pkey PRIMARY KEY (id);


--
-- Name: preauthorization_requests preauthorization_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.preauthorization_requests
    ADD CONSTRAINT preauthorization_requests_pkey PRIMARY KEY (id);


--
-- Name: provider_accounts provider_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_accounts
    ADD CONSTRAINT provider_accounts_pkey PRIMARY KEY (id);


--
-- Name: provider_admin_documents provider_admin_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_admin_documents
    ADD CONSTRAINT provider_admin_documents_pkey PRIMARY KEY (id);


--
-- Name: provider_allowed_employers provider_allowed_employers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_allowed_employers
    ADD CONSTRAINT provider_allowed_employers_pkey PRIMARY KEY (id);


--
-- Name: provider_contract_pricing_items provider_contract_pricing_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contract_pricing_items
    ADD CONSTRAINT provider_contract_pricing_items_pkey PRIMARY KEY (id);


--
-- Name: provider_contracts provider_contracts_contract_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contracts
    ADD CONSTRAINT provider_contracts_contract_number_key UNIQUE (contract_number);


--
-- Name: provider_contracts provider_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contracts
    ADD CONSTRAINT provider_contracts_pkey PRIMARY KEY (id);


--
-- Name: provider_mapping_audit provider_mapping_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_mapping_audit
    ADD CONSTRAINT provider_mapping_audit_pkey PRIMARY KEY (id);


--
-- Name: provider_payments provider_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT provider_payments_pkey PRIMARY KEY (id);


--
-- Name: provider_raw_services provider_raw_services_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_raw_services
    ADD CONSTRAINT provider_raw_services_pkey PRIMARY KEY (id);


--
-- Name: provider_service_mappings provider_service_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings
    ADD CONSTRAINT provider_service_mappings_pkey PRIMARY KEY (id);


--
-- Name: provider_service_price_import_logs provider_service_price_import_logs_import_batch_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_price_import_logs
    ADD CONSTRAINT provider_service_price_import_logs_import_batch_id_key UNIQUE (import_batch_id);


--
-- Name: provider_service_price_import_logs provider_service_price_import_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_price_import_logs
    ADD CONSTRAINT provider_service_price_import_logs_pkey PRIMARY KEY (id);


--
-- Name: provider_services provider_services_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_services
    ADD CONSTRAINT provider_services_pkey PRIMARY KEY (id);


--
-- Name: providers providers_contact_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.providers
    ADD CONSTRAINT providers_contact_email_key UNIQUE (contact_email);


--
-- Name: providers providers_license_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.providers
    ADD CONSTRAINT providers_license_number_key UNIQUE (license_number);


--
-- Name: providers providers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.providers
    ADD CONSTRAINT providers_pkey PRIMARY KEY (id);


--
-- Name: settlement_batch_items settlement_batch_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batch_items
    ADD CONSTRAINT settlement_batch_items_pkey PRIMARY KEY (id);


--
-- Name: settlement_batches settlement_batches_batch_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batches
    ADD CONSTRAINT settlement_batches_batch_number_key UNIQUE (batch_number);


--
-- Name: settlement_batches settlement_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batches
    ADD CONSTRAINT settlement_batches_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_setting_key_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_setting_key_key UNIQUE (setting_key);


--
-- Name: eligibility_checks uk_eligibility_request_id; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.eligibility_checks
    ADD CONSTRAINT uk_eligibility_request_id UNIQUE (request_id);


--
-- Name: member_attributes uk_member_attribute_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_attributes
    ADD CONSTRAINT uk_member_attribute_code UNIQUE (member_id, attribute_code);


--
-- Name: medical_reviewer_providers uk_reviewer_provider; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_reviewer_providers
    ADD CONSTRAINT uk_reviewer_provider UNIQUE (reviewer_id, provider_id);


--
-- Name: provider_services unique_provider_service; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_services
    ADD CONSTRAINT unique_provider_service UNIQUE (provider_id, service_code);


--
-- Name: ent_service_aliases uq_alias_text_per_service_locale; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ent_service_aliases
    ADD CONSTRAINT uq_alias_text_per_service_locale UNIQUE (medical_service_id, alias_text, locale);


--
-- Name: settlement_batch_items uq_batch_item_claim; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batch_items
    ADD CONSTRAINT uq_batch_item_claim UNIQUE (claim_id);


--
-- Name: member_deductibles uq_member_deductible_year; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_deductibles
    ADD CONSTRAINT uq_member_deductible_year UNIQUE (member_id, deductible_year);


--
-- Name: medical_service_categories uq_msc_primary_per_context; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_service_categories
    ADD CONSTRAINT uq_msc_primary_per_context UNIQUE (service_id, context, is_primary) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: provider_payments uq_payments_batch; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT uq_payments_batch UNIQUE (settlement_batch_id);


--
-- Name: provider_payments uq_payments_reference; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT uq_payments_reference UNIQUE (payment_reference);


--
-- Name: provider_allowed_employers uq_provider_employer; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_allowed_employers
    ADD CONSTRAINT uq_provider_employer UNIQUE (provider_id, employer_id);


--
-- Name: provider_raw_services uq_prs_provider_name; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_raw_services
    ADD CONSTRAINT uq_prs_provider_name UNIQUE (provider_id, raw_name);


--
-- Name: provider_service_mappings uq_psm_raw_service; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings
    ADD CONSTRAINT uq_psm_raw_service UNIQUE (provider_raw_service_id);


--
-- Name: user_audit_log user_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_audit_log
    ADD CONSTRAINT user_audit_log_pkey PRIMARY KEY (id);


--
-- Name: user_login_attempts user_login_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_login_attempts
    ADD CONSTRAINT user_login_attempts_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: visit_attachments visit_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visit_attachments
    ADD CONSTRAINT visit_attachments_pkey PRIMARY KEY (id);


--
-- Name: visits visits_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT visits_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_account_transactions_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_account_transactions_provider_date ON public.account_transactions USING btree (provider_account_id, transaction_date);


--
-- Name: idx_aliases_locale; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_aliases_locale ON public.ent_service_aliases USING btree (locale);


--
-- Name: idx_aliases_service_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_aliases_service_id ON public.ent_service_aliases USING btree (medical_service_id);


--
-- Name: idx_aliases_text; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_aliases_text ON public.ent_service_aliases USING btree (alias_text);


--
-- Name: idx_allowed_employers_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_allowed_employers_employer ON public.provider_allowed_employers USING btree (employer_id);


--
-- Name: idx_allowed_employers_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_allowed_employers_provider ON public.provider_allowed_employers USING btree (provider_id);


--
-- Name: idx_audit_action_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_action_type ON public.user_audit_log USING btree (action_type);


--
-- Name: idx_audit_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_created ON public.user_audit_log USING btree (created_at DESC);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_entity ON public.user_audit_log USING btree (entity_type, entity_id);


--
-- Name: idx_audit_logs_entity; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_logs_entity ON public.audit_logs USING btree (entity_type, entity_id);


--
-- Name: idx_audit_logs_timestamp; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_logs_timestamp ON public.audit_logs USING btree ("timestamp" DESC);


--
-- Name: idx_audit_logs_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_logs_user ON public.audit_logs USING btree (user_id);


--
-- Name: idx_audit_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_user ON public.user_audit_log USING btree (user_id);


--
-- Name: idx_audit_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_username ON public.user_audit_log USING btree (username);


--
-- Name: idx_batch_items_batch; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batch_items_batch ON public.settlement_batch_items USING btree (batch_id);


--
-- Name: idx_batch_items_claim; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batch_items_claim ON public.settlement_batch_items USING btree (claim_id);


--
-- Name: idx_batches_payment_summary; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batches_payment_summary ON public.settlement_batches USING btree (status, paid_at, total_amount);


--
-- Name: idx_batches_pending; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batches_pending ON public.settlement_batches USING btree (provider_id, status) WHERE ((status)::text = 'DRAFT'::text);


--
-- Name: idx_batches_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batches_provider ON public.settlement_batches USING btree (provider_id);


--
-- Name: idx_batches_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batches_provider_date ON public.settlement_batches USING btree (provider_id, created_at DESC, status);


--
-- Name: idx_batches_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_batches_status ON public.settlement_batches USING btree (status);


--
-- Name: idx_bpr_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bpr_active ON public.benefit_policy_rules USING btree (active);


--
-- Name: idx_bpr_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bpr_category ON public.benefit_policy_rules USING btree (medical_category_id);


--
-- Name: idx_bpr_policy; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bpr_policy ON public.benefit_policy_rules USING btree (policy_id);


--
-- Name: idx_bpr_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bpr_service ON public.benefit_policy_rules USING btree (medical_service_id);


--
-- Name: idx_claim_attachments_claim; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_attachments_claim ON public.claim_attachments USING btree (claim_id);


--
-- Name: idx_claim_attachments_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_attachments_date ON public.claim_attachments USING btree (claim_id, created_at DESC);


--
-- Name: idx_claim_attachments_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_attachments_type ON public.claim_attachments USING btree (attachment_type);


--
-- Name: idx_claim_attachments_type_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_attachments_type_date ON public.claim_attachments USING btree (attachment_type, created_at DESC);


--
-- Name: idx_claim_audit_actor; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_audit_actor ON public.claim_audit_logs USING btree (actor_user_id);


--
-- Name: idx_claim_audit_claim_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_audit_claim_id ON public.claim_audit_logs USING btree (claim_id);


--
-- Name: idx_claim_audit_timestamp; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_audit_timestamp ON public.claim_audit_logs USING btree ("timestamp" DESC);


--
-- Name: idx_claim_history_timeline; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_history_timeline ON public.claim_history USING btree (claim_id, changed_at DESC, new_status);


--
-- Name: idx_claim_line_claim; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_line_claim ON public.claim_lines USING btree (claim_id);


--
-- Name: idx_claim_line_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_line_service ON public.claim_lines USING btree (medical_service_id);


--
-- Name: idx_claim_line_service_analysis; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claim_line_service_analysis ON public.claim_lines USING btree (medical_service_id, total_price);


--
-- Name: idx_claims_approval_metrics; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_approval_metrics ON public.claims USING btree (status, reviewed_at, approved_amount);


--
-- Name: idx_claims_approval_metrics_full; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_approval_metrics_full ON public.claims USING btree (status, reviewed_at, approved_amount) WHERE ((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[]));


--
-- Name: idx_claims_member_date_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_member_date_status ON public.claims USING btree (member_id, service_date DESC, status);


--
-- Name: idx_claims_member_date_status_reporting; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_member_date_status_reporting ON public.claims USING btree (member_id, service_date DESC, status);


--
-- Name: idx_claims_monthly_reporting; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_monthly_reporting ON public.claims USING btree (status, service_date, approved_amount);


--
-- Name: idx_claims_monthly_reporting_full; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_monthly_reporting_full ON public.claims USING btree (status, service_date, provider_id, approved_amount);


--
-- Name: idx_claims_pending_review; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_pending_review ON public.claims USING btree (status, created_at DESC) WHERE ((status)::text = 'SUBMITTED'::text);


--
-- Name: idx_claims_pre_auth; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_pre_auth ON public.claims USING btree (pre_authorization_id);


--
-- Name: idx_claims_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_provider_date ON public.claims USING btree (provider_id, service_date DESC, status);


--
-- Name: idx_claims_provider_date_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_provider_date_status ON public.claims USING btree (provider_id, service_date DESC, status);


--
-- Name: idx_claims_provider_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_provider_status ON public.claims USING btree (provider_id, status, approved_amount);


--
-- Name: idx_claims_provider_status_approved; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_provider_status_approved ON public.claims USING btree (provider_id, status, approved_amount) WHERE ((status)::text = 'APPROVED'::text);


--
-- Name: idx_claims_reviewer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_reviewer ON public.claims USING btree (reviewer_id, status, service_date DESC);


--
-- Name: idx_claims_reviewer_status_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_reviewer_status_date ON public.claims USING btree (reviewer_id, status, service_date DESC);


--
-- Name: idx_claims_sla; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_sla ON public.claims USING btree (within_sla, actual_completion_date);


--
-- Name: idx_claims_unassigned; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_claims_unassigned ON public.claims USING btree (status, service_date DESC) WHERE (reviewer_id IS NULL);


--
-- Name: idx_contracts_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contracts_employer ON public.provider_contracts USING btree (employer_id);


--
-- Name: idx_contracts_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contracts_provider ON public.provider_contracts USING btree (provider_id);


--
-- Name: idx_contracts_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contracts_status ON public.provider_contracts USING btree (contract_status);


--
-- Name: idx_deductibles_member; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_deductibles_member ON public.member_deductibles USING btree (member_id);


--
-- Name: idx_deductibles_near_limit; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_deductibles_near_limit ON public.member_deductibles USING btree (member_id, deductible_year) WHERE (deductible_used >= (total_deductible * 0.8));


--
-- Name: idx_deductibles_year; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_deductibles_year ON public.member_deductibles USING btree (deductible_year);


--
-- Name: idx_eligibility_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_date ON public.eligibility_checks USING btree (check_date DESC);


--
-- Name: idx_eligibility_member; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_member ON public.eligibility_checks USING btree (member_id);


--
-- Name: idx_eligibility_member_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_member_date ON public.eligibility_checks USING btree (member_id, check_timestamp DESC);


--
-- Name: idx_eligibility_policy_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_policy_id ON public.eligibility_checks USING btree (policy_id);


--
-- Name: idx_eligibility_request_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_request_id ON public.eligibility_checks USING btree (request_id);


--
-- Name: idx_eligibility_scope; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_scope ON public.eligibility_checks USING btree (company_scope_id);


--
-- Name: idx_eligibility_service_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_service_date ON public.eligibility_checks USING btree (service_date DESC);


--
-- Name: idx_eligibility_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_eligibility_status ON public.eligibility_checks USING btree (status);


--
-- Name: idx_email_tokens_expires_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_email_tokens_expires_at ON public.email_verification_tokens USING btree (expires_at);


--
-- Name: idx_email_tokens_expiry; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_email_tokens_expiry ON public.email_verification_tokens USING btree (expiry_date);


--
-- Name: idx_email_tokens_token; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_email_tokens_token ON public.email_verification_tokens USING btree (token);


--
-- Name: idx_email_tokens_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_email_tokens_user ON public.email_verification_tokens USING btree (user_id);


--
-- Name: idx_employers_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employers_active ON public.employers USING btree (active);


--
-- Name: idx_employers_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employers_code ON public.employers USING btree (code);


--
-- Name: idx_employers_default; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employers_default ON public.employers USING btree (is_default) WHERE (is_default = true);


--
-- Name: idx_import_errors_error_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_import_errors_error_type ON public.member_import_errors USING btree (error_type);


--
-- Name: idx_import_errors_log_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_import_errors_log_id ON public.member_import_errors USING btree (import_log_id);


--
-- Name: idx_import_errors_row_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_import_errors_row_number ON public.member_import_errors USING btree (row_number);


--
-- Name: idx_legacy_contracts_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_legacy_contracts_active ON public.legacy_provider_contracts USING btree (active);


--
-- Name: idx_legacy_contracts_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_legacy_contracts_provider ON public.legacy_provider_contracts USING btree (provider_id);


--
-- Name: idx_legacy_contracts_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_legacy_contracts_service ON public.legacy_provider_contracts USING btree (service_code);


--
-- Name: idx_login_attempts_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_created ON public.user_login_attempts USING btree (created_at);


--
-- Name: idx_login_attempts_failed; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_failed ON public.user_login_attempts USING btree (username, attempted_at DESC) WHERE (success = false);


--
-- Name: idx_login_attempts_failed_window; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_failed_window ON public.user_login_attempts USING btree (username, attempted_at DESC) WHERE (success = false);


--
-- Name: idx_login_attempts_result; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_result ON public.user_login_attempts USING btree (attempt_result);


--
-- Name: idx_login_attempts_success_attempted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_success_attempted ON public.user_login_attempts USING btree (success, attempted_at DESC);


--
-- Name: idx_login_attempts_user_id_attempted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_user_id_attempted ON public.user_login_attempts USING btree (user_id, attempted_at DESC);


--
-- Name: idx_login_attempts_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_attempts_username ON public.user_login_attempts USING btree (username);


--
-- Name: idx_medical_categories_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_categories_active ON public.medical_categories USING btree (active);


--
-- Name: idx_medical_categories_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_categories_code ON public.medical_categories USING btree (code);


--
-- Name: idx_medical_categories_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_categories_deleted ON public.medical_categories USING btree (deleted) WHERE (deleted = false);


--
-- Name: idx_medical_categories_parent_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_categories_parent_id ON public.medical_categories USING btree (parent_id);


--
-- Name: idx_medical_services_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_services_active ON public.medical_services USING btree (active);


--
-- Name: idx_medical_services_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_services_category ON public.medical_services USING btree (category_id);


--
-- Name: idx_medical_services_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_services_code ON public.medical_services USING btree (service_code);


--
-- Name: idx_medical_services_is_master; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_services_is_master ON public.medical_services USING btree (is_master) WHERE (deleted = false);


--
-- Name: idx_medical_specialties_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medical_specialties_deleted ON public.medical_specialties USING btree (deleted) WHERE (deleted = false);


--
-- Name: idx_member_attributes_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_attributes_code ON public.member_attributes USING btree (attribute_code);


--
-- Name: idx_member_attributes_member; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_attributes_member ON public.member_attributes USING btree (member_id);


--
-- Name: idx_member_import_logs_batch; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_import_logs_batch ON public.member_import_logs USING btree (import_batch_id);


--
-- Name: idx_member_import_logs_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_import_logs_created ON public.member_import_logs USING btree (created_at DESC);


--
-- Name: idx_member_import_logs_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_import_logs_status ON public.member_import_logs USING btree (status);


--
-- Name: idx_member_import_logs_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_member_import_logs_user ON public.member_import_logs USING btree (imported_by_user_id) WHERE (imported_by_user_id IS NOT NULL);


--
-- Name: idx_members_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_active ON public.members USING btree (active);


--
-- Name: idx_members_barcode; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_barcode ON public.members USING btree (barcode);


--
-- Name: idx_members_benefit_policy; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_benefit_policy ON public.members USING btree (benefit_policy_id);


--
-- Name: idx_members_card_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_card_id ON public.members USING btree (member_card_id);


--
-- Name: idx_members_card_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_card_number ON public.members USING btree (card_number);


--
-- Name: idx_members_civil_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_civil_id ON public.members USING btree (civil_id);


--
-- Name: idx_members_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_employer ON public.members USING btree (employer_id);


--
-- Name: idx_members_employer_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_employer_active ON public.members USING btree (employer_id, active) WHERE (active = true);


--
-- Name: idx_members_employer_active_report; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_employer_active_report ON public.members USING btree (employer_id) WHERE (active = true);


--
-- Name: idx_members_employer_search; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_employer_search ON public.members USING btree (employer_id, civil_id, full_name) WHERE (active = true);


--
-- Name: idx_members_national_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_national_id ON public.members USING btree (national_id);


--
-- Name: idx_members_parent_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_parent_id ON public.members USING btree (parent_id);


--
-- Name: idx_members_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_members_status ON public.members USING btree (status);


--
-- Name: idx_mrp_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_mrp_active ON public.medical_reviewer_providers USING btree (active);


--
-- Name: idx_mrp_provider_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_mrp_provider_id ON public.medical_reviewer_providers USING btree (provider_id);


--
-- Name: idx_mrp_reviewer_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_mrp_reviewer_id ON public.medical_reviewer_providers USING btree (reviewer_id);


--
-- Name: idx_msc_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_msc_category_id ON public.medical_service_categories USING btree (category_id);


--
-- Name: idx_msc_context; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_msc_context ON public.medical_service_categories USING btree (context);


--
-- Name: idx_msc_service_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_msc_service_id ON public.medical_service_categories USING btree (service_id);


--
-- Name: idx_network_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_network_employer ON public.network_providers USING btree (employer_id);


--
-- Name: idx_network_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_network_provider ON public.network_providers USING btree (provider_id);


--
-- Name: idx_network_tier; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_network_tier ON public.network_providers USING btree (network_tier);


--
-- Name: idx_package_services_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_package_services_service ON public.medical_service_categories USING btree (service_id);


--
-- Name: idx_password_tokens_expires_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_password_tokens_expires_at ON public.password_reset_tokens USING btree (expires_at);


--
-- Name: idx_password_tokens_expiry; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_password_tokens_expiry ON public.password_reset_tokens USING btree (expiry_date);


--
-- Name: idx_password_tokens_token; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_password_tokens_token ON public.password_reset_tokens USING btree (token);


--
-- Name: idx_password_tokens_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_password_tokens_user ON public.password_reset_tokens USING btree (user_id);


--
-- Name: idx_pauthreq_expiring; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_pauthreq_expiring ON public.preauthorization_requests USING btree (valid_until) WHERE (((status)::text = 'APPROVED'::text) AND (valid_until IS NOT NULL));


--
-- Name: idx_pauthreq_member_status_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_pauthreq_member_status_date ON public.preauthorization_requests USING btree (member_id, status, created_at DESC);


--
-- Name: idx_pauthreq_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_pauthreq_provider_date ON public.preauthorization_requests USING btree (provider_id, created_at DESC, status);


--
-- Name: idx_pma_raw_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_pma_raw_service ON public.provider_mapping_audit USING btree (provider_raw_service_id);


--
-- Name: idx_policies_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_active ON public.benefit_policies USING btree (active);


--
-- Name: idx_policies_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_code ON public.benefit_policies USING btree (policy_code);


--
-- Name: idx_policies_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_employer ON public.benefit_policies USING btree (employer_id);


--
-- Name: idx_policies_end_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_end_date ON public.benefit_policies USING btree (end_date);


--
-- Name: idx_policies_start_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_start_date ON public.benefit_policies USING btree (start_date);


--
-- Name: idx_policies_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policies_type ON public.benefit_policies USING btree (policy_type);


--
-- Name: idx_policy_assignments_dates; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policy_assignments_dates ON public.member_policy_assignments USING btree (assignment_start_date, assignment_end_date);


--
-- Name: idx_policy_assignments_member; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policy_assignments_member ON public.member_policy_assignments USING btree (member_id);


--
-- Name: idx_policy_assignments_policy; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_policy_assignments_policy ON public.member_policy_assignments USING btree (policy_id);


--
-- Name: idx_preauth_audit_action; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_audit_action ON public.pre_authorization_audit USING btree (action);


--
-- Name: idx_preauth_audit_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_audit_date ON public.pre_authorization_audit USING btree (change_date DESC);


--
-- Name: idx_preauth_audit_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_audit_id ON public.pre_authorization_audit USING btree (pre_authorization_id);


--
-- Name: idx_preauth_audit_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_audit_user ON public.pre_authorization_audit USING btree (changed_by);


--
-- Name: idx_preauth_expiring; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_expiring ON public.preauthorization_requests USING btree (valid_until) WHERE (((status)::text = 'APPROVED'::text) AND (valid_until IS NOT NULL));


--
-- Name: idx_preauth_member_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_member_id ON public.pre_authorizations USING btree (member_id);


--
-- Name: idx_preauth_member_status_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_member_status_date ON public.preauthorization_requests USING btree (member_id, status, created_at DESC);


--
-- Name: idx_preauth_provider_date_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_provider_date_status ON public.preauthorization_requests USING btree (provider_id, created_at DESC, status);


--
-- Name: idx_preauth_provider_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_provider_id ON public.pre_authorizations USING btree (provider_id);


--
-- Name: idx_preauth_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_preauth_status ON public.pre_authorizations USING btree (status);


--
-- Name: idx_provider_contracts_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_contracts_active ON public.provider_contracts USING btree (active);


--
-- Name: idx_provider_contracts_expiring; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_contracts_expiring ON public.provider_contracts USING btree (contract_end_date) WHERE ((active = true) AND (contract_end_date IS NOT NULL));


--
-- Name: idx_provider_docs_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_docs_provider ON public.provider_admin_documents USING btree (provider_id);


--
-- Name: idx_provider_docs_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_docs_type ON public.provider_admin_documents USING btree (document_type);


--
-- Name: idx_provider_payments_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_payments_date ON public.provider_payments USING btree (payment_date DESC);


--
-- Name: idx_provider_payments_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_payments_provider ON public.provider_payments USING btree (provider_id);


--
-- Name: idx_provider_services_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_services_active ON public.provider_services USING btree (active);


--
-- Name: idx_provider_services_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_services_code ON public.provider_services USING btree (service_code);


--
-- Name: idx_provider_services_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_provider_services_provider ON public.provider_services USING btree (provider_id);


--
-- Name: idx_providers_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_providers_active ON public.providers USING btree (active);


--
-- Name: idx_providers_license; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_providers_license ON public.providers USING btree (license_number);


--
-- Name: idx_providers_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_providers_type ON public.providers USING btree (provider_type) WHERE (active = true);


--
-- Name: idx_prs_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prs_provider ON public.provider_raw_services USING btree (provider_id);


--
-- Name: idx_prs_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prs_status ON public.provider_raw_services USING btree (status);


--
-- Name: idx_psm_medical_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_psm_medical_service ON public.provider_service_mappings USING btree (medical_service_id);


--
-- Name: idx_settlement_batch_payment; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_settlement_batch_payment ON public.settlement_batches USING btree (status, paid_at, total_amount);


--
-- Name: idx_settlement_batches_provider_date_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_settlement_batches_provider_date_status ON public.settlement_batches USING btree (provider_id, created_at, status);


--
-- Name: idx_settlements_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_settlements_active ON public.settlement_batches USING btree (status, created_at DESC) WHERE ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying])::text[]));


--
-- Name: idx_system_settings_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_system_settings_active ON public.system_settings USING btree (active) WHERE (active = true);


--
-- Name: idx_system_settings_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_system_settings_key ON public.system_settings USING btree (setting_key);


--
-- Name: idx_transactions_account; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_account ON public.account_transactions USING btree (provider_account_id);


--
-- Name: idx_transactions_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_created ON public.account_transactions USING btree (created_at DESC);


--
-- Name: idx_transactions_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_date ON public.account_transactions USING btree (transaction_date DESC);


--
-- Name: idx_transactions_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_provider_date ON public.account_transactions USING btree (provider_account_id, transaction_date DESC);


--
-- Name: idx_transactions_reference; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_reference ON public.account_transactions USING btree (reference_type, reference_id);


--
-- Name: idx_transactions_reporting; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_reporting ON public.account_transactions USING btree (transaction_date, transaction_type, amount);


--
-- Name: idx_transactions_reporting_full; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_reporting_full ON public.account_transactions USING btree (transaction_date, transaction_type, amount);


--
-- Name: idx_transactions_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_transactions_type ON public.account_transactions USING btree (transaction_type);


--
-- Name: idx_user_audit_action_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_audit_action_created ON public.user_audit_log USING btree (action_type, created_at DESC);


--
-- Name: idx_users_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_active ON public.users USING btree (is_active);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_employer ON public.users USING btree (employer_id) WHERE (employer_id IS NOT NULL);


--
-- Name: idx_users_enabled; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_enabled ON public.users USING btree (enabled);


--
-- Name: idx_users_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_provider ON public.users USING btree (provider_id) WHERE (provider_id IS NOT NULL);


--
-- Name: idx_users_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_type ON public.users USING btree (user_type);


--
-- Name: idx_users_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_username ON public.users USING btree (username);


--
-- Name: idx_visit_attachments_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visit_attachments_type ON public.visit_attachments USING btree (attachment_type);


--
-- Name: idx_visit_attachments_visit; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visit_attachments_visit ON public.visit_attachments USING btree (visit_id);


--
-- Name: idx_visit_attachments_visit_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visit_attachments_visit_date ON public.visit_attachments USING btree (visit_id, created_at DESC);


--
-- Name: idx_visits_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_category ON public.visits USING btree (medical_category_id) WHERE (medical_category_id IS NOT NULL);


--
-- Name: idx_visits_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_date ON public.visits USING btree (visit_date DESC);


--
-- Name: idx_visits_employer; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_employer ON public.visits USING btree (employer_id);


--
-- Name: idx_visits_member; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_member ON public.visits USING btree (member_id);


--
-- Name: idx_visits_member_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_member_date ON public.visits USING btree (member_id, visit_date DESC);


--
-- Name: idx_visits_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_provider ON public.visits USING btree (provider_id);


--
-- Name: idx_visits_provider_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_provider_date ON public.visits USING btree (provider_id, visit_date DESC);


--
-- Name: idx_visits_service; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_service ON public.visits USING btree (medical_service_id) WHERE (medical_service_id IS NOT NULL);


--
-- Name: idx_visits_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_visits_status ON public.visits USING btree (status);


--
-- Name: uk_legacy_contract_service_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_legacy_contract_service_date ON public.legacy_provider_contracts USING btree (provider_id, service_code, effective_from);


--
-- Name: uq_active_contract_per_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_active_contract_per_provider ON public.provider_contracts USING btree (provider_id) WHERE ((contract_status)::text = 'ACTIVE'::text);


--
-- Name: uq_medical_services_code_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_medical_services_code_active ON public.medical_services USING btree (code) WHERE (deleted = false);


--
-- Name: uq_module_access_module_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_module_access_module_key ON public.module_access USING btree (module_key);


--
-- Name: uq_network_provider; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_network_provider ON public.network_providers USING btree (employer_id, provider_id) WHERE (active = true);


--
-- Name: user_login_attempts trg_sync_login_attempt_result; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_sync_login_attempt_result BEFORE INSERT OR UPDATE ON public.user_login_attempts FOR EACH ROW EXECUTE FUNCTION public.trg_sync_login_attempt_result_fn();


--
-- Name: user_audit_log trg_sync_user_audit_log; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_sync_user_audit_log BEFORE INSERT OR UPDATE ON public.user_audit_log FOR EACH ROW EXECUTE FUNCTION public.trg_sync_user_audit_log_fn();


--
-- Name: provider_accounts fk_account_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_accounts
    ADD CONSTRAINT fk_account_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: ent_service_aliases fk_alias_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ent_service_aliases
    ADD CONSTRAINT fk_alias_service FOREIGN KEY (medical_service_id) REFERENCES public.medical_services(id) ON DELETE CASCADE;


--
-- Name: provider_allowed_employers fk_allowed_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_allowed_employers
    ADD CONSTRAINT fk_allowed_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE CASCADE;


--
-- Name: provider_allowed_employers fk_allowed_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_allowed_employers
    ADD CONSTRAINT fk_allowed_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE CASCADE;


--
-- Name: member_policy_assignments fk_assignment_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_policy_assignments
    ADD CONSTRAINT fk_assignment_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- Name: member_policy_assignments fk_assignment_policy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_policy_assignments
    ADD CONSTRAINT fk_assignment_policy FOREIGN KEY (policy_id) REFERENCES public.benefit_policies(id) ON DELETE RESTRICT;


--
-- Name: user_audit_log fk_audit_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_audit_log
    ADD CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: settlement_batch_items fk_batch_item_batch; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batch_items
    ADD CONSTRAINT fk_batch_item_batch FOREIGN KEY (batch_id) REFERENCES public.settlement_batches(id) ON DELETE CASCADE;


--
-- Name: settlement_batch_items fk_batch_item_claim; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batch_items
    ADD CONSTRAINT fk_batch_item_claim FOREIGN KEY (claim_id) REFERENCES public.claims(id) ON DELETE RESTRICT;


--
-- Name: claim_attachments fk_claim_attachment; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_attachments
    ADD CONSTRAINT fk_claim_attachment FOREIGN KEY (claim_id) REFERENCES public.claims(id) ON DELETE CASCADE;


--
-- Name: claim_history fk_claim_history; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_history
    ADD CONSTRAINT fk_claim_history FOREIGN KEY (claim_id) REFERENCES public.claims(id) ON DELETE CASCADE;


--
-- Name: claim_lines fk_claim_line_claim; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_lines
    ADD CONSTRAINT fk_claim_line_claim FOREIGN KEY (claim_id) REFERENCES public.claims(id) ON DELETE CASCADE;


--
-- Name: claim_lines fk_claim_line_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claim_lines
    ADD CONSTRAINT fk_claim_line_service FOREIGN KEY (medical_service_id) REFERENCES public.medical_services(id) ON DELETE RESTRICT;


--
-- Name: claims fk_claim_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT fk_claim_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- Name: claims fk_claim_preauth; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT fk_claim_preauth FOREIGN KEY (pre_authorization_id) REFERENCES public.preauthorization_requests(id) ON DELETE RESTRICT;


--
-- Name: claims fk_claim_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT fk_claim_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: provider_contracts fk_contract_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contracts
    ADD CONSTRAINT fk_contract_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: provider_contracts fk_contract_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contracts
    ADD CONSTRAINT fk_contract_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: member_deductibles fk_deductible_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_deductibles
    ADD CONSTRAINT fk_deductible_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- Name: eligibility_checks fk_eligibility_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.eligibility_checks
    ADD CONSTRAINT fk_eligibility_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- Name: eligibility_checks fk_eligibility_policy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.eligibility_checks
    ADD CONSTRAINT fk_eligibility_policy FOREIGN KEY (policy_id) REFERENCES public.benefit_policies(id) ON DELETE RESTRICT;


--
-- Name: email_verification_tokens fk_email_verify_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT fk_email_verify_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: member_import_errors fk_import_errors_log; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_import_errors
    ADD CONSTRAINT fk_import_errors_log FOREIGN KEY (import_log_id) REFERENCES public.member_import_logs(id) ON DELETE CASCADE;


--
-- Name: medical_categories fk_medical_category_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_categories
    ADD CONSTRAINT fk_medical_category_parent FOREIGN KEY (parent_id) REFERENCES public.medical_categories(id) ON DELETE RESTRICT;


--
-- Name: medical_services fk_medical_service_category; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_services
    ADD CONSTRAINT fk_medical_service_category FOREIGN KEY (category_id) REFERENCES public.medical_categories(id) ON DELETE RESTRICT;


--
-- Name: member_attributes fk_member_attrs_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.member_attributes
    ADD CONSTRAINT fk_member_attrs_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE CASCADE;


--
-- Name: members fk_member_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_member_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: members fk_member_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_member_parent FOREIGN KEY (parent_id) REFERENCES public.members(id) ON DELETE SET NULL;


--
-- Name: members fk_member_policy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.members
    ADD CONSTRAINT fk_member_policy FOREIGN KEY (benefit_policy_id) REFERENCES public.benefit_policies(id) ON DELETE SET NULL;


--
-- Name: medical_reviewer_providers fk_mrp_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_reviewer_providers
    ADD CONSTRAINT fk_mrp_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: medical_reviewer_providers fk_mrp_reviewer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_reviewer_providers
    ADD CONSTRAINT fk_mrp_reviewer FOREIGN KEY (reviewer_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: medical_service_categories fk_msc_category; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_service_categories
    ADD CONSTRAINT fk_msc_category FOREIGN KEY (category_id) REFERENCES public.medical_categories(id) ON DELETE RESTRICT;


--
-- Name: medical_service_categories fk_msc_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_service_categories
    ADD CONSTRAINT fk_msc_service FOREIGN KEY (service_id) REFERENCES public.medical_services(id) ON DELETE CASCADE;


--
-- Name: network_providers fk_network_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.network_providers
    ADD CONSTRAINT fk_network_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: network_providers fk_network_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.network_providers
    ADD CONSTRAINT fk_network_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: password_reset_tokens fk_password_reset_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: preauthorization_requests fk_pauthreq_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.preauthorization_requests
    ADD CONSTRAINT fk_pauthreq_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- Name: preauthorization_requests fk_pauthreq_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.preauthorization_requests
    ADD CONSTRAINT fk_pauthreq_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: provider_payments fk_payment_batch; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT fk_payment_batch FOREIGN KEY (settlement_batch_id) REFERENCES public.settlement_batches(id);


--
-- Name: provider_payments fk_payment_created_by; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT fk_payment_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: provider_payments fk_payment_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_payments
    ADD CONSTRAINT fk_payment_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id);


--
-- Name: provider_mapping_audit fk_pma_raw; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_mapping_audit
    ADD CONSTRAINT fk_pma_raw FOREIGN KEY (provider_raw_service_id) REFERENCES public.provider_raw_services(id);


--
-- Name: provider_mapping_audit fk_pma_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_mapping_audit
    ADD CONSTRAINT fk_pma_user FOREIGN KEY (performed_by) REFERENCES public.users(id);


--
-- Name: benefit_policies fk_policy_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policies
    ADD CONSTRAINT fk_policy_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: pre_authorization_attachments fk_preauth_att; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pre_authorization_attachments
    ADD CONSTRAINT fk_preauth_att FOREIGN KEY (preauthorization_request_id) REFERENCES public.preauthorization_requests(id) ON DELETE CASCADE;


--
-- Name: provider_contract_pricing_items fk_pricing_contract; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contract_pricing_items
    ADD CONSTRAINT fk_pricing_contract FOREIGN KEY (contract_id) REFERENCES public.provider_contracts(id) ON DELETE CASCADE;


--
-- Name: provider_contract_pricing_items fk_pricing_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_contract_pricing_items
    ADD CONSTRAINT fk_pricing_service FOREIGN KEY (medical_service_id) REFERENCES public.medical_services(id) ON DELETE RESTRICT;


--
-- Name: provider_admin_documents fk_provider_docs; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_admin_documents
    ADD CONSTRAINT fk_provider_docs FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE CASCADE;


--
-- Name: provider_services fk_provider_services_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_services
    ADD CONSTRAINT fk_provider_services_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE CASCADE;


--
-- Name: provider_raw_services fk_prs_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_raw_services
    ADD CONSTRAINT fk_prs_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE CASCADE;


--
-- Name: provider_service_mappings fk_psm_raw; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings
    ADD CONSTRAINT fk_psm_raw FOREIGN KEY (provider_raw_service_id) REFERENCES public.provider_raw_services(id) ON DELETE CASCADE;


--
-- Name: provider_service_mappings fk_psm_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings
    ADD CONSTRAINT fk_psm_service FOREIGN KEY (medical_service_id) REFERENCES public.medical_services(id) ON DELETE RESTRICT;


--
-- Name: provider_service_mappings fk_psm_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.provider_service_mappings
    ADD CONSTRAINT fk_psm_user FOREIGN KEY (mapped_by) REFERENCES public.users(id);


--
-- Name: benefit_policy_rules fk_rule_policy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policy_rules
    ADD CONSTRAINT fk_rule_policy FOREIGN KEY (policy_id) REFERENCES public.benefit_policies(id) ON DELETE CASCADE;


--
-- Name: benefit_policy_rules fk_rule_service; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.benefit_policy_rules
    ADD CONSTRAINT fk_rule_service FOREIGN KEY (medical_service_id) REFERENCES public.medical_services(id) ON DELETE RESTRICT;


--
-- Name: settlement_batches fk_settlement_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settlement_batches
    ADD CONSTRAINT fk_settlement_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: account_transactions fk_transaction_account; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account_transactions
    ADD CONSTRAINT fk_transaction_account FOREIGN KEY (provider_account_id) REFERENCES public.provider_accounts(id) ON DELETE RESTRICT;


--
-- Name: users fk_user_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_user_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: users fk_user_provider; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_user_provider FOREIGN KEY (provider_id) REFERENCES public.providers(id) ON DELETE RESTRICT;


--
-- Name: visit_attachments fk_visit_attachment; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visit_attachments
    ADD CONSTRAINT fk_visit_attachment FOREIGN KEY (visit_id) REFERENCES public.visits(id) ON DELETE CASCADE;


--
-- Name: visits fk_visit_employer; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT fk_visit_employer FOREIGN KEY (employer_id) REFERENCES public.employers(id) ON DELETE RESTRICT;


--
-- Name: visits fk_visit_member; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.visits
    ADD CONSTRAINT fk_visit_member FOREIGN KEY (member_id) REFERENCES public.members(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict UUoleM3y8krhcEoQFe28M9ZC9ed4Ym3wvK7EqnAXDxIAWFa3bQONzQNJsbkKnjo

