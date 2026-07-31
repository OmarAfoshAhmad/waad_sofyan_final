import pandas as pd

def infer_provider_type(name):
    if pd.isna(name):
        return 'CLINIC'
    name_str = str(name).lower()
    
    if any(x in name_str for x in ['صيدلية', 'صيدليه', 'pharmacy']): 
        return 'PHARMACY'
    if any(x in name_str for x in ['أسنان', 'اسنان', 'dental', 'dentist']): 
        return 'CLINIC_DEN'
    if any(x in name_str for x in ['علاج طبيعي', 'فيزياء']): 
        return 'PHYSIOTHERAPY'
    if any(x in name_str for x in ['تحاليل', 'مختبر', 'lab']): 
        return 'LAB'
    if any(x in name_str for x in ['أشعة', 'اشعة', 'radiology']): 
        return 'RADIOLOGY'
    if any(x in name_str for x in ['مستشفى', 'مشفى', 'hospital']): 
        return 'HOSPITAL'
        
    return 'CLINIC'

template_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Providers_Import_Template.xlsx'
data_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx'

xl = pd.ExcelFile(template_path)
df_tmp = pd.read_excel(xl, sheet_name='Data', header=None)

header_idx = -1
for idx, row in df_tmp.iterrows():
    if any('الاسم' in str(cell) or 'name' in str(cell).lower() for cell in row.values):
        header_idx = idx
        break

if header_idx != -1:
    print(f"Found headers at row index {header_idx}")
    headers = df_tmp.iloc[header_idx].tolist()
    print("Headers:", headers)
    
    df_data = pd.read_excel(data_path)
    
    df_ready = pd.DataFrame(columns=headers)
    
    col_name = next((col for col in headers if 'الاسم' in str(col) or 'name' in str(col).lower()), None)
    col_license = next((col for col in headers if 'الترخيص' in str(col) or 'license' in str(col).lower()), None)
    col_type = next((col for col in headers if 'النوع' in str(col) or 'type' in str(col).lower()), None)
    col_city = next((col for col in headers if 'المدينة' in str(col) or 'city' in str(col).lower()), None)
    col_active = next((col for col in headers if 'نشط' in str(col) or 'الحالة' in str(col) or 'active' in str(col).lower()), None)
    col_network = next((col for col in headers if 'الشبكة' in str(col) or 'network' in str(col).lower()), None)
    
    if col_name: df_ready[col_name] = df_data['facility_name / اسم المرفق']
    if col_license: df_ready[col_license] = df_data['facility_code / كود المرفق']
    if col_type: df_ready[col_type] = df_data['facility_name / اسم المرفق'].apply(infer_provider_type)
    if col_city: df_ready[col_city] = 'Tripoli'
    if col_active: df_ready[col_active] = 'YES'
    if col_network: df_ready[col_network] = 'ضمن الشبكة'
    
    # Add allow_all_employers column to make it a global network by default
    df_ready['شبكة عامة\nallow_all_employers'] = 'نعم'
        
    clean_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Providers_Ready_To_Import_Clean.xlsx'
    df_ready.to_excel(clean_path, index=False)
    print("Saved to", clean_path)
else:
    print("Could not find headers in Data sheet!")
