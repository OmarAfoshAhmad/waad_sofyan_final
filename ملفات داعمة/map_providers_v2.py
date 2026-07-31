import pandas as pd

template_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Providers_Import_Template.xlsx'
data_path = 'd:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx'

# Read template without header to see rows
df_tmp = pd.read_excel(template_path, header=None)
print("Top rows of template:")
print(df_tmp.head(10))

# Usually header is at row 2 (index 1) or row 3 (index 2)
# Let's find the row that contains 'الاسم'
header_idx = -1
for idx, row in df_tmp.iterrows():
    if any('الاسم' in str(cell) for cell in row.values):
        header_idx = idx
        break

if header_idx != -1:
    print(f"Found headers at row index {header_idx}")
    headers = df_tmp.iloc[header_idx].tolist()
    print(headers)
    
    # Read data
    df_data = pd.read_excel(data_path)
    
    # Create mapped dataframe
    df_ready = pd.DataFrame(columns=headers)
    
    # Find matching columns by inspecting headers
    col_name = next((col for col in headers if 'الاسم' in str(col)), None)
    col_license = next((col for col in headers if 'الترخيص' in str(col)), None)
    col_type = next((col for col in headers if 'النوع' in str(col)), None)
    col_active = next((col for col in headers if 'نشط' in str(col) or 'الحالة' in str(col)), None)
    
    if col_name:
        df_ready[col_name] = df_data['facility_name / اسم المرفق']
    if col_license:
        df_ready[col_license] = df_data['facility_code / كود المرفق']
    if col_type:
        df_ready[col_type] = 'مستشفى' # Let's put hospital as default, or clinic
    if col_active:
        df_ready[col_active] = 'نعم'
        
    # Write to excel starting from the same header index
    with pd.ExcelWriter('d:/tba_waad_system-main_success/tba_waad_system-main/Providers_Ready_To_Import.xlsx') as writer:
        df_tmp.iloc[:header_idx].to_excel(writer, index=False, header=False) # Write title/instruction rows
        df_ready.to_excel(writer, startrow=header_idx, index=False)
        
    print("Done generating Providers_Ready_To_Import.xlsx")
else:
    print("Could not find headers in template!")
