-- V40: Align pdf_company_settings.logo_data with @Lob mapping expected by Hibernate (PostgreSQL oid)

ALTER TABLE pdf_company_settings
    ALTER COLUMN logo_data TYPE oid
    USING NULL::oid;
