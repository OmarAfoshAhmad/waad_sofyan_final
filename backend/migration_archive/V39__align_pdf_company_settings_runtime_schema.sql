-- V39: Create pdf_company_settings table required by PdfCompanySettings entity

CREATE TABLE IF NOT EXISTS pdf_company_settings (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(512),
    logo_data BYTEA,
    address TEXT,
    phone VARCHAR(50),
    email VARCHAR(100),
    website VARCHAR(255),
    footer_text TEXT,
    footer_text_en TEXT,
    header_color VARCHAR(7),
    footer_color VARCHAR(7),
    page_size VARCHAR(20),
    margin_top INTEGER,
    margin_bottom INTEGER,
    margin_left INTEGER,
    margin_right INTEGER,
    is_active BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
