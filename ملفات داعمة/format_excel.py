import pandas as pd

input_path = "d:/tba_waad_system-main_success/tba_waad_system-main/قائمة التصنيفات المعتمدة النهائي_كامل.xlsx"
output_path = "d:/tba_waad_system-main_success/tba_waad_system-main/قائمة_التصنيفات_جاهز_للاستيراد.xlsx"

try:
    # Read the file skipping the first title row
    df = pd.read_excel(input_path, skiprows=1)
    
    # Rename column to match backend expectations
    if "التصنيف الأب" in df.columns:
        df.rename(columns={"التصنيف الأب": "رمز التصنيف الأب"}, inplace=True)
        
    # Save the modified file ready for import
    df.to_excel(output_path, index=False)
    print("Success: File is ready at " + output_path)
    print("New Headers:", df.columns.tolist())
except Exception as e:
    print("Error:", e)
