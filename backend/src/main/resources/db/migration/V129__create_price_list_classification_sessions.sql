CREATE TABLE IF NOT EXISTS price_list_classification_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_name VARCHAR(300) NOT NULL,
    original_file_name VARCHAR(500),
    provider_id BIGINT,
    provider_name VARCHAR(255),
    contract_id BIGINT,
    contract_code VARCHAR(100),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    total_rows INTEGER NOT NULL DEFAULT 0,
    high_confidence_count INTEGER NOT NULL DEFAULT 0,
    needs_review_count INTEGER NOT NULL DEFAULT 0,
    unknown_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    ranged_price_count INTEGER NOT NULL DEFAULT 0,
    posted_count INTEGER NOT NULL DEFAULT 0,
    notes VARCHAR(2000),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_list_session_status ON price_list_classification_sessions(status);
CREATE INDEX IF NOT EXISTS idx_price_list_session_provider ON price_list_classification_sessions(provider_id);
CREATE INDEX IF NOT EXISTS idx_price_list_session_contract ON price_list_classification_sessions(contract_id);
CREATE INDEX IF NOT EXISTS idx_price_list_session_created_at ON price_list_classification_sessions(created_at);

CREATE TABLE IF NOT EXISTS price_list_classification_items (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES price_list_classification_sessions(id) ON DELETE CASCADE,
    row_number INTEGER,
    source_sheet VARCHAR(255),
    provider_service_code VARCHAR(100),
    provider_service_name VARCHAR(500) NOT NULL,
    canonical_name VARCHAR(300),
    dictionary_entry_id BIGINT,
    medical_category_id BIGINT,
    medical_category_code VARCHAR(100),
    medical_category_name VARCHAR(255),
    confidence INTEGER,
    status VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    price NUMERIC(15,2),
    min_price NUMERIC(15,2),
    max_price NUMERIC(15,2),
    price_label VARCHAR(100),
    duplicate_name BOOLEAN NOT NULL DEFAULT FALSE,
    merged_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    merged_source_count INTEGER NOT NULL DEFAULT 1,
    merge_notes VARCHAR(2000),
    manual_review_note VARCHAR(2000),
    posted_pricing_item_id BIGINT,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_list_item_session ON price_list_classification_items(session_id);
CREATE INDEX IF NOT EXISTS idx_price_list_item_status ON price_list_classification_items(status);
CREATE INDEX IF NOT EXISTS idx_price_list_item_category ON price_list_classification_items(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_price_list_item_provider_code ON price_list_classification_items(provider_service_code);
CREATE INDEX IF NOT EXISTS idx_price_list_item_posted ON price_list_classification_items(posted_pricing_item_id);
