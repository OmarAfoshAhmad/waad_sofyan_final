import pandas as pd

# Define Jalyana Main Categories and their Contexts
ROOT_CATEGORIES = {
    'CAT-OUTPAT': 'خارج المستشفى (OP)',
    'CAT-INPAT': 'داخل المستشفى (IP)',
    'CAT-DENTAL': 'الأسنان',
    'CAT-VISION': 'العيون',
    'CAT-MATERNITY': 'الأمومة',
    'CAT-CHRONIC': 'الأمراض المزمنة',
    'CAT-EMERGENCY': 'الحالات الطارئة',
    'CAT-OTHER': 'أخرى'
}

def classify_dar_shifa():
    df = pd.read_excel('قائمة اسعار خدمات دار الشفاء مصنفة.xlsx', skiprows=8)
    
    # Mapping logic based on specialty (Unnamed: 4) and category (Unnamed: 5)
    mapping = []
    for index, row in df.iterrows():
        specialty = str(row['Unnamed: 4'])
        generic_cat = str(row['Unnamed: 5'])
        service_name = str(row['Unnamed: 3'])
        
        main_cat = 'أخرى'
        sub_cat = 'غير مصنف'
        
        if 'أسنان' in specialty or 'الأسنان' in generic_cat:
            main_cat = ROOT_CATEGORIES['CAT-DENTAL']
            sub_cat = 'أسنان روتيني' if 'كشف' in service_name or 'خلع' in service_name else 'أسنان متقدم'
        elif 'العيون' in specialty:
            main_cat = ROOT_CATEGORIES['CAT-VISION']
            sub_cat = 'عيون روتيني'
        elif 'إيواء' in generic_cat or 'الجراحة' in specialty or 'التخذير' in specialty:
            main_cat = ROOT_CATEGORIES['CAT-INPAT']
            sub_cat = 'جراحات وإقامة'
        elif 'معامل' in specialty or 'تحاليل' in generic_cat:
            main_cat = ROOT_CATEGORIES['CAT-OUTPAT']
            sub_cat = 'تحاليل وأشعة روتينية'
        elif 'اشعة' in specialty or 'الصور التشخيصية' in specialty:
            main_cat = ROOT_CATEGORIES['CAT-OUTPAT']
            sub_cat = 'أشعة روتينية' if 'عادية' in service_name else 'أشعة تخصصية (MRI/CT)'
        elif 'العيادات الخارجية' in specialty or 'كشف' in specialty:
            main_cat = ROOT_CATEGORIES['CAT-OUTPAT']
            sub_cat = 'كشوفات خارجية (OP)'
        elif 'الطوارئ' in specialty:
            main_cat = ROOT_CATEGORIES['CAT-EMERGENCY']
            sub_cat = 'خدمات الطوارئ'

        mapping.append({
            'الخدمة': service_name,
            'التصنيف الرئيسي (Context)': main_cat,
            'التصنيف الفرعي (Benefit Item)': sub_cat
        })
    
    result_df = pd.DataFrame(mapping)
    print(result_df.head(20).to_string())
    # Save a sample to show the user
    result_df.head(50).to_csv('dar_shifa_mapped_sample.csv', index=False, encoding='utf-8-sig')

if __name__ == '__main__':
    classify_dar_shifa()
