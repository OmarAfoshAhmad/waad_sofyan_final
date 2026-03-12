-- ============================================================
-- Table: medical_service_categories
-- Depends on: medical_services, medical_categories
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_service_categories (
    id          BIGINT PRIMARY KEY DEFAULT nextval('medical_service_category_seq'),
    service_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    context     VARCHAR(20) NOT NULL DEFAULT 'ANY'
        CHECK (context IN ('OUTPATIENT','INPATIENT','EMERGENCY','ANY')),
    is_primary  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_msc_service  FOREIGN KEY (service_id)  REFERENCES medical_services(id)   ON DELETE CASCADE,
    CONSTRAINT fk_msc_category FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT,
    CONSTRAINT uq_msc_primary_per_context UNIQUE (service_id, context, is_primary)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_msc_service_id                    ON medical_service_categories(service_id);
CREATE INDEX IF NOT EXISTS idx_msc_category_id                   ON medical_service_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_msc_context                       ON medical_service_categories(context);
CREATE INDEX IF NOT EXISTS idx_medical_svc_categories_category   ON medical_service_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_medical_svc_categories_composite  ON medical_service_categories(category_id, service_id);
