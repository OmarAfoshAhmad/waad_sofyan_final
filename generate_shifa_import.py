import pandas as pd
import re
import sys

# Ensure UTF-8 output for Arabic text
sys.stdout.reconfigure(encoding='utf-8')

source_file = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx"
output_file = r"d:\tba_waad_system-main\tba_waad_system-main\Dar_Shifa_Import_Ready.xlsx"

try:
    df_raw = pd.read_excel(source_file, header=None)
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)

CATEGORIES = {
    'CAT-OUTPAT': 'خارج المستشفى (OP)',
    'CAT-INPAT': 'داخل المستشفى (IP)',
    'CAT-DENTAL': 'الأسنان',
    'CAT-VISION': 'العيون',
    'CAT-MATERNITY': 'الأمومة',
    'CAT-CHRONIC': 'الأمراض المزمنة',
    'CAT-EMERGENCY': 'الحالات الطارئة',
    'CAT-OTHER': 'أخرى'
}

def classify(name, specialty, raw_cat):
    n = str(name).lower()
    s = str(specialty).lower()
    r = str(raw_cat).lower()
    
    # Check DENTAL
    if any(k in n or k in s for k in ['أسنان', 'خلع', 'حشو', 'تقويم']):
        return CATEGORIES['CAT-DENTAL'], 'CAT-DENTAL'
        
    # Check VISION
    if any(k in n or k in s for k in ['عيون', 'نظارات', 'رمد', 'بصريات']):
        return CATEGORIES['CAT-VISION'], 'CAT-VISION'
        
    # Check MATERNITY
    if any(k in n or k in s for k in ['ولادة', 'قيصرية', 'حمل', 'توليد', 'جنين', 'أطفال أنابيب']):
        return CATEGORIES['CAT-MATERNITY'], 'CAT-MATERNITY'
        
    # Check EMERGENCY
    if any(k in n or k in s or k in r for k in ['طوارئ', 'إسعاف']):
        return CATEGORIES['CAT-EMERGENCY'], 'CAT-EMERGENCY'
        
    # Check INPATIENT (Surgery, Operations, Anesthesia, Inpatient)
    if any(k in n or k in s or k in r for k in ['عملية', 'تخدير', 'إقامة', 'عناية', 'جراحة', 'متابعة داخلية', 'إيواء']):
        return CATEGORIES['CAT-INPAT'], 'CAT-INPAT'
        
    # Check LAB/Imaging -> usually OUTPATIENT (OP) 
    # but we can call them "خارج المستشفى" as requested
    if any(k in n or k in s for k in ['تحليل', 'فحص دم', 'بول', 'سائل', 'كيمياء', 'دم', 'مختبر', 'أشعة', 'رنين', 'مقطعية', 'إيكو']):
        return CATEGORIES['CAT-OUTPAT'], 'CAT-OUTPAT'
        
    # Check Consultancy
    if any(k in n or k in s for k in ['كشف', 'استشارة', 'مراجعة', 'عيادة']):
        return CATEGORIES['CAT-OUTPAT'], 'CAT-OUTPAT'
    
    return CATEGORIES['CAT-OUTPAT'], 'CAT-OUTPAT' # Default

import_data = []

for idx, row in df_raw.iterrows():
    # Price: Index 2, Name: Index 3, Specialty: Index 4, RawCat: Index 5
    raw_name_cell = str(row[3]) if pd.notnull(row[3]) else ""
    if not raw_name_cell.strip() or any(h in raw_name_cell for h in ['اسم الخدمة', 'الكود', 'البيان']):
        continue
    
    try:
        p_val = str(row[2])
        price_val = float(re.sub(r'[^0-9.]', '', p_val))
    except:
        continue
        
    svc_code = ""
    svc_name = raw_name_cell
    m = re.match(r'^([A-Z0-9-]+)\s+(.+)$', raw_name_cell)
    if m:
        svc_code = m.group(1)
        svc_name = m.group(2)
        
    raw_specialty = str(row[4]) if pd.notnull(row[4]) else ""
    raw_cat = str(row[5]) if pd.notnull(row[5]) else ""
    
    cat_name, cat_code = classify(svc_name, raw_specialty, raw_cat)
    
    import_data.append({
        'service_name / اسم الخدمة ★': svc_name,
        'service_code / الكود': svc_code,
        'unit_price / السعر': price_val,
        'category / التصنيف': cat_name,
        'specialty / التخصص': raw_specialty,
        'notes / ملاحظات': f"Root Mapping: {cat_code}"
    })

df_output = pd.DataFrame(import_data)
try:
    df_output.to_excel(output_file, index=False)
    print(f"Created file: {output_file}")
    print(f"Processed {len(df_output)} services.")
except Exception as e:
    print(f"Error saving: {e}")
