import pandas as pd
import json

def get_excel_info(file_path):
    try:
        df = pd.read_excel(file_path)
        return {
            "columns": df.columns.tolist(),
            "head": df.head(5).to_dict(orient="records")
        }
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    file_path = r"d:\tba_waad_system-main\tba_waad_system-main\Excel Files\جدول_منافع_مصلحة_الجمارك.xlsx"
    info = get_excel_info(file_path)
    with open("excel_info.json", "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)
    print("Done")
