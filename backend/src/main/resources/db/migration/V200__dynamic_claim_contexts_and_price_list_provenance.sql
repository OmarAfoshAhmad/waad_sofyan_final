CREATE TABLE claim_contexts (
    code VARCHAR(60) PRIMARY KEY,
    name_ar VARCHAR(120) NOT NULL,
    base_encounter_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 100,
    CONSTRAINT chk_claim_context_base_encounter
        CHECK (base_encounter_type IN ('OUTPATIENT', 'INPATIENT', 'ANY'))
);

INSERT INTO claim_contexts(code, name_ar, base_encounter_type, display_order)
VALUES ('OUTPATIENT', 'عيادات خارجية', 'OUTPATIENT', 10),
       ('INPATIENT', 'إيواء', 'INPATIENT', 20);

CREATE TABLE claim_context_source_aliases (
    id BIGSERIAL PRIMARY KEY,
    source_alias VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    claim_context_code VARCHAR(60) NOT NULL REFERENCES claim_contexts(code),
    medical_category_code VARCHAR(100) REFERENCES medical_categories(code),
    provider_id BIGINT REFERENCES providers(id),
    requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_claim_context_alias_provider
    ON claim_context_source_aliases(normalized_alias, COALESCE(provider_id, 0));

-- Only classifications observed in the six approved provider price lists.
INSERT INTO claim_context_source_aliases
    (source_alias, normalized_alias, claim_context_code, medical_category_code, requires_review)
VALUES
    ('إيواء', 'ايواء', 'INPATIENT', NULL, FALSE),
    ('عيادات خارجية', 'عيادات خارجيه', 'OUTPATIENT', NULL, FALSE),
    ('أشعة تحاليل رسوم أطباء', 'اشعه تحاليل رسوم اطباء', 'OUTPATIENT', NULL, FALSE),
    ('الرنين المغناطيسي والمقطعية والاشعة التشخيصية',
     'الرنين المغناطيسي والمقطعيه والاشعه التشخيصيه', 'OUTPATIENT', 'CAT-IMG-ADV', FALSE),
    ('أسنان روتيني', 'اسنان روتيني', 'OUTPATIENT', 'CAT-DENT-ROUTINE', FALSE),
    ('علاج طبيعي', 'علاج طبيعي', 'OUTPATIENT', 'CAT-PHYSIO', FALSE),
    ('تمريض منزلي', 'تمريض منزلي', 'INPATIENT', 'CAT-HOME-NURSING', TRUE);

INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
    ('CAT-COV-DENT-ADVANCED', 'أسنان تركيبات وزراعة وتقويم', 'أسنان تركيبات وزراعة وتقويم',
     'Dental Prosthodontics, Implants and Orthodontics', NULL, NULL, false, true, NOW(), NOW()),
    ('CAT-COV-EYE-OPTICAL', 'كشف عيون ونظارة', 'كشف عيون ونظارة',
     'Eye Examination and Optical', NULL, NULL, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, name_ar = EXCLUDED.name_ar, name_en = EXCLUDED.name_en,
    active = true, deleted = false, updated_at = NOW();

INSERT INTO claim_context_source_aliases
    (source_alias, normalized_alias, claim_context_code, medical_category_code, requires_review)
VALUES ('أسنان تجميلي', 'اسنان تجميلي', 'OUTPATIENT', 'CAT-COV-DENT-ADVANCED', FALSE);

DO $$
DECLARE advanced_id BIGINT; optical_id BIGINT;
BEGIN
    SELECT id INTO advanced_id FROM medical_categories WHERE code = 'CAT-COV-DENT-ADVANCED';
    SELECT id INTO optical_id FROM medical_categories WHERE code = 'CAT-COV-EYE-OPTICAL';
    IF EXISTS (SELECT 1 FROM medical_categories
               WHERE code IN ('CAT-DENT-PROSTHO','CAT-DENT-ORTHO','CAT-DENT-IMPLANT')
                 AND parent_id IS NOT NULL AND parent_id <> advanced_id) THEN
        RAISE EXCEPTION 'Advanced dental categories already have a different parent';
    END IF;
    IF EXISTS (SELECT 1 FROM medical_categories
               WHERE code IN ('CAT-EYE-EXAM','CAT-OPT')
                 AND parent_id IS NOT NULL AND parent_id <> optical_id) THEN
        RAISE EXCEPTION 'Eye/optical categories already have a different parent';
    END IF;
    UPDATE medical_categories SET parent_id = advanced_id, updated_at = NOW()
    WHERE code IN ('CAT-DENT-PROSTHO','CAT-DENT-ORTHO','CAT-DENT-IMPLANT');
    UPDATE medical_categories SET parent_id = optical_id, updated_at = NOW()
    WHERE code IN ('CAT-EYE-EXAM','CAT-OPT');
END $$;

ALTER TABLE price_list_classification_items
    ADD COLUMN source_classification VARCHAR(255),
    ADD COLUMN claim_context_code VARCHAR(60) REFERENCES claim_contexts(code);

ALTER TABLE benefit_policy_rules ADD COLUMN claim_context_code VARCHAR(60);
UPDATE benefit_policy_rules
SET claim_context_code = CASE WHEN encounter_type = 'INPATIENT' THEN 'INPATIENT' ELSE 'OUTPATIENT' END;
ALTER TABLE benefit_policy_rules ALTER COLUMN claim_context_code SET NOT NULL;
ALTER TABLE benefit_policy_rules
    ADD CONSTRAINT fk_bpr_claim_context FOREIGN KEY (claim_context_code) REFERENCES claim_contexts(code);

ALTER TABLE claims ADD COLUMN claim_context_code VARCHAR(60);
UPDATE claims
SET claim_context_code = CASE WHEN encounter_type = 'INPATIENT' THEN 'INPATIENT' ELSE 'OUTPATIENT' END;
ALTER TABLE claims ALTER COLUMN claim_context_code SET NOT NULL;
ALTER TABLE claims
    ADD CONSTRAINT fk_claim_context FOREIGN KEY (claim_context_code) REFERENCES claim_contexts(code);

ALTER TABLE provider_contract_pricing_items ADD COLUMN claim_context_code VARCHAR(60);
UPDATE provider_contract_pricing_items
SET claim_context_code = CASE
    WHEN encounter_type = 'INPATIENT' THEN 'INPATIENT'
    WHEN encounter_type = 'OUTPATIENT' THEN 'OUTPATIENT'
    ELSE NULL END;
ALTER TABLE provider_contract_pricing_items
    ADD CONSTRAINT fk_pricing_item_claim_context FOREIGN KEY (claim_context_code) REFERENCES claim_contexts(code);

COMMENT ON COLUMN price_list_classification_items.source_classification IS
    'Provider classification preserved verbatim; matching uses a separate alias row.';
COMMENT ON COLUMN price_list_classification_items.claim_context_code IS
    'Data-driven claim context resolved from the source alias or confirmed by a reviewer.';
