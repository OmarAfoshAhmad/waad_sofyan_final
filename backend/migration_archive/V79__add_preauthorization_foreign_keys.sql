-- Phase 4: Add FK constraints to pre_authorizations table
-- All 3 columns are nullable, all orphan counts verified = 0

ALTER TABLE pre_authorizations
    ADD CONSTRAINT fk_preauth_medical_service
        FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE RESTRICT;

ALTER TABLE pre_authorizations
    ADD CONSTRAINT fk_preauth_medical_category
        FOREIGN KEY (service_category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT;

ALTER TABLE pre_authorizations
    ADD CONSTRAINT fk_preauth_visit
        FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT;
