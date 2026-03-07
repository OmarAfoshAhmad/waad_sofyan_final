-- Phase 3: Add FK constraints to visits table
-- All 4 columns are nullable, all orphan counts verified = 0

ALTER TABLE visits
    ADD CONSTRAINT fk_visit_provider
        FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT;

ALTER TABLE visits
    ADD CONSTRAINT fk_visit_medical_service
        FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE RESTRICT;

ALTER TABLE visits
    ADD CONSTRAINT fk_visit_medical_category
        FOREIGN KEY (medical_category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT;

ALTER TABLE visits
    ADD CONSTRAINT fk_visit_eligibility_check
        FOREIGN KEY (eligibility_check_id) REFERENCES eligibility_checks(id) ON DELETE RESTRICT;
