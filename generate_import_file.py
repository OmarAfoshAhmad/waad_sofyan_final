import pandas as pd
import math

def transform_to_template():
    # 1. Load system categories for mapping
    # My manual extraction based on V20260304_01 and V88:
    # Key: Name (AR), Value: Code
    system_cats = {
        'جراحة عامة - إيواء': 'CAT-IP-GS',
        'أوعية دموية - إيواء': 'CAT-IP-VASC',
        'قلب وقسطرة - إيواء': 'CAT-IP-CARD',
        'نساء وولادة - إيواء': 'CAT-IP-OB',
        'علاج أورام - إيواء': 'CAT-IP-ONCO',
        'غسيل كلى - إيواء': 'CAT-IP-DIAL',
        'عناية فائقة - إيواء': 'CAT-IP-ICU',
        'غرفة عمليات - عامة': 'CAT-OR-GEN',
        'غرفة عمليات - أوعية دموية': 'CAT-OR-VASC',
        'غرفة عمليات - تجميل': 'CAT-OR-PLAST',
        'كشف وزيارات - عيادة': 'CAT-OP-CONS',
        'تحاليل ومختبرات - عيادة': 'CAT-OP-LAB',
        'أشعة تخصصية - عيادة': 'CAT-OP-IMG',
        'علاج طبيعي - عيادة': 'CAT-OP-PHYS',
        'أسنان - عيادة': 'CAT-OP-DENT',
        'نظارات طبية - عيادة': 'CAT-OP-OPT',
        'أدوية بوصفة - عيادة': 'CAT-OP-DRUG',
        'علاج ألم - عيادة': 'CAT-OP-PAIN',
        'إسعاف محلي - طوارئ': 'CAT-EM-AMB',
        'إخلاء طبي - طوارئ': 'CAT-EM-EVAC',
        'أدوية مزمنة - منافع خاصة': 'CAT-SP-CHR',
        'إصابات عمل - منافع خاصة': 'CAT-SP-OCC',
        'طب نفسي - منافع خاصة': 'CAT-SP-PSY',
        'عمليات': 'CAT-OPER',
        'إيواء': 'CAT-INPAT',
        'عيادات خارجية': 'CAT-OUTPAT',
        'تحاليل طبية': 'CAT-LAB',
        'اسنان وقائي': 'CAT-DENT-PREV',
        'اسنان تجميلي': 'CAT-DENT-COS',
        'اشعة': 'CAT-RAD',
        'علاج طبيعي (عام)': 'CAT-PHYSIO'
    }

    # 2. Source file extraction mapping
    def map_source_cat_to_system(src_cat, src_service_name):
        if not isinstance(src_cat, str) or pd.isna(src_cat):
            # Try to infer from name if it's a consultation
            name = str(src_service_name).lower()
            if 'كشف' in name or 'متابعة' in name or 'examination' in name:
                return 'كشف وزيارات - عيادة'
            return 'عيادات خارجية' # Default

        src_cat = src_cat.strip()
        if 'الايواء والاعاشة' in src_cat: return 'إيواء'
        if 'الجراحة العامة' in src_cat: return 'غرفة عمليات - عامة'
        if 'النساء والولادة' in src_cat: return 'نساء وولادة - إيواء'
        if 'الانف والاذن والحنجرة' in src_cat: return 'عمليات'
        if 'المالك البولية' in src_cat: return 'عمليات'
        if 'المناظير' in src_cat: return 'عمليات'
        if 'Surgery' in src_cat: return 'غرفة عمليات - عامة'
        if 'طوارئ' in src_cat: return 'إسعاف محلي - طوارئ'
        return 'إيواء' # Default fallback for this specific file structure

    # 3. Load Source Data
    source_df = pd.read_excel('مصحة منارة المستقبل درنة.xlsx')
    
    # Process Rows
    final_data = []
    current_cat_name = "عيادات خارجية" # Default start
    
    for idx, row in source_df.iterrows():
        # Source structure: 'name', 'price', 'Unnamed: 4' (EN Cat), 'Unnamed: 5' (AR Cat)
        service_name_ar = str(row['name']) if not pd.isna(row['name']) else None
        service_price = row['price'] if not pd.isna(row['price']) else 0
        cat_col = str(row['Unnamed: 5']) if not pd.isna(row['Unnamed: 5']) else None
        
        # If we hit a row with a new category, update current_cat_name
        if cat_col and (service_name_ar is None or "إجمالي" in service_name_ar or service_name_ar == ""):
            current_cat_name = cat_col.strip()
            continue # Skip category header rows
            
        if cat_col: # Some rows have both service name and category
             current_cat_name = cat_col.strip()

        if not service_name_ar or "إجمالي" in service_name_ar:
            continue
            
        mapped_cat = map_source_cat_to_system(current_cat_name, service_name_ar)
        
        final_data.append({
            'service_name / اسم الخدمة ★': service_name_ar,
            'service_code / الكود': f"MANARA-{idx+1:04d}",
            'unit_price / السعر': service_price,
            'category / التصنيف': mapped_cat,
            'specialty / التخصص': '',
            'notes / ملاحظات': str(row['nameEnglish']) if not pd.isna(row['nameEnglish']) else ""
        })
        
    # 5. Export to CSV/Excel Template format
    final_df = pd.DataFrame(final_data)
    output_filename = 'Manara_Import_Template.xlsx'
    
    # Use the same column order as the template
    columns = ['service_name / اسم الخدمة ★', 'service_code / الكود', 'unit_price / السعر', 'category / التصنيف', 'specialty / التخصص', 'notes / ملاحظات']
    final_df = final_df[columns]
    
    final_df.to_excel(output_filename, index=False)
    print(f"Successfully generated: {output_filename}")
    print(f"Total rows: {len(final_df)}")

if __name__ == "__main__":
    transform_to_template()
