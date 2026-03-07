
 -- ============================================================================
-- V89: Seed Clean Unified Medical Services
-- ============================================================================
-- Source   : Normalized & deduplicated from V13 reference dictionary.
-- Rules    :
--   • No category/sub_category columns — uses specialty_id FK only.
--   • No pricing data.
--   • All rows: is_master=TRUE, active=TRUE, status='ACTIVE'.
--   • Idempotent: WHERE NOT EXISTS on LOWER(TRIM(name_ar)).
--   • code generated from medical_service_seq.
--   • name (NOT NULL) = COALESCE(name_ar, name_en).
-- Target DB version: v88
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION 0: Ensure id sequence is ahead of existing max id
-- ─────────────────────────────────────────────────────────────────────────────
SELECT setval(
    'medical_services_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM medical_services), 1),
    true
);

-- ─────────────────────────────────────────────────────────────────────────────
-- INSERT all master services
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO medical_services
    (code, name, name_ar, name_en, specialty_id, is_master, active, status)
SELECT
    'MED-' || nextval('medical_service_seq')::text,
    COALESCE(NULLIF(TRIM(v.name_ar), ''), TRIM(v.name_en))                          AS name,
    NULLIF(TRIM(v.name_ar), '')                                                       AS name_ar,
    NULLIF(TRIM(v.name_en), '')                                                       AS name_en,
    (SELECT id FROM medical_specialties WHERE code = v.spec_code AND deleted = FALSE) AS specialty_id,
    TRUE   AS is_master,
    TRUE   AS active,
    'ACTIVE' AS status
FROM (VALUES

-- ═══════════════════════════════════════════════
-- SP-ACCOMM : خدمات الإيواء
-- ═══════════════════════════════════════════════
    ('إقامة في غرفة عادية / اليوم',              'Regular Ward Stay / Day',                 'SP-ACCOMM'),
    ('إقامة في غرفة مزدوجة / اليوم',             'Double Room Stay / Day',                  'SP-ACCOMM'),
    ('إقامة في غرفة مشتركة / اليوم',             'Shared Room Stay / Day',                  'SP-ACCOMM'),
    ('إقامة بالعناية المركزة / اليوم',            'ICU Stay / Day',                          'SP-ACCOMM'),
    ('إقامة في الحضانة / اليوم',                  'Nursery Stay / Day',                      'SP-ACCOMM'),
    ('إقامة في وحدة العناية الخاصة / اليوم',     'Special Care Unit Stay / Day',            'SP-ACCOMM'),

-- ═══════════════════════════════════════════════
-- SP-ICU : خدمات الرعاية بالعناية المركزة
-- ═══════════════════════════════════════════════
    ('جهاز ضخ الأدوية',                          'Infusion Pump',                           'SP-ICU'),
    ('مراقبة مستمرة للمريض',                     'Continuous Patient Monitoring',           'SP-ICU'),
    ('تركيب أنبوب أنفي معدي بالعناية المركزة',   'NGT Insertion (ICU)',                     'SP-ICU'),
    ('4-6 غيار تمريض / اليوم',                   '4-6 Nursing Dressing / Day',              'SP-ICU'),
    ('تركيب أنبوب صدر بالعناية المركزة',         'Chest Tube Insertion (ICU)',              'SP-ICU'),
    ('سحب إفرازات بالعناية المركزة',             'Tracheal Suctioning (ICU)',               'SP-ICU'),
    ('DOD مرور مناوب العناية الفائقة',           'DOD ICU Round',                          'SP-ICU'),

-- ═══════════════════════════════════════════════
-- SP-EMERG : الخدمات بأقسام الإيواء والطوارئ
-- ═══════════════════════════════════════════════
    ('تهوية ميكانيكية / اليوم',                  'Mechanical Ventilation / Day',            'SP-EMERG'),
    ('تنبيب القصبة الهوائية',                    'Endotracheal Intubation',                 'SP-EMERG'),
    ('إنعاش قلبي رئوي',                          'Cardiopulmonary Resuscitation (CPR)',      'SP-EMERG'),
    ('تخطيط قلب في قسم الطوارئ',                 'ECG for Doctor (Emergency)',              'SP-EMERG'),
    ('مجموعة الوريد المركزي',                    'Central Venous Set',                      'SP-EMERG'),
    ('بخاخة تنفسية',                             'Nebulizer',                               'SP-EMERG'),
    ('إدخال قنية وريدية',                        'Canula Insertion',                        'SP-EMERG'),
    ('رعاية وتنظيف حروق عميقة / اليوم',         'Debridement and Care of Deep Burns / Day','SP-EMERG'),
    ('إدخال قسطرة فوق عانية بالطوارئ',          'Suprapubic Catheter Insertion (ER)',      'SP-EMERG'),
    ('Endotracheal intubation (Emergency)',       'Endotracheal Intubation Emergency',       'SP-EMERG'),
    ('تصوير بالأشعة بالجهاز المتنقل',            'Portable X-Ray',                          'SP-EMERG'),
    ('تلفزيون بالجهاز المتنقل',                  'Portable Ultrasound',                     'SP-EMERG'),
    ('CTG',                                       'CTG (Cardiotocography)',                  'SP-EMERG'),
    ('إدخال قسطرة بولية بالطوارئ',               'IFC Insertion (Emergency)',               'SP-EMERG'),
    ('غسيل معدة',                                'Gastric Lavage',                          'SP-EMERG'),

-- ═══════════════════════════════════════════════
-- SP-NURSING : خدمات الرعاية الطبية العامة
-- ═══════════════════════════════════════════════
    ('أبرة بدون ألم',                            'Painless Injection',                      'SP-NURSING'),
    ('تركيب أنبوب أنفي معدي',                   'NGT Insertion (Nursing)',                 'SP-NURSING'),
    ('قياس ضغط الدم',                           'Measuring Blood Pressure',                'SP-NURSING'),
    ('جلسة بخار',                                'Steam Session',                           'SP-NURSING'),
    ('غيار جرح مستوى أول',                      'Wound Dressing Grade 1',                  'SP-NURSING'),
    ('غيار جرح مستوى ثان',                      'Wound Dressing Grade 2',                  'SP-NURSING'),
    ('حقنة خاصة بالنساء والولادة',               'Special Injection for Obstetrics',        'SP-NURSING'),
    ('حقن في الوريد دون جهاز',                  'IV Injection without Device',             'SP-NURSING'),
    ('حقنة في الوريد مع كانيولا ومحاليل',       'IV Drip with Cannula and Fluids',         'SP-NURSING'),
    ('حقنة تحت الجلد',                           'Subcutaneous Injection',                  'SP-NURSING'),
    ('حقنة عضلية',                               'Intramuscular Injection',                 'SP-NURSING'),
    ('سحب دم للتحليل',                           'Venipuncture / Blood Draw',               'SP-NURSING'),
    ('تركيب جبيرة جبسية',                        'Plaster Cast Application',                'SP-NURSING'),
    ('ورقة خروج',                               'Discharge Sheet',                         'SP-NURSING'),
    ('تخطيط قلب (تمريض)',                        'ECG (Nursing)',                           'SP-NURSING'),
    ('تنظيف مسمار القدم درجة أولى',             'Foot Nail Cleaning Grade 1',              'SP-NURSING'),
    ('تنظيف مسمار القدم درجة ثانية',            'Foot Nail Cleaning Grade 2',              'SP-NURSING'),
    ('حقن دوالي A',                             'Varicose Vein Injection A',               'SP-NURSING'),
    ('حقن دوالي B',                             'Varicose Vein Injection B',               'SP-NURSING'),
    ('اختبار حساسية وتحديد المسببات',           'Allergy Testing and Identification',      'SP-NURSING'),
    ('سحب مفصلي',                               'Joint Aspiration (Nursing)',              'SP-NURSING'),
    ('تطهير بالحقنة الشرجية',                   'Enema',                                   'SP-NURSING'),
    ('مستلزمات يومية',                           'Daily Supplies',                          'SP-NURSING'),
    ('أكسجين / اليوم',                          'Oxygen Flow / Day',                       'SP-NURSING'),
    ('خدمات إضافية',                            'Additional Services',                     'SP-NURSING'),
    ('تركيب أو إزالة لولب',                     'IUD Insertion / Removal',                'SP-NURSING'),

-- ═══════════════════════════════════════════════
-- SP-ANES : التخدير
-- ═══════════════════════════════════════════════
    ('تخدير عام مستوى أول',                     'General Anesthesia Level 1',              'SP-ANES'),
    ('تخدير عام مستوى ثانٍ',                    'General Anesthesia Level 2',              'SP-ANES'),
    ('تخدير عام عالٍ',                           'High General Anesthesia',                 'SP-ANES'),
    ('قيمة مواد تخدير مستوى بسيط',             'Anesthesia Supplies - Basic',             'SP-ANES'),
    ('قيمة مواد تخدير مستوى متوسط',            'Anesthesia Supplies - Intermediate',      'SP-ANES'),
    ('قيمة مواد تخدير مستوى متقدم',            'Anesthesia Supplies - Advanced',          'SP-ANES'),
    ('حقنة تخدير أطراف علوية لتخفيف الألم',   'Upper Extremity Nerve Block',             'SP-ANES'),
    ('PCA',                                       'Patient-Controlled Analgesia',           'SP-ANES'),
    ('تسريب ليدوكايين',                          'Lidocaine Infusion',                      'SP-ANES'),
    ('حقنة ظهر - جانب واحد',                    'Epidural Injection - One Side',          'SP-ANES'),
    ('حقنة ظهر - جانبين',                       'Epidural Injection - Both Sides',        'SP-ANES'),
    ('تخدير ألم ظهر',                           'Epidural for Back Pain',                  'SP-ANES'),
    ('حقنة موجهة بالتنظير الفلوري فئة أ',      'Fluoroscopy-Guided Injection Class A',   'SP-ANES'),
    ('حقنة موجهة بالتنظير الفلوري فئة ب',      'Fluoroscopy-Guided Injection Class B',   'SP-ANES'),
    ('حقنة موجهة بالتنظير الفلوري فئة ج',      'Fluoroscopy-Guided Injection Class C',   'SP-ANES'),
    ('حقنة موجهة بالتنظير الفلوري فئة د',      'Fluoroscopy-Guided Injection Class D',   'SP-ANES'),
    ('حقنة موجهة بالتنظير الفلوري فئة هـ',     'Fluoroscopy-Guided Injection Class E',   'SP-ANES'),
    ('حقنة في الركبة بحقن ثابت',               'Knee Injection (Anesthesia)',             'SP-ANES'),
    ('تخدير رنين مغناطيسي',                     'MRI Sedation',                            'SP-ANES'),
    ('تسريب دواء Breast Ca',                    'Breast Ca. Injection',                   'SP-ANES'),

-- ═══════════════════════════════════════════════
-- SP-LAB : المختبر والتحاليل الطبية
-- ═══════════════════════════════════════════════
    ('تعداد الدم الشامل CBC',                   'Complete Blood Count (CBC)',              'SP-LAB'),
    ('سرعة الترسيب ESR',                        'Erythrocyte Sedimentation Rate (ESR)',    'SP-LAB'),
    ('فصيلة الدم',                               'Blood Group & Rh',                        'SP-LAB'),
    ('AMYLASE (AMY)',                             'Amylase',                                 'SP-LAB'),
    ('AMYLASE URINE (AMY U)',                     'Amylase Urine',                           'SP-LAB'),
    ('DNA',                                       'DNA Test',                                'SP-LAB'),
    ('ANTI-ENDOMYSIAL IgA (AENDOA)',              'Anti-Endomysial IgA',                    'SP-LAB'),
    ('GOT (AST)',                                 'AST / GOT',                               'SP-LAB'),
    ('BILIRUBIN (BILI)',                          'Bilirubin Total',                         'SP-LAB'),
    ('CA 19-9',                                   'CA 19-9 Tumor Marker',                   'SP-LAB'),
    ('CA Calcium',                               'Calcium',                                 'SP-LAB'),
    ('STOOL CALPROTECTIN (CALP)',                 'Stool Calprotectin',                     'SP-LAB'),
    ('CARBAMAZEPINE (CAR)',                       'Carbamazepine Level',                    'SP-LAB'),
    ('ESR',                                       'ESR (Lab)',                               'SP-LAB'),
    ('ESTRADIOL E2 FEMALE (E2F)',                 'Estradiol E2 (Female)',                  'SP-LAB'),
    ('ESTRADIOL E2 MALE (E2M)',                   'Estradiol E2 (Male)',                    'SP-LAB'),
    ('FERRITIN (FERR)',                           'Ferritin',                               'SP-LAB'),
    ('PROLACTIN',                                 'Prolactin',                               'SP-LAB'),
    ('PROTEIN TOTAL (TP)',                        'Total Protein',                           'SP-LAB'),
    ('PSA TOTAL (TPSA)',                          'PSA Total',                               'SP-LAB'),
    ('IGM TOXO (TOXO)',                           'Toxoplasma IgM',                          'SP-LAB'),
    ('TRIGLYCERIDES (TRI)',                       'Triglycerides',                           'SP-LAB'),
    ('NASAL SWAB (NASAL)',                        'Nasal Swab',                              'SP-LAB'),
    ('FLUID PERICARIDIAL',                        'Pericardial Fluid',                       'SP-LAB'),
    ('FLUID PLEURAL',                             'Pleural Fluid',                           'SP-LAB'),
    ('SYE SYNOVIAL FLUID (R/E)',                 'Synovial Fluid Examination',             'SP-LAB'),
    ('FT3 T3 Free',                              'Free T3',                                 'SP-LAB'),
    ('URINE ELECTROLYTE (U/EL)',                  'Urine Electrolyte',                       'SP-LAB'),
    ('CASA',                                      'CASA (Semen Analysis)',                  'SP-LAB'),
    ('H.Pylori Breath Test (HPU)',                'H. Pylori Breath Test',                  'SP-LAB'),
    ('HOMA-IR Insulin Resistance',               'Insulin Resistance (HOMA-IR)',            'SP-LAB'),
    ('FACTOR II (F2)',                            'Coagulation Factor II',                  'SP-LAB'),
    ('FACTOR VII (F7)',                           'Coagulation Factor VII',                 'SP-LAB'),
    ('FACTOR X',                                  'Coagulation Factor X',                   'SP-LAB'),
    ('FACTOR XI (F11)',                           'Coagulation Factor XI',                  'SP-LAB'),
    ('FACTOR XII (F12)',                          'Coagulation Factor XII',                 'SP-LAB'),
    ('PROTEIN S (PROTS)',                         'Protein S',                               'SP-LAB'),
    ('PROTEIN C (PROTC)',                         'Protein C',                               'SP-LAB'),
    ('ACID FAST BACILLI (AFBS)',                  'Acid Fast Bacilli Stain Sputum',         'SP-LAB'),
    ('GRAM STAIN (G.S)',                          'Gram Stain',                              'SP-LAB'),
    ('STOOL CULTURE',                             'Stool Culture',                           'SP-LAB'),
    ('CSF CULTURE',                               'CSF Culture',                             'SP-LAB'),
    ('PAP SMEAR (C/PAP)',                         'Pap Smear',                               'SP-LAB'),
    ('FINE NEEDLE ASPIRATION (C/FNAC)',           'Fine Needle Aspiration Cytology',        'SP-LAB'),
    ('BRONCHIAL WASH (C/BRON)',                   'Bronchial Wash Cytology',                'SP-LAB'),
    ('PHENYTOIN LEVEL',                           'Phenytoin Level',                         'SP-LAB'),
    ('PHENOBARBITAL LEVEL',                       'Phenobarbital Level',                    'SP-LAB'),
    ('TACROLIMUS (FK506)',                        'Tacrolimus Level',                        'SP-LAB'),
    ('KEPPRA LEVEL',                              'Levetiracetam (Keppra) Level',           'SP-LAB'),
    ('CYCLOSPORIN LEVEL (F/CYCLOSPORIN)',         'Cyclosporin Level',                      'SP-LAB'),
    ('IGD (F/IGD)',                               'Immunoglobulin D (IgD)',                 'SP-LAB'),
    ('ALDOSTERONE LEVEL (F/ALDOSTERONE)',         'Aldosterone Level',                      'SP-LAB'),
    ('RENIN (F/RENIN)',                           'Renin Level',                             'SP-LAB'),
    ('LAMOTRIGINE LEVEL (F/LAMOTRIGINE)',         'Lamotrigine Level',                      'SP-LAB'),
    ('GASTRIC-PARIETAL CELL (F/GAST)',            'Gastric Parietal Cell Antibody',         'SP-LAB'),
    ('INTRINSIC FACTOR (F/INTRINSIC F)',          'Intrinsic Factor Antibody',              'SP-LAB'),
    ('IL6 INTERLEUKIN (F/INTER)',                 'Interleukin-6 (IL-6)',                   'SP-LAB'),
    ('H.PYLORI STOOL',                            'H. Pylori Stool Antigen',                'SP-LAB'),
    ('H.PYLORI BY RAPID (HPR)',                   'H. Pylori Rapid Test',                   'SP-LAB'),
    ('تحاليل معمل',                              'General Lab Tests',                       'SP-LAB'),
    ('Inhalation Panel Test',                     'Inhalation Panel Allergy Test',          'SP-LAB'),
    ('تحليل كيميائي شامل',                       'Comprehensive Metabolic Panel',          'SP-LAB'),
    ('CBC مع صورة دم تفصيلية',                  'CBC with Differential',                   'SP-LAB'),

-- ═══════════════════════════════════════════════
-- SP-PATH : علم الأنسجة (Histopathology)
-- ═══════════════════════════════════════════════
    ('رحم كامل (H/TAH)',                         'Total Abdominal Hysterectomy Specimen',   'SP-PATH'),
    ('أورام رحمية (H/UTEM)',                     'Uterine Myoma Specimen',                 'SP-PATH'),
    ('استئصال الطحال (H/SPL)',                   'Splenectomy Specimen',                   'SP-PATH'),
    ('استئصال الزائدة (H/APPN)',                 'Appendectomy Specimen',                  'SP-PATH'),
    ('استئصال الكلية (H/NEPH)',                  'Nephrectomy Specimen',                   'SP-PATH'),
    ('كيس مبيض يميني (H/OVA-RT)',                'Right Ovarian Cyst Specimen',            'SP-PATH'),
    ('كيس مبيض يساري (H/OVA-LT)',                'Left Ovarian Cyst Specimen',             'SP-PATH'),
    ('أورام الأنف (H/NAS)',                      'Nasal Polyp Specimen',                   'SP-PATH'),
    ('استئصال الثدي (H/MAS)',                    'Mastectomy Specimen',                    'SP-PATH'),
    ('POLYP SPECIMEN (H/POL)',                    'Polyp Specimen',                         'SP-PATH'),
    ('PERIANAL SPECIMEN (H/PER)',                 'Perianal Specimen',                      'SP-PATH'),
    ('DRAIN SPECIMEN (C/DR)',                     'Drain Cytology',                         'SP-PATH'),

-- ═══════════════════════════════════════════════
-- SP-RAD : الأشعة والتصوير الطبي
-- ═══════════════════════════════════════════════
    ('صورة أشعة صدر AP أو LAT',                 'Chest X-Ray PA or LAT',                  'SP-RAD'),
    ('صورة أشعة صدر PA و LAT',                  'Chest X-Ray PA & LAT',                   'SP-RAD'),
    ('صورة أشعة ركبة AP أو LAT',                'Knee X-Ray AP or LAT',                   'SP-RAD'),
    ('صورة أشعة ركبة AP و LAT',                 'Knee X-Ray AP & LAT',                    'SP-RAD'),
    ('صورة أشعة الجمجمة أو الجيوب',             'Skull / PNS X-Ray',                      'SP-RAD'),
    ('صورة أشعة العمود الفقري العنقي AP أو LAT','Cervical Spine X-Ray AP or LAT',         'SP-RAD'),
    ('صورة أشعة العمود الفقري العنقي AP و LAT', 'Cervical Spine X-Ray AP & LAT',          'SP-RAD'),
    ('صورة أشعة العمود الفقري القطني AP أو LAT','Lumbar Spine X-Ray AP or LAT',           'SP-RAD'),
    ('صورة أشعة العمود الفقري القطني AP و LAT', 'Lumbar Spine X-Ray AP & LAT',            'SP-RAD'),
    ('صورة أشعة اليد AP أو LAT',                'Hand X-Ray AP or LAT',                   'SP-RAD'),
    ('صورة أشعة اليد AP و LAT',                 'Hand X-Ray AP & LAT',                    'SP-RAD'),
    ('صورة أشعة الساعد AP أو LAT',              'Forearm X-Ray AP or LAT',                'SP-RAD'),
    ('صورة أشعة الساعد AP و LAT',               'Forearm X-Ray AP & LAT',                 'SP-RAD'),
    ('صورة أشعة الكوع AP أو LAT',               'Elbow X-Ray AP or LAT',                  'SP-RAD'),
    ('صورة أشعة الكتف AP أو LAT',               'Shoulder X-Ray AP or LAT',               'SP-RAD'),
    ('صورة أشعة الورك AP و LAT',                'Hip X-Ray AP & LAT',                     'SP-RAD'),
    ('صورة أشعة الفخذ AP أو LAT',               'Femur X-Ray AP or LAT',                  'SP-RAD'),
    ('صورة أشعة الساق AP أو LAT',               'Leg X-Ray AP or LAT',                    'SP-RAD'),
    ('صورة أشعة الكاحل AP أو LAT',              'Ankle X-Ray AP or LAT',                  'SP-RAD'),
    ('صورة أشعة القدم AP أو LAT',               'Foot X-Ray AP or LAT',                   'SP-RAD'),
    ('صورة أشعة الرسغ',                          'Wrist X-Ray',                            'SP-RAD'),
    ('صورة أشعة KUB',                            'KUB X-Ray',                              'SP-RAD'),
    ('صورة أشعة عظمة الأنف',                    'Nasal Bone X-Ray',                       'SP-RAD'),
    ('صورة تلفزيون بطن',                         'Abdominal Ultrasound',                   'SP-RAD'),
    ('صورة تلفزيون حوض',                         'Pelvic Ultrasound',                      'SP-RAD'),
    ('صورة تلفزيون رقبة',                        'Neck Ultrasound',                        'SP-RAD'),
    ('صورة تلفزيون كلى',                         'Renal Ultrasound',                       'SP-RAD'),
    ('تلفزيون دوبلر كلتا الشريانين السباتيين', 'Carotid Doppler Both Sides',             'SP-RAD'),
    ('تلفزيون دوبلر الأطراف السفلية وريدي',    'LL Venous Doppler',                      'SP-RAD'),
    ('تلفزيون دوبلر الأطراف السفلية شرياني',   'LL Arterial Doppler',                    'SP-RAD'),
    ('تلفزيون شرجي',                             'Rectal Ultrasound',                      'SP-RAD'),
    ('CT دماغ',                                  'CT Brain',                               'SP-RAD'),
    ('CT الجيوب الأنفية / أذن / محجر العين',   'CT PNS / Ear / Orbit',                   'SP-RAD'),
    ('CT بطن',                                   'CT Abdomen',                             'SP-RAD'),
    ('CT حوض',                                   'CT Pelvis',                              'SP-RAD'),
    ('CT بطن وحوض',                              'CT Abdomen & Pelvis',                    'SP-RAD'),
    ('استخدام صبغة أثناء فحص CT',               'CT Contrast Dye',                         'SP-RAD'),
    ('تخدير عام أثناء فحص CT',                  'CT General Anesthesia',                  'SP-RAD'),
    ('رنين مغناطيسي دماغ',                       'MRI Brain',                              'SP-RAD'),
    ('رنين مغناطيسي عمود فقري',                  'MRI Spine',                              'SP-RAD'),
    ('رنين مغناطيسي عمود فقري عنقي',            'MRI Cervical Spine',                     'SP-RAD'),
    ('رنين مغناطيسي عمود فقري قطني',            'MRI Lumbar Spine',                       'SP-RAD'),
    ('رنين مغناطيسي بطن وحوض',                  'MRI Abdomen & Pelvis',                   'SP-RAD'),
    ('رنين مغناطيسي ركبة',                       'MRI Knee',                               'SP-RAD'),
    ('رنين مغناطيسي كتف',                        'MRI Shoulder',                           'SP-RAD'),
    ('رنين مغناطيسي كامل العمود الفقري',        'MRI Complete Spine',                     'SP-RAD'),
    ('استخدام صبغة أثناء فحص الرنين',           'MRI Contrast Dye',                        'SP-RAD'),
    ('استخدام تخدير أثناء فحص الرنين',          'MRI General Anesthesia',                 'SP-RAD'),
    ('صورة رنين للدماغ والعمود الفقري',         'MRI Brain & Spine',                      'SP-RAD'),
    ('SIALOGRAPHY',                               'Sialography',                            'SP-RAD'),
    ('U.S.S دوبلر الشرايين الكلوية',             'Doppler of Renal Arteries',              'SP-RAD'),
    ('صورة أشعة في الصدر بإجراء جراحي',         'Chest CT Interventional Procedure',     'SP-RAD'),
    ('فيلم إضافي للرنين',                        'Extra MRI Film',                         'SP-RAD'),
    ('DEXA - قياس كثافة العظام',                'DEXA Bone Density Scan',                 'SP-RAD'),

-- ═══════════════════════════════════════════════
-- SP-CARD-DIAG : التشخيص القلبي
-- ═══════════════════════════════════════════════
    ('تخطيط قلب كهربائي ECG',                   'Electrocardiogram (ECG)',                'SP-CARD-DIAG'),
    ('تخطيط صوتي للقلب',                         'Echocardiogram',                         'SP-CARD-DIAG'),
    ('قسطرة تشخيصية للقلب',                      'Diagnostic Cardiac Catheterization',    'SP-CARD-DIAG'),
    ('قسطرة تشخيصية قلبية بعد CABG',            'Post-CABG Coronary Angiography',         'SP-CARD-DIAG'),
    ('قسطرة تشخيصية محيطية',                    'Peripheral Angiography',                 'SP-CARD-DIAG'),
    ('قسطرة كاروتيد',                            'Carotid Angiography',                    'SP-CARD-DIAG'),
    ('قسطرة كلوية',                              'Renal Angiography',                     'SP-CARD-DIAG'),
    ('هولتر قلب',                                'Holter Monitor',                         'SP-CARD-DIAG'),
    ('اختبار جهد',                               'Exercise Stress Test',                   'SP-CARD-DIAG'),

-- ═══════════════════════════════════════════════
-- SP-CARDIO : أمراض القلب (Outpatient)
-- ═══════════════════════════════════════════════
    ('تركيب منشط عضلة القلب - غرفة واحدة',     'Pacemaker Insertion - Single Chamber',   'SP-CARDIO'),
    ('تركيب منشط عضلة القلب - غرفتان',         'Pacemaker Insertion - Dual Chamber',     'SP-CARDIO'),
    ('تركيب منشط عضلة القلب - ثلاث غرف',       'Pacemaker Insertion - Three Chamber',    'SP-CARDIO'),

-- ═══════════════════════════════════════════════
-- SP-CARDIO-SURG : جراحة القلب والصدر
-- ═══════════════════════════════════════════════
    ('تركيب دعامة قلبية - وعاء واحد',          'Single Vessel Angioplasty + Stent',      'SP-CARDIO-SURG'),
    ('تركيب دعامة ثنائية تفرع',                'Bifurcation Angioplasty + Stent',        'SP-CARDIO-SURG'),
    ('تركيب دعامات متعددة الأوعية',             'Multi-vessel Angioplasty',               'SP-CARDIO-SURG'),
    ('استئصال جزء من الرئة وقصبته',            'Excision of Lung and Bronchus',          'SP-CARDIO-SURG'),
    ('إزالة أنبوب صدر',                         'Chest Tube Removal',                     'SP-CARDIO-SURG'),
    ('تعفن صدر (Decortications)',               'Decortication',                          'SP-CARDIO-SURG'),
    ('كيس هيداتيد بسيط',                        'Simple Hydatid Cyst',                    'SP-CARDIO-SURG'),
    ('إعادة تشكيل القصبة الهوائية',             'Repair / Reconstruction of Trachea',    'SP-CARDIO-SURG'),
    ('جسم غريب في جدار الصدر',                  'Foreign Body in Chest Wall',             'SP-CARDIO-SURG'),

-- ═══════════════════════════════════════════════
-- SP-GEN-SURG : الجراحة العامة
-- ═══════════════════════════════════════════════
    ('استئصال الزائدة الدودية (مفتوحة)',         'Classical Appendectomy',                 'SP-GEN-SURG'),
    ('استئصال الزائدة بالمنظار',                'Laparoscopic Appendectomy',              'SP-GEN-SURG'),
    ('استئصال الزائدة المثقوبة',                'Perforated Appendectomy',               'SP-GEN-SURG'),
    ('فتق أربي بسيط',                           'Inguinal Hernia Repair',                 'SP-GEN-SURG'),
    ('فتق سري',                                  'Umbilical Hernia Repair',                'SP-GEN-SURG'),
    ('فتق انسدادي',                             'Obstructed Hernia',                       'SP-GEN-SURG'),
    ('استئصال المرارة بالمنظار',                'Laparoscopic Cholecystectomy',           'SP-GEN-SURG'),
    ('استئصال المرارة المفتوحة',                'Open Cholecystectomy',                   'SP-GEN-SURG'),
    ('استئصال الطحال بالمنظار',                'Laparoscopic Splenectomy',               'SP-GEN-SURG'),
    ('استئصال جزء من البنكرياس',               'Partial Pancreatectomy',                 'SP-GEN-SURG'),
    ('تصريف كيس البنكرياس',                    'Internal Drainage of Pancreatic Cyst',  'SP-GEN-SURG'),
    ('فتح المعدة (Laparotomy)',                 'Laparotomy / Exploratory',               'SP-GEN-SURG'),
    ('بضع البطن بالمنظار',                      'Laparoscopic Laparotomy',               'SP-GEN-SURG'),
    ('شق البطن للبحث',                          'Abdominal Paracentesis',                 'SP-GEN-SURG'),
    ('استئصال البواسير',                         'Hemorrhoidectomy',                       'SP-GEN-SURG'),
    ('ربط البواسير بالمنظار',                   'Endoscopic Hemorrhoid Ligation',         'SP-GEN-SURG'),
    ('ناسور شرجي',                               'Fistula in Ano',                         'SP-GEN-SURG'),
    ('ناسور شرجي معقد',                          'Complicated Fistula in Ano',             'SP-GEN-SURG'),
    ('قولون صناعي (Colostomy)',                 'Colostomy',                               'SP-GEN-SURG'),
    ('فغر الدقاق (Ileostomy)',                  'Ileostomy',                               'SP-GEN-SURG'),
    ('استئصال الغدة الدرقية (جزئي)',            'Partial Thyroidectomy',                  'SP-GEN-SURG'),
    ('استئصال الغدة الدرقية (كامل)',            'Total Thyroidectomy',                    'SP-GEN-SURG'),
    ('استئصال جزء من الغدة الكظرية',           'Partial Adrenalectomy',                  'SP-GEN-SURG'),
    ('استئصال الغدة الجار درقية',              'Parathyroidectomy',                       'SP-GEN-SURG'),
    ('استئصال الثدي (تشخيصي)',                  'Diagnostic Procedure on Breast',         'SP-GEN-SURG'),
    ('استئصال سرطان الثدي',                     'Mastectomy (Malignant)',                  'SP-GEN-SURG'),
    ('ربط وريد',                                 'Ligation of Vein',                       'SP-GEN-SURG'),
    ('قسطرة وريدية للكلى',                      'Venous Catheterization for Renal Dialysis','SP-GEN-SURG'),
    ('قسطرة سرية / وريدية',                     'Venous / Umbilical Catheterization',     'SP-GEN-SURG'),
    ('استئصال ورم نسيج ضام واسع',              'Wide Excision of Connective Tissue Tumor','SP-GEN-SURG'),
    ('مفاغرة الصفراء أو قناة الصفراء',         'Anastomosis of Gall Bladder/Bile Duct', 'SP-GEN-SURG'),
    ('استئصال كيس',                             'Cyst Excision',                           'SP-GEN-SURG'),
    ('ولادة طبيعية',                            'Normal Delivery',                         'SP-GEN-SURG'),
    ('قيصرية أولى',                             'First Caesarean Section',                'SP-GEN-SURG'),
    ('قيصرية ثانية',                            'Second Caesarean Section',               'SP-GEN-SURG'),
    ('قيصرية ثالثة',                            'Third Caesarean Section',                'SP-GEN-SURG'),
    ('تكبير حجم المستقيم (Dilatation Anus)',    'Dilatation of Anus',                     'SP-GEN-SURG'),

-- ═══════════════════════════════════════════════
-- SP-ORTHO : جراحة العظام
-- ═══════════════════════════════════════════════
    ('إعادة تحديد مفصل صغير مفتوحة',           'Open Reduction of Small Joint',          'SP-ORTHO'),
    ('استبدال الورك الكامل',                    'Total Hip Replacement',                   'SP-ORTHO'),
    ('استبدال الركبة الكامل',                   'Total Knee Replacement',                 'SP-ORTHO'),
    ('تثبيت داخلي لكسر',                       'Internal Fixation of Fracture',          'SP-ORTHO'),
    ('إزالة مسمار تثبيت',                      'Implant / Nail Removal',                 'SP-ORTHO'),
    ('جبسة مقوسة',                              'Cast Application (Curved)',               'SP-ORTHO'),
    ('جبسة تحت الكوع',                         'Below Elbow Cast',                        'SP-ORTHO'),
    ('جبسة تحت الركبة',                        'Below Knee Cast',                         'SP-ORTHO'),
    ('جبسة ذراع كاملة',                        'Full Arm Cast',                           'SP-ORTHO'),
    ('جبسة كتف',                                'Shoulder Spica Cast',                    'SP-ORTHO'),
    ('جبسة ورك',                                'Hip Spica Cast',                         'SP-ORTHO'),
    ('تحت إيهام عام جبسة',                     'Cast under General Anesthesia',          'SP-ORTHO'),
    ('استئصال العمود الفقري مستوى واحد',       'Single Level Laminectomy',               'SP-ORTHO'),
    ('استئصال العمود الفقري مستويين',          'Two Level Laminectomy',                  'SP-ORTHO'),
    ('استئصال العمود الفقري ثلاثة مستويات',   'Three Level Laminectomy',                'SP-ORTHO'),
    ('استئصال قرص فقري مع توسع الثقبة مستوى واحد','Single Level Discectomy + Foraminotomy','SP-ORTHO'),
    ('استئصال قرص فقري مع توسع الثقبة مستويين', 'Two Level Discectomy + Foraminotomy', 'SP-ORTHO'),
    ('رأب الساق (Bipolar)',                     'Bipolar Hemiarthroplasty',               'SP-ORTHO'),
    ('حقن الركبة بالمادة الجيلاتينية',         'Knee Gel Injection',                     'SP-ORTHO'),
    ('فصل وحقن بلازما بمفصل الركبة',          'PRP Knee Injection',                     'SP-ORTHO'),
    ('استكشاف جرح وإصلاح عضلة',               'Wound Exploration and Muscle Repair',   'SP-ORTHO'),
    ('إعادة تحديد الورك المعقد بالأطفال',      'CDH Close Reduction Hip Spica',         'SP-ORTHO'),

-- ═══════════════════════════════════════════════
-- SP-NEURO-SURG : جراحة المخ والأعصاب
-- ═══════════════════════════════════════════════
    ('استئصال ورم دماغي',                       'Brain Tumor Excision',                   'SP-NEURO-SURG'),
    ('تصريف بطيني داخلي',                       'Ventriculoperitoneal Shunt Insertion',   'SP-NEURO-SURG'),
    ('تصريف بطيني خارجي',                      'External Ventricular Drain Insertion',   'SP-NEURO-SURG'),
    ('استئصال ورم شوكي - قطني',                'Spinal Tumor Excision - Lumbar',         'SP-NEURO-SURG'),
    ('استئصال ورم شوكي - ظهري',                'Spinal Tumor Excision - Dorsal',         'SP-NEURO-SURG'),
    ('تثبيت الكسر القطني بمسامير وقضبان',     'Posterior Lumbar Fracture Fixation',     'SP-NEURO-SURG'),
    ('حقن في غرفة العمليات العصبية',          'Injection inside O.T. (Neuro)',          'SP-NEURO-SURG'),
    ('تثبيت فتق سحائي',                        'Rupture Meningiocele Repair',            'SP-NEURO-SURG'),
    ('جراحة أدمة دماغية (Craniotomy)',         'Craniotomy',                             'SP-NEURO-SURG'),
    ('استئصال ورم دماغي للأطفال',             'Pediatric Brain Tumor Excision',         'SP-NEURO-SURG'),

-- ═══════════════════════════════════════════════
-- SP-NEURO-DIAG : التشخيص العصبي
-- ═══════════════════════════════════════════════
    ('EEG-EMG-EP',                               'EEG / EMG / Evoked Potentials',          'SP-NEURO-DIAG'),
    ('اختبار التنفس',                           'Pulmonary Function Test',                'SP-NEURO-DIAG'),
    ('NCV (قياس سرعة التوصيل العصبي)',          'Nerve Conduction Velocity (NCV)',        'SP-NEURO-DIAG'),
    ('تخطيط كهربائي للدماغ',                   'Electroencephalography (EEG)',           'SP-NEURO-DIAG'),

-- ═══════════════════════════════════════════════
-- SP-VASC : جراحة الأوعية الدموية
-- ═══════════════════════════════════════════════
    ('ليزر الدوالي',                            'Spider Veins Laser',                     'SP-VASC'),
    ('إجراءات الأوعية الدموية الرضحية',        'Vascular Trauma Procedure',              'SP-VASC'),
    ('قطع وإعادة مفاغرة وعاء دموي',           'Resection of Vessel with Anastomosis',   'SP-VASC'),
    ('استئصال بطانة الشريان (Endarterectomy)', 'Endarterectomy',                         'SP-VASC'),
    ('منظار الدوالي دراسة دوبلر',             'Venous Doppler Study',                   'SP-VASC'),
    ('حقن دوالي تجميلي',                       'Cosmetic Varicose Vein Injection',       'SP-VASC'),

-- ═══════════════════════════════════════════════
-- SP-UROL : جراحة المسالك البولية
-- ═══════════════════════════════════════════════
    ('منظار مثانة مع إدخال دعامة',             'Cystoscopy + DJ Stent Insertion',        'SP-UROL'),
    ('استئصال الكلية',                          'Nephrectomy',                             'SP-UROL'),
    ('استئصال كيس الخصية',                     'Hydrocelectomy',                          'SP-UROL'),
    ('إصلاح مجرى البول',                        'Meatotomy / Urethral Dilation',         'SP-UROL'),
    ('استئصال البروستاتا الجذري',               'Radical Prostatectomy',                  'SP-UROL'),
    ('قسطرة فوق عانية (في غرفة العمليات)',     'Suprapubic Catheterization (OT)',        'SP-UROL'),
    ('استئصال الحالب',                          'Ureterectomy',                            'SP-UROL'),
    ('زرع الحالب',                              'Implantation of Ureter',                 'SP-UROL'),
    ('رأب الحوض الكلوي (Pyeloplasty)',          'Pyeloplasty',                             'SP-UROL'),
    ('إنشاء نافورة بولية (Nephrostomy)',        'Nephrostomy',                             'SP-UROL'),
    ('منظار مثانة مع تحرير ضيق',              'Cystoscope with Stricture Release',      'SP-UROL'),
    ('منظار بولي مع إزالة حصوات',             'Ureteroscopy with Stone Removal',        'SP-UROL'),
    ('تثبيت الخصية (Orchidopexy)',             'Orchidopexy',                            'SP-UROL'),
    ('منظار مثانة مع إخلاء جلطة',            'Cystoscopy and Clot Evacuation',         'SP-UROL'),

-- ═══════════════════════════════════════════════
-- SP-PEDS-SURG : جراحة الأطفال
-- ═══════════════════════════════════════════════
    ('استئصال الزائدة التقليدية للأطفال',      'Classical Appendectomy (Pediatric)',     'SP-PEDS-SURG'),
    ('استئصال الزائدة المثقوبة للأطفال',       'Perforated Appendectomy (Pediatric)',   'SP-PEDS-SURG'),
    ('شق عضلة البواب (Pyloromyotomy)',          'Pyloromyotomy',                          'SP-PEDS-SURG'),
    ('انغلاف (Intussusception)',                'Intussusception Reduction',              'SP-PEDS-SURG'),
    ('استئصال خصية خارج الصفن',               'Orchidopexy (Pediatric)',               'SP-PEDS-SURG'),
    ('خزعة العقدة اللمفاوية للأطفال',          'Lymph Node Biopsy (Pediatric)',         'SP-PEDS-SURG'),
    ('خزعة المستقيم للأطفال',                  'Rectal Biopsy (Pediatric)',             'SP-PEDS-SURG'),
    ('استئصال الطحال للأطفال',                 'Open Splenectomy (Pediatric)',           'SP-PEDS-SURG'),
    ('علاج فتق للأطفال',                       'Hernia Repair (Pediatric)',             'SP-PEDS-SURG'),

-- ═══════════════════════════════════════════════
-- SP-PLAST : جراحة التجميل والحروق
-- ═══════════════════════════════════════════════
    ('تشريط الحروق (Escharotomy)',             'Escharotomy',                            'SP-PLAST'),
    ('تصغير الثدي',                             'Reduction Mammoplasty',                  'SP-PLAST'),
    ('شق وإغلاق الجلد والأنسجة',             'Suture / Closure of Skin & Subcutaneous','SP-PLAST'),
    ('مراجعة الندبة',                           'Scar Revision',                          'SP-PLAST'),
    ('شق الجلد وإزالة جسم غريب',             'Incision with F.B. Removal from Skin',  'SP-PLAST'),
    ('استئصال آفة جلدية جذرية',               'Radical Excision of Skin Lesion',       'SP-PLAST'),
    ('Carpal Tunnel Syndrome I',                'Carpal Tunnel Syndrome Release I',       'SP-PLAST'),
    ('Carpal Tunnel Syndrome II',               'Carpal Tunnel Syndrome Release II',      'SP-PLAST'),
    ('فيلر كوري',                              'Korean Filler',                           'SP-PLAST'),
    ('فيلر إيطالي',                             'Italian Filler',                         'SP-PLAST'),
    ('فيلر سويسري',                             'Swiss Filler',                           'SP-PLAST'),
    ('ميكسر تفتيح A',                          'Whitening Mixer A',                      'SP-PLAST'),
    ('ميكسر تفتيح B',                          'Whitening Mixer B',                      'SP-PLAST'),
    ('ميكسر تفتيح C',                          'Whitening Mixer C',                      'SP-PLAST'),
    ('ميكسر تفتيح D',                          'Whitening Mixer D',                      'SP-PLAST'),
    ('حقنة الفراولة A',                        'Strawberry Injection A',                 'SP-PLAST'),
    ('حقنة الفراولة B',                        'Strawberry Injection B',                 'SP-PLAST'),
    ('حقنة الفراولة C',                        'Strawberry Injection C',                 'SP-PLAST'),
    ('حقنة السالمون A',                        'Salmon Injection A',                     'SP-PLAST'),
    ('حقنة السالمون B',                        'Salmon Injection B',                     'SP-PLAST'),
    ('حقنة السالمون C',                        'Salmon Injection C',                     'SP-PLAST'),
    ('حقنة الكولاجين A',                       'Collagen Injection A',                   'SP-PLAST'),
    ('حقنة الكولاجين B',                       'Collagen Injection B',                   'SP-PLAST'),
    ('كولاجين عين',                             'Eye Collagen',                           'SP-PLAST'),
    ('إبرة بولي لاكتيك A',                    'Poly Lactic Acid Injection A',           'SP-PLAST'),
    ('إبرة بولي لاكتيك B',                    'Poly Lactic Acid Injection B',           'SP-PLAST'),
    ('إبرة بولي لاكتيك C',                    'Poly Lactic Acid Injection C',           'SP-PLAST'),
    ('اكزوم A',                                'Exosome A',                              'SP-PLAST'),
    ('اكزوم B',                                'Exosome B',                              'SP-PLAST'),
    ('اكزوم C',                                'Exosome C',                              'SP-PLAST'),
    ('اكزوم D',                                'Exosome D',                              'SP-PLAST'),
    ('حقنة الجوري',                            'Rose Injection',                         'SP-PLAST'),
    ('تقشير بارد B',                           'Cold Peel B',                            'SP-PLAST'),
    ('جلسة ميزو للشعر',                        'Mesotherapy for Hair',                   'SP-PLAST'),
    ('حقن تجميلي',                             'Cosmetic Injections',                    'SP-PLAST'),

-- ═══════════════════════════════════════════════
-- SP-ENT-SURG : جراحة الأنف والأذن والحنجرة
-- ═══════════════════════════════════════════════
    ('إصلاح كسر الأنف',                         'Reduction of Nasal Fracture',            'SP-ENT-SURG'),
    ('جراحات الجيوب الأنفية والزوائد والالتهابات بالمنظار','Endoscopic Sinus Surgery (FESS)','SP-ENT-SURG'),
    ('رأب الحاجز الأنفي (Septoplasty)',         'Septoplasty',                            'SP-ENT-SURG'),
    ('رأب الأنف والحاجز (Septorhinoplasty)',    'Septorhinoplasty',                       'SP-ENT-SURG'),
    ('ورم الأنف الداخلي (Intranasal Polypectomy)','Intranasal Polypectomy',              'SP-ENT-SURG'),
    ('استئصال اللوزتين والنتوء اللحمي',        'Adeno-Tonsillectomy',                    'SP-ENT-SURG'),
    ('استئصال اللوزتين',                        'Tonsillectomy',                          'SP-ENT-SURG'),
    ('استئصال النتوء اللحمي',                   'Adenoidectomy',                          'SP-ENT-SURG'),
    ('استئصال النتوء اللحمي مع فتحة مؤقتة',   'Adenoidectomy & Grommet',               'SP-ENT-SURG'),
    ('جراحة الجيوب جانب واحد',                 'Sinus Surgery One Side',                'SP-ENT-SURG'),
    ('استئصال تكبير المحارة الأنفية',          'Turbinectomy',                           'SP-ENT-SURG'),
    ('فتح الجيوب (Intranasal Antrostomy)',       'Intranasal Antrostomy',                 'SP-ENT-SURG'),
    ('استئصال ورم الحنجرة',                     'Laryngectomy with Dissection',          'SP-ENT-SURG'),
    ('منظار الحنجرة',                           'Laryngoscopy',                           'SP-ENT-SURG'),
    ('خيوط بعد عملية الأنف',                   'Ear Polyp (One Side)',                  'SP-ENT-SURG'),
    ('فتح الأذن مع شفط',                       'EAR EUA + EUM',                         'SP-ENT-SURG'),
    ('استئصال ورم البلعوم',                    'EUA Nasopharynx',                        'SP-ENT-SURG'),
    ('ثقب الأذن لجهة واحدة',                  'Close Ear Hole One Side',               'SP-ENT-SURG'),
    ('ثقب الأذن لجهتين',                       'Close Ear Hole Both Sides',             'SP-ENT-SURG'),
    ('عملية مع اقتطاع طرف قطعة من الأذن جهة واحدة','Ear Polyp Removal One Side',      'SP-ENT-SURG'),
    ('TRACHEOSTOMY مؤقت',                       'Temporary Tracheostomy',                'SP-ENT-SURG'),
    ('TRACHEOSTOMY دائم',                       'Permanent Tracheostomy',                'SP-ENT-SURG'),

-- ═══════════════════════════════════════════════
-- SP-ENT : الأنف والأذن والحنجرة (عيادي)
-- ═══════════════════════════════════════════════
    ('منظار حنجرة مرن',                         'Flexible Laryngoscopy',                  'SP-ENT'),
    ('منظار صلب للأذن',                         'Rigid Ear Endoscopy',                   'SP-ENT'),
    ('إزالة تقسيم الأنف (Nasal Split Removal)', 'Removal of Nasal Split',               'SP-ENT'),
    ('غيار ما بعد عملية الأذن',                'Post-Op ENT Dressing',                  'SP-ENT'),
    ('شفط الأذن',                               'Ear Suction',                            'SP-ENT'),
    ('CTR (ENT Procedure)',                      'CTR ENT',                               'SP-ENT'),
    ('SAT/SRT واختبار تمييز الكلام',           'SAT/SRT and Speech Discrimination',     'SP-ENT'),
    ('Visual Reinforcement Audiometry (VRA)',    'Visual Reinforcement Audiometry',        'SP-ENT'),

-- ═══════════════════════════════════════════════
-- SP-AUDIO : السمع والتوازن
-- ═══════════════════════════════════════════════
    ('قياس السمع الصوتي (Audiometry)',           'Pure Tone Audiometry',                  'SP-AUDIO'),
    ('تمييز الكلام (Speech Discrimination)',     'Speech Discrimination Test',            'SP-AUDIO'),
    ('اختبار اتزان (Balance Test)',              'Balance / Vestibular Test',             'SP-AUDIO'),
    ('قياس ضغط الأذن (Tympanometry)',           'Tympanometry',                           'SP-AUDIO'),

-- ═══════════════════════════════════════════════
-- SP-OPHTH-SURG : جراحة العيون
-- ═══════════════════════════════════════════════
    ('حقن شبكية مرضى السكري',                  'Intravitreal Injection (Diabetic)',      'SP-OPHTH-SURG'),
    ('إزالة كيس دمعي (Dacryocystectomy)',       'Dacryocystectomy',                      'SP-OPHTH-SURG'),
    ('تصليح تمزق الملتحمة',                    'Conjunctival Laceration Repair',        'SP-OPHTH-SURG'),
    ('استئصال الجناح مع طعم (Pterygium + Graft)','Pterygium with Graft',               'SP-OPHTH-SURG'),
    ('DCR داخلي (Endoscopic)',                  'Endoscopic DCR',                        'SP-OPHTH-SURG'),
    ('DCR خارجي',                               'External DCR',                          'SP-OPHTH-SURG'),
    ('ICL مع تخدير',                            'ICL Implantation',                      'SP-OPHTH-SURG'),
    ('ARTIZAN عدسة مع تخدير',                   'Artisan Lens Implantation',             'SP-OPHTH-SURG'),
    ('قسطرة قناة الدمع تحت تخدير عام',        'Probing + S.W.O under GA',              'SP-OPHTH-SURG'),
    ('ثقب الصرف (Punctal Plug)',                'Punctal Plug Insertion',                'SP-OPHTH-SURG'),
    ('استئصال القزحية الجراحي (Peripheral Iridectomy LA)','Peripheral Iridectomy LA', 'SP-OPHTH-SURG'),
    ('حقنة داخل الجسم الزجاجي (INTRAVITREAL)', 'Intravitreal Implant Insertion',       'SP-OPHTH-SURG'),

-- ═══════════════════════════════════════════════
-- SP-OPHTH : أمراض العيون (عيادي)
-- ═══════════════════════════════════════════════
    ('Argon Laser معالجة',                      'Argon Laser Therapy',                   'SP-OPHTH'),
    ('ARM (Automated Refraction)',               'Automated Refraction',                  'SP-OPHTH'),
    ('PENTACAM',                                 'Pentacam Corneal Topography',           'SP-OPHTH'),
    ('OCT (Optical Coherence Tomography)',       'Optical Coherence Tomography',          'SP-OPHTH'),
    ('OCT+ANGIO',                                'OCT Angiography',                       'SP-OPHTH'),
    ('مجهر حيوي (SLIT LAMP)',                   'Slit Lamp Examination',                 'SP-OPHTH'),
    ('SPECULAR MICROSCOPY',                      'Specular Microscopy',                   'SP-OPHTH'),
    ('إزالة جسم غريب من العين',                'Removal of Foreign Body (Eye)',         'SP-OPHTH'),
    ('PICKING LA (Eye)',                         'Picking LA',                            'SP-OPHTH'),

-- ═══════════════════════════════════════════════
-- SP-GI-SURG : جراحة الجهاز الهضمي والمناظير
-- ═══════════════════════════════════════════════
    ('منظار هضمي علوي تشخيصي',                 'Diagnostic Upper GI Endoscopy',         'SP-GI-SURG'),
    ('منظار قولوني تشخيصي',                    'Diagnostic Colonoscopy',                 'SP-GI-SURG'),
    ('ERCP علاجي',                              'Therapeutic ERCP',                       'SP-GI-SURG'),
    ('علاج ليزر آرغون',                         'Argon Laser Endoscopy Therapy',         'SP-GI-SURG'),
    ('ربط وعاء نازف بالمنظار',                 'Endoscopic Clipping of Bleeding',       'SP-GI-SURG'),
    ('UPPER GIT Endoscopy',                      'Upper GI Endoscopy (General)',          'SP-GI-SURG'),
    ('TRACHEOSTOMY بالمنظار',                   'Endoscopic Tracheostomy',               'SP-GI-SURG'),

-- ═══════════════════════════════════════════════
-- SP-OBS-GYN : النساء والولادة (عيادي)
-- ═══════════════════════════════════════════════
    ('سحب كيس من البربخ',                       'Retract Cyst from Epididymis',           'SP-OBS-GYN'),
    ('إزالة لولب',                              'Loop / IUD Removal',                    'SP-OBS-GYN'),
    ('استئصال زوائد صغيرة (Polypectomy)',       'Simple Polypectomy (Gynae)',             'SP-OBS-GYN'),
    ('خياطة فرج بعد ولادة',                    'Episiotomy Repair',                      'SP-OBS-GYN'),
    ('CTG للأم',                                'CTG Monitoring',                         'SP-OBS-GYN'),

-- ═══════════════════════════════════════════════
-- SP-OBS-SURG : جراحة النساء والولادة
-- ═══════════════════════════════════════════════
    ('D&C (توسيع وكحت)',                        'Dilatation & Curettage (D&C)',           'SP-OBS-SURG'),
    ('استئصال الرحم الكامل (TAH)',              'Total Abdominal Hysterectomy',          'SP-OBS-SURG'),
    ('استئصال الرحم الجزئي (Sub-Total)',        'Sub-Total Hysterectomy',                'SP-OBS-SURG'),
    ('إجراءات مساعدة للولادة',                 'Other Procedures Assisting Delivery',   'SP-OBS-SURG'),
    ('ختان بطانة عنق الرحم (Trachelorrhaphy)', 'Trachelorrhaphy',                        'SP-OBS-SURG'),
    ('ثقب غشاء البكارة (Imperforated Hymen)',  'Imperforated Hymen Incision',           'SP-OBS-SURG'),
    ('خياطة نوع A بعد الولادة',                'Perineal Suture A',                     'SP-OBS-SURG'),
    ('خياطة نوع B بعد الولادة',                'Perineal Suture B',                     'SP-OBS-SURG'),
    ('بضع المهبل (Episiotomy)',                 'Episiotomy',                             'SP-OBS-SURG'),

-- ═══════════════════════════════════════════════
-- SP-MAXFAC : جراحة الوجه والفكين
-- ═══════════════════════════════════════════════
    ('قلع جراحي لضرس عقل',                     'Surgical Extraction of Impacted Tooth', 'SP-MAXFAC'),
    ('قلع جراحي لأضراس عقل متعددة',           'Surgical Extraction - Multiple Impacted','SP-MAXFAC'),
    ('إزالة كيس فكي',                           'Jaw Cyst Removal',                       'SP-MAXFAC'),
    ('إزالة كيس فكي كبير',                     'Large Jaw Cyst Removal',                'SP-MAXFAC'),

-- ═══════════════════════════════════════════════
-- SP-DENT : طب الأسنان
-- ═══════════════════════════════════════════════
    ('D4 Perio Chart',                           'D4 Periodontal Chart',                  'SP-DENT'),
    ('D5 Digital Intra Oral X-Ray',              'Digital Intra Oral X-Ray',              'SP-DENT'),
    ('E2 Pulpectomy',                            'Pulpectomy',                             'SP-DENT'),
    ('E3 Root Canal Treatment Anterior (Rotary)','Root Canal Treatment - Anterior',       'SP-DENT'),
    ('QR23 LIP BUMPER',                          'Lip Bumper',                             'SP-DENT'),
    ('QR24 ESSEX ONE ARCH',                      'Essex Retainer One Arch',               'SP-DENT'),
    ('QR25 NIGHT GUARD',                         'Night Guard',                            'SP-DENT'),
    ('DENTAL SURGERY 03',                        'Dental Surgery 03',                      'SP-DENT'),
    ('DENTAL SURGERY 04',                        'Dental Surgery 04',                      'SP-DENT'),
    ('تنظيف وتلميع الأسنان',                   'Dental Scaling & Polishing',             'SP-DENT'),
    ('حشو أسنان',                               'Dental Filling',                         'SP-DENT'),
    ('تركيب تاج أسنان',                        'Dental Crown',                           'SP-DENT'),
    ('جسر أسنان',                               'Dental Bridge',                          'SP-DENT'),
    ('تبييض أسنان',                             'Teeth Whitening',                        'SP-DENT'),
    ('تقويم أسنان',                             'Orthodontic Treatment',                  'SP-DENT'),
    ('قلع أسنان عادي',                          'Simple Tooth Extraction',               'SP-DENT'),

-- ═══════════════════════════════════════════════
-- SP-DIALYSIS : غسيل الكلى
-- ═══════════════════════════════════════════════
    ('جلسة غسيل كلى',                          'Hemodialysis Session',                   'SP-DIALYSIS'),
    ('ساعة إضافية غسيل كلى',                  'Additional Hour HD',                     'SP-DIALYSIS'),
    ('تركيب قسطرة غسيل كلى مؤقتة',           'Hemodialysis Catheter Insertion (Temp)', 'SP-DIALYSIS'),
    ('تركيب قسطرة غسيل كلى دائمة',           'Hemodialysis Catheter Insertion (Perm)', 'SP-DIALYSIS'),
    ('تركيب قسطرة غسيل البريتون',             'Peritoneal Dialysis Catheter',          'SP-DIALYSIS'),

-- ═══════════════════════════════════════════════
-- SP-CHEMO : العلاج الكيماوي
-- ═══════════════════════════════════════════════
    ('جلسة كيماوي بالتسريب الوريدي',          'IV Chemotherapy Session',                'SP-CHEMO'),
    ('حصة مشاركة الكيماوي',                   'Chemotherapy Share',                     'SP-CHEMO'),

-- ═══════════════════════════════════════════════
-- SP-ONCOL : الأورام
-- ═══════════════════════════════════════════════
    ('استشارة أورام',                           'Oncology Consultation',                  'SP-ONCOL'),
    ('متابعة حالة أورام',                      'Oncology Follow-Up',                     'SP-ONCOL'),

-- ═══════════════════════════════════════════════
-- SP-PHYSIO : العلاج الطبيعي
-- ═══════════════════════════════════════════════
    ('علاج بالتأهيل الحركي',                   'Physical Rehabilitation Session',        'SP-PHYSIO'),
    ('علاج طبيعي عام',                          'General Physiotherapy Session',          'SP-PHYSIO'),
    ('علاج بالموجات فوق الصوتية',              'Ultrasonic Therapy',                     'SP-PHYSIO'),
    ('علاج بالتيار الكهربائي',                 'Electrical Stimulation Therapy',         'SP-PHYSIO'),
    ('علاج الظهر',                              'Back Pain Physiotherapy',               'SP-PHYSIO'),
    ('تمارين إعادة تأهيل',                     'Rehabilitation Exercises',               'SP-PHYSIO'),

-- ═══════════════════════════════════════════════
-- SP-PSYCH : الطب النفسي
-- ═══════════════════════════════════════════════
    ('كشف نفسي',                                'Psychiatric Consultation',               'SP-PSYCH'),
    ('جلسة علاج نفسي',                        'Psychotherapy Session',                  'SP-PSYCH'),
    ('تقييم نفسي',                              'Psychiatric Assessment',                 'SP-PSYCH'),

-- ═══════════════════════════════════════════════
-- SP-DERMA : الجلدية
-- ═══════════════════════════════════════════════
    ('كشف جلدي',                                'Dermatology Consultation',               'SP-DERMA'),
    ('Cautery A (1-2 lesions)',                  'Cautery A (1-2 Lesions)',               'SP-DERMA'),
    ('Cautery B (3-4 lesions)',                  'Cautery B (3-4 Lesions)',               'SP-DERMA'),
    ('Cautery C (5-6 lesions)',                  'Cautery C (5-6 Lesions)',               'SP-DERMA'),
    ('إزالة أكياس دهنية',                      'Lipoma Removal',                         'SP-DERMA'),
    ('إزالة أكياس دهنية متعددة',               'Multiple Lipoma Removal',               'SP-DERMA'),

-- ═══════════════════════════════════════════════
-- SP-REPRO : العقم والخصوبة
-- ═══════════════════════════════════════════════
    ('دوالي الحبل المنوي المجهرية (Microsurgical Varicocelectomy)','Microsurgical Varicocelectomy','SP-REPRO'),
    ('ICSI / IVF',                               'ICSI / In Vitro Fertilization',         'SP-REPRO'),
    ('استشارة خصوبة',                           'Fertility Consultation',                 'SP-REPRO'),

-- ═══════════════════════════════════════════════
-- SP-PULM : أمراض الصدر والجهاز التنفسي
-- ═══════════════════════════════════════════════
    ('كشف صدر وجهاز تنفسي',                    'Pulmonology Consultation',               'SP-PULM'),
    ('قياس وظائف التنفس',                       'Spirometry / PFT',                       'SP-PULM'),
    ('تنظير الشعب الهوائية',                   'Bronchoscopy',                           'SP-PULM'),

-- ═══════════════════════════════════════════════
-- SP-NEPHRO : أمراض الكلى
-- ═══════════════════════════════════════════════
    ('كشف كلى',                                 'Nephrology Consultation',                'SP-NEPHRO'),
    ('بزل كلى موجه بالأشعة',                  'Renal Biopsy (Image-Guided)',            'SP-NEPHRO'),

-- ═══════════════════════════════════════════════
-- SP-RHEUM : الروماتولوجيا
-- ═══════════════════════════════════════════════
    ('كشف روماتيزم',                            'Rheumatology Consultation',              'SP-RHEUM'),
    ('حقن داخل مفصلي',                         'Intra-Articular Injection',              'SP-RHEUM'),

-- ═══════════════════════════════════════════════
-- SP-ENDО : الغدد الصماء
-- ═══════════════════════════════════════════════
    ('كشف غدد صماء',                           'Endocrinology Consultation',             'SP-ENDO'),
    ('تركيب شريحة سكر إلكترونية',             'CGM Sensor Insertion',                   'SP-ENDO'),

-- ═══════════════════════════════════════════════
-- SP-INFECT : الأمراض المعدية
-- ═══════════════════════════════════════════════
    ('كشف أمراض معدية',                         'Infectious Disease Consultation',        'SP-INFECT'),

-- ═══════════════════════════════════════════════
-- SP-NEUROL : الأعصاب (عيادي)
-- ═══════════════════════════════════════════════
    ('كشف أعصاب',                              'Neurology Consultation',                 'SP-NEUROL'),
    ('اختبار رنح / مشية',                      'Gait / Ataxia Assessment',              'SP-NEUROL'),

-- ═══════════════════════════════════════════════
-- SP-GASTRO : الجهاز الهضمي (عيادي)
-- ═══════════════════════════════════════════════
    ('كشف جهاز هضمي',                          'Gastroenterology Consultation',          'SP-GASTRO'),

-- ═══════════════════════════════════════════════
-- SP-PEDS : طب الأطفال (عيادي)
-- ═══════════════════════════════════════════════
    ('كشف أطفال عام',                          'Pediatrics General Consultation',        'SP-PEDS'),
    ('تطعيم',                                   'Vaccination',                            'SP-PEDS'),
    ('متابعة نمو الطفل',                       'Child Growth Follow-Up',                'SP-PEDS'),

-- ═══════════════════════════════════════════════
-- SP-CARDIO (more outpatient cardiology)
-- ═══════════════════════════════════════════════
    ('كشف قلب',                                 'Cardiology Consultation',               'SP-CARDIO'),
    ('اختبار جهد قلبي',                        'Cardiac Stress Test',                   'SP-CARDIO'),

-- ═══════════════════════════════════════════════
-- SP-NURSING : More outpatient consultations
-- (from 'الكشف و الاستشارات الطبية' V13 group)
-- ═══════════════════════════════════════════════
    ('كشف استشاري عام / زيارة واحدة',          'General Consultant Visit',               'SP-NURSING'),
    ('كشف إخصائي بالعيادة الخارجية',          'Specialist Outpatient Visit',            'SP-NURSING'),
    ('كشف استشاري نساء بالعيادة + صورة',      'Gynae Consultant Visit + US',            'SP-NURSING'),
    ('كشف إخصائي نساء بالعيادة + صورة',       'Gynae Specialist Visit + US',            'SP-NURSING'),
    ('متابعة استشاري / اليوم',                  'Daily Consultant Supervision',          'SP-NURSING'),
    ('استدعاء استشاري إضافي',                  'Additional Consultant Call',             'SP-NURSING'),
    ('استدعاء مناوب - مستوى 1',               'On-Call - Level 1',                     'SP-NURSING'),
    ('CALL3 خروج طبيب مع سيارة إسعاف',        'CALL3 Doctor + Ambulance',              'SP-NURSING'),
    ('CALL4 مصاريف التنقل بسيارة الإسعاف',    'CALL4 Ambulance Transport Fees',        'SP-NURSING'),
    ('CALL5 خروج ممرض مع سيارة إسعاف',        'CALL5 Nurse + Ambulance',               'SP-NURSING'),
    ('جلسة كي ثآليل جلدية بالعيادة',          'Electrocautery Warts',                  'SP-NURSING'),
    ('زيارة طبيب زائر',                        'Visiting Doctor Fee',                    'SP-NURSING'),
    ('إشراف طبي ومتابعة إخصائي / اليوم',     'Specialist Daily Medical Supervision',  'SP-NURSING'),
    ('كشف أو زيارة أخصائي تغذية',            'Nutritionist Visit',                     'SP-NURSING'),
    ('غرز جرح كبير',                           'Large Wound Stitches',                  'SP-NURSING'),
    ('غرز جرح متوسط',                          'Medium Wound Stitches',                 'SP-NURSING'),
    ('ثقب الأذن',                               'Ear Piercing',                          'SP-NURSING'),
    ('ثقب حلقة',                                'Ring Hole Piercing',                    'SP-NURSING'),
    ('حقن علاجي داخل العيادة مستوى 1',        'Therapeutic Injection Level 1',         'SP-NURSING'),
    ('حقن علاجي داخل العيادة مستوى 2',        'Therapeutic Injection Level 2',         'SP-NURSING'),
    ('حقن علاجي داخل العيادة مستوى 3',        'Therapeutic Injection Level 3',         'SP-NURSING'),
    ('حقن علاجي داخل العيادة مستوى 4',        'Therapeutic Injection Level 4',         'SP-NURSING'),
    ('حقن علاجي داخل العيادة مستوى 5',        'Therapeutic Injection Level 5',         'SP-NURSING'),
    ('تكلفة استخدام الجهاز',                   'Equipment Usage Fee',                   'SP-NURSING'),
    ('VECTATHERAPY 1',                           'Vectatherapy 1',                        'SP-NURSING'),
    ('VECTATHERAPY 2',                           'Vectatherapy 2',                        'SP-NURSING'),
    ('حصة فني الأشعة',                         'X-Ray Technician Fee',                  'SP-NURSING')

) AS v(name_ar, name_en, spec_code)
WHERE
    -- Must have a usable Arabic name
    COALESCE(NULLIF(TRIM(v.name_ar), ''), NULLIF(TRIM(v.name_en), '')) IS NOT NULL
    -- Idempotent: skip if already exists by Arabic name
    AND NOT EXISTS (
        SELECT 1
        FROM   medical_services ms
        WHERE  LOWER(TRIM(ms.name_ar)) = LOWER(TRIM(v.name_ar))
          AND  ms.deleted = FALSE
    )
    -- specialty must resolve
    AND (SELECT id FROM medical_specialties WHERE code = v.spec_code AND deleted = FALSE) IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION 2: Unique index on lower(name_ar) for future idempotency enforcement
-- ─────────────────────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS uk_medical_services_name_ar_lower
    ON medical_services (LOWER(name_ar))
    WHERE deleted = FALSE AND name_ar IS NOT NULL;
