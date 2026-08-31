INSERT INTO claim_contexts(code, name_ar, base_encounter_type, active, display_order)
VALUES
    ('FULL_COVERAGE', 'تغطية كاملة', 'ANY', true, 30),
    ('PHARMACY', 'أدوية وصيدلة', 'OUTPATIENT', true, 40),
    ('MATERNITY', 'ولادة وحمل', 'INPATIENT', true, 50),
    ('PREGNANCY_COMPLICATIONS', 'مضاعفات الحمل', 'INPATIENT', true, 60)
ON CONFLICT (code) DO UPDATE SET
    name_ar = EXCLUDED.name_ar,
    base_encounter_type = EXCLUDED.base_encounter_type,
    active = true,
    display_order = EXCLUDED.display_order;
