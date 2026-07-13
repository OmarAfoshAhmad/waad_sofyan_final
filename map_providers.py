import pandas as pd

template_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Providers_Import_Template.xlsx'
data_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx'

# Read headers of template
df_template = pd.read_excel(template_path, nrows=0)
template_headers = df_template.columns.tolist()
print("Template Headers:", template_headers)

# Read data
df_data = pd.read_excel(data_path)
print("Data Headers:", df_data.columns.tolist())
print(f"Data rows: {len(df_data)}")

# Create a new dataframe with template columns
df_ready = pd.DataFrame(columns=template_headers)

# Map data
# 1. facility_name -> 'الاسم'
df_ready['الاسم'] = df_data['facility_name / اسم المرفق']
# 2. facility_code -> 'رقم الترخيص' (Assuming facility code can be used as license number if missing)
df_ready['رقم الترخيص'] = df_data['facility_code / كود المرفق']
# 3. providerType -> 'النوع' (We will set a default type like CLINIC or HOSPITAL, or just 'عيادة')
df_ready['النوع'] = 'عيادة' # Default type, or user can change it
# 4. active -> 'نشط'
if 'نشط' in template_headers:
    df_ready['نشط'] = 'نعم'
elif 'الحالة' in template_headers:
    df_ready['الحالة'] = 'نعم'

df_ready.to_excel('d:/tba_waad_system-main_success/tba_waad_system-main/Providers_Ready_To_Import.xlsx', index=False)
print("Saved to Providers_Ready_To_Import.xlsx")
