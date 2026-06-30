-- 1. Add new columns to provider_contract_pricing_items table
ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS encounter_type VARCHAR(20) CHECK (encounter_type IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','ANY')),
    ADD COLUMN IF NOT EXISTS requires_review BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS review_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS classification_status VARCHAR(20) DEFAULT 'AUTO' CHECK (classification_status IN ('AUTO','MANUAL','PENDING_REVIEW','APPROVED','REJECTED')),
    ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(10) DEFAULT 'LOW' CHECK (confidence_level IN ('HIGH','MEDIUM','LOW')),
    ADD COLUMN IF NOT EXISTS classification_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS approved_by BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS imported_main_category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS imported_sub_category VARCHAR(255);

-- 2. Create the mapping table
CREATE TABLE IF NOT EXISTS service_specialty_insurance_map (
    id                       BIGSERIAL PRIMARY KEY,
    source_specialty_name_ar VARCHAR(255),
    source_specialty_name_en VARCHAR(255),
    keyword_patterns         TEXT,         -- JSON array of regex/keyword patterns
    match_field              VARCHAR(50) DEFAULT 'BOTH',
    insurance_category_code  VARCHAR(50) NOT NULL,
    default_encounter_type   VARCHAR(20) DEFAULT 'INPATIENT',
    requires_review          BOOLEAN DEFAULT false,
    review_reason            VARCHAR(500),
    priority                 INTEGER DEFAULT 100,
    confidence_level         VARCHAR(10) DEFAULT 'HIGH',
    provider_id              BIGINT REFERENCES providers(id),
    contract_id              BIGINT REFERENCES provider_contracts(id),
    is_active                BOOLEAN DEFAULT true,
    created_by               BIGINT REFERENCES users(id),
    created_at               TIMESTAMP DEFAULT NOW()
);

-- 3. Seed initial standard data
INSERT INTO service_specialty_insurance_map 
(source_specialty_name_ar, keyword_patterns, insurance_category_code, default_encounter_type, confidence_level, priority)
VALUES
-- العظام والمفاصل
('جراحة العظام', '["عظام","مفاصل","كسر","رباط","عمود فقري","ركبة","ورك"]', 'CAT001', 'INPATIENT', 'HIGH', 10),
-- القلب والقسطرة
('جراحة القلب', '["قلب","قسطرة","شريان تاجي","صمام","أورطا"]', 'CAT002', 'INPATIENT', 'HIGH', 10),
-- الولادة
('النساء وولادة', '["ولادة","قيصرية","حمل","أمومة","مشيمة"]', 'CAT021', 'INPATIENT', 'HIGH', 10),
-- الرنين والمقطعي
(NULL, '["رنين","MRI","CT scan","مقطعي","طبقي"]', 'CAT024', 'OUTPATIENT', 'HIGH', 5),
-- الأشعة والتحاليل
(NULL, '["أشعة سينية","تحاليل","مختبر","CBC","PCR"]', 'CAT023', 'OUTPATIENT', 'HIGH', 5),
-- كشف العيون (outpatient فقط)
('أمراض العيون', '["كشف عيون","فحص نظر","قياس نظر"]', 'CAT029', 'OUTPATIENT', 'MEDIUM', 20),
-- جراحة عيون (inpatient)
('أمراض العيون', '["عملية","جراحة","استئصال"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'HIGH', 15),
-- الأسنان الروتيني
('الأسنان', '["حشو","خلع","تنظيف","كشف أسنان"]', 'CAT028', 'OUTPATIENT', 'HIGH', 10),
-- الأسنان التجميلي
('الأسنان', '["زراعة","تقويم","تركيب","تاج","جسر"]', 'CAT031', 'OUTPATIENT', 'HIGH', 10),
-- تجميل — يحتاج مراجعة دائماً
('جراحة التجميل', '["تجميل","شفط دهون","رفع","تكبير"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'MEDIUM', 30),
-- ENT
('أنف وأذن وحنجرة', '["لوزتين","أذن","أنف","حنجرة","خشاء"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'HIGH', 10),
-- المسالك
('جراحة المسالك التناسلية', '["مسالك","كلى","مثانة","بروستاتا","حصوة"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'HIGH', 10),
-- المخ والأعصاب
('جراحة المخ والأعصاب', '["مخ","أعصاب","دماغ","ورم عصبي","عمود"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'HIGH', 10),
-- العقم — يحتاج مراجعة دائماً
('العقم والخصوبة', '["عقم","خصوبة","حقن مجهري","أطفال أنابيب"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'LOW', 50),
-- التخدير — يُلحق بالعملية
('التخدير', '["تخدير","بنج","مخدر"]', 'SUB-INPAT-GENERAL', 'INPATIENT', 'LOW', 50),
-- الجراحة العامة الافتراضية
('الجراحة العامة', NULL, 'SUB-INPAT-GENERAL', 'INPATIENT', 'HIGH', 90),
-- Wildcard الإيواء الافتراضي
(NULL, NULL, 'SUB-INPAT-GENERAL', 'INPATIENT', 'LOW', 999)
ON CONFLICT DO NOTHING;
