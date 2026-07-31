import pandas as pd
import sys
import re

sys.stdout.reconfigure(encoding='utf-8')

file_path = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx"
output_path = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة_خدمات_دار_الشفاء_الموحدة_v2.xlsx"

# Dictionary for Rule Mapping (ID: AR_Name, EN_Name)
rules_definition = {
    1: ("داخل المستشفى - عام", "Inpatient - General"),
    2: ("داخل المستشفى - تمريض منزلي", "Inpatient - Home Nursing"),
    3: ("داخل المستشفى - علاج طبيعي", "Inpatient - Physiotherapy"),
    4: ("داخل المستشفى - إصابات عمل", "Inpatient - Work Injuries"),
    5: ("داخل المستشفى - طب نفسي", "Inpatient - Psychiatry"),
    6: ("داخل المستشفى - ولادة طبيعية وقيصرية", "Inpatient - Delivery"),
    7: ("داخل المستشفى - مضاعفات حمل", "Inpatient - Maternity Complications"),
    8: ("خارج المستشفى - عام", "Outpatient - General"),
    9: ("خارج المستشفى - أشعة", "Outpatient - Radiology"),
    10: ("خارج المستشفى - رنين مغناطيسي", "Outpatient - MRI"),
    11: ("خارج المستشفى - علاجات وادوية روتينية", "Outpatient - Routine Meds"),
    12: ("خارج المستشفى - أجهزة ومعدات", "Outpatient - Medical Devices"),
    13: ("خارج المستشفى - علاج طبيعي", "Outpatient - Physiotherapy"),
    14: ("خارج المستشفى - اسنان روتيني", "Outpatient - Routine Dental"),
    15: ("خارج المستشفى - اسنان تجميلي", "Outpatient - Cosmetic Dental"),
    16: ("خارج المستشفى - النظارة الطبية", "Outpatient - Medical Glasses")
}

def get_rule_id(service, specialty, context_raw):
    s = service.lower()
    sp = specialty.lower()
    c = context_raw.lower()
    
    is_inpatient = any(x in c or x in sp for x in ['إيواء', 'اقامة', 'عمليات', 'داخل'])
    
    if is_inpatient:
        if 'تمريض' in s or 'منزلي' in s: return 2
        if 'طبيعي' in s or 'physio' in s: return 3
        if 'عمل' in s or 'إصابات' in s: return 4
        if 'نفسي' in s or 'psychiatry' in s: return 5
        if 'ولادة' in s or 'قيصرية' in s or 'delivery' in s: return 6
        if 'مضاعفات' in s or 'حمل' in s: return 7
        return 1
    else:
        if 'رنين' in s or 'mri' in s: return 10
        if any(x in s for x in ['أشعة', 'اشعه', 'x-ray', 'مقطع', 'scan', 'ايكو', 'echo', 'سونار']): return 9
        if any(x in s for x in ['دواء', 'ادوية', 'علاجات', 'روتينية']): return 11
        if any(x in s for x in ['جهاز', 'معدات', 'device']): return 12
        if 'طبيعي' in s or 'physio' in s: return 13
        if ('تجميل' in s or 'تبييض' in s) and ('أسنان' in s or 'dental' in s): return 15
        if 'أسنان' in s or 'dental' in s: return 14
        if 'نظارة' in s or 'نظارات' in s: return 16
        return 8

# Medical terms for English translation
translation_dict = {
    'جهاز التنفس الاصطناعي': 'Mechanical Ventilator',
    'تطهير بالحقنة الشرجية': 'Enema Cleansing',
    'تحليل غازات الدم': 'Blood Gas Analysis (ABG)',
    'سحب وشفط سوائل': 'Fluid Aspiration',
    'غسيل معدة': 'Gastric Lavage',
    'صورة أشعة': 'X-Ray Image',
    'كشف عيادة': 'Clinic Consultation',
    'تركيب قسطرة بولية': 'Urinary Catheterization',
    'نقل دم': 'Blood Transfusion',
    'خياطة جرح': 'Wound Suture',
    'جبس': 'Cast/Plaster',
    'تخطيط قلب': 'ECG/EKG',
    'مختبر': 'Laboratory',
    'أيكو': 'Echocardiogram',
    'رنين': 'MRI Scan'
}

def process_row(row):
    original_service = str(row.iloc[3])
    specialty = str(row.iloc[4])
    original_cat = str(row.iloc[5])
    
    # 1. Medical Code
    code_match = re.search(r'([A-Z]+-\d+)', original_service)
    med_code = code_match.group(1) if code_match else ""
    
    # 2. Clean Service Name AR
    clean_service_ar = original_service.replace(med_code, "").strip()
    
    # 3. English Name
    service_en = ""
    for ar_term, en_term in translation_dict.items():
        if ar_term in clean_service_ar:
            service_en = en_term
            break
    if not service_en:
        service_en = "Medical Procedure" if "خدمات" in specialty else "Medical Item/Service"

    # 4. Context & Rule Mapping
    rule_id = get_rule_id(clean_service_ar, specialty, original_cat)
    rule_ar, rule_en = rules_definition[rule_id]
    
    return pd.Series([
        clean_service_ar,       # اسم الخدمة عربي
        service_en,              # اسم الخدمة انجليزي
        med_code,                # الكود الطبي
        rule_ar,                 # قاعدة التأمين (عربي)
        rule_en,                 # Insurance Rule (EN)
        rule_id,                 # رقم القاعدة
        original_cat             # تصنيف المشفى الأصلي
    ])

try:
    df_raw = pd.read_excel(file_path, skiprows=8)
    df_raw = df_raw.dropna(subset=[df_raw.columns[3]])
    
    print(f"Finalizing unified list with 16 classifications for {len(df_raw)} items...")
    
    final_data = df_raw.apply(process_row, axis=1)
    final_data.columns = [
        'اسم الخدمة (عربي)', 
        'Service Name (English)', 
        'الكود الطبي', 
        'تصنيف التأمين (العربي)', 
        'Insurance Category (English)',
        'رقم القاعدة (1-16)',
        'التصنيف الأصلي للمشفى'
    ]
    
    final_data.to_excel(output_path, index=False)
    print(f"DONE: {output_path}")

except Exception as e:
    import traceback
    traceback.print_exc()
