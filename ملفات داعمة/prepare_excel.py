import pandas as pd
try:
    input_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx'
    df_in = pd.read_excel(input_path)
    facility_names = df_in['facility_name / اسم المرفق'].dropna().tolist()
    data = []
    for name in facility_names:
        name_clean = str(name).strip()
        p_type = 'CLINIC'
        if 'مستشفى' in name_clean or 'مستشفي' in name_clean: p_type = 'HOSPITAL'
        elif 'صيدلية' in name_clean: p_type = 'PHARMACY'
        elif 'مختبر' in name_clean or 'معمل' in name_clean: p_type = 'LAB'
        elif 'اشعة' in name_clean or 'أشعة' in name_clean: p_type = 'RADIOLOGY'
        
        data.append({
            'اسم مقدم الخدمة': name_clean,
            'نوع المقدم': p_type,
            'المدينة': '',
            'الاسم بالإنجليزية': '',
            'رقم الهاتف': '',
            'البريد الإلكتروني': '',
            'العنوان': ''
        })
    df_out = pd.DataFrame(data)
    output_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/ملف_المرافق_الجاهز_للاستيراد.xlsx'
    df_out.to_excel(output_path, index=False)
    print('Generated file with', len(data), 'rows at', output_path)
except Exception as e:
    print('ERROR:', e)
