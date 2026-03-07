-- Create missing table for ProviderServicePriceImportLog entity

CREATE TABLE IF NOT EXISTS provider_service_price_import_logs (
    id BIGSERIAL PRIMARY KEY,
    import_batch_id VARCHAR(64) NOT NULL UNIQUE,
    provider_id BIGINT NOT NULL,
    provider_code VARCHAR(100) NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT,
    import_mode VARCHAR(20) NOT NULL DEFAULT 'REPLACE',
    total_rows INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    skipped_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    error_details JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    processing_time_ms BIGINT,
    imported_by_user_id BIGINT,
    imported_by_username VARCHAR(100),
    created_at TIMESTAMP
);
