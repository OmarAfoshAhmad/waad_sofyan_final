import pandas as pd
import glob
import os
import re
import numpy as np

path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Unified_Benefit_Tables_Ready_V3/*.xlsx'
files = glob.glob(path)
files = [f for f in files if not os.path.basename(f).startswith('~$')]

def extract_number(s):
    if pd.isna(s):
        return pd.NA, ""
    s_str = str(s).strip()
    if s_str.isnumeric():
        return int(s_str), ""
    
    # Text mapping
    text_val = s_str
    num_val = pd.NA
    
    if 'نظارة كل سنتين' in s_str: num_val = 1
    elif 'كل 3 سنوات' in s_str: num_val = 1
    elif 'سنويا' in s_str: num_val = 1
    elif 'اذا دعت الضرورة ذلك' in s_str: num_val = pd.NA
    elif 'كل 5 سنوات' in s_str: num_val = 1
    elif 'واحدة في السنة' in s_str: num_val = 1
    else:
        # Extract first number
        match = re.search(r'\d+', s_str)
        if match:
            num_val = int(match.group())
            
    return num_val, f" (سقف المرات الأصلي: {text_val})"

def extract_amount(s):
    if pd.isna(s):
        return pd.NA, ""
    s_str = str(s).strip().replace(',', '')
    try:
        float(s_str)
        return float(s_str), ""
    except:
        pass
    
    text_val = str(s).strip()
    num_val = pd.NA
    
    if 'تغطية كاملة' in s_str:
        num_val = pd.NA
    else:
        match = re.search(r'\d+', s_str)
        if match:
            num_val = int(match.group())
            
    return num_val, f" (سقف المبلغ الأصلي: {text_val})"

for f in files:
    try:
        df = pd.read_excel(f)
        changed = False
        
        if 'ملاحظات' not in df.columns:
            df['ملاحظات'] = pd.NA
            
        if 'سقف المرات' in df.columns:
            for i, row in df.iterrows():
                val = row['سقف المرات']
                if pd.notna(val) and not str(val).isnumeric():
                    num, note = extract_number(val)
                    df.at[i, 'سقف المرات'] = num
                    if note:
                        existing_note = str(df.at[i, 'ملاحظات']) if pd.notna(df.at[i, 'ملاحظات']) else ""
                        if existing_note == "nan": existing_note = ""
                        df.at[i, 'ملاحظات'] = (existing_note + note).strip()
                    changed = True
                    
        if 'سقف المبلغ (د.ل)' in df.columns:
            for i, row in df.iterrows():
                val = row['سقف المبلغ (د.ل)']
                if pd.notna(val):
                    val_str = str(val).replace(',', '')
                    try:
                        float(val_str)
                    except:
                        num, note = extract_amount(val)
                        df.at[i, 'سقف المبلغ (د.ل)'] = num
                        if note:
                            existing_note = str(df.at[i, 'ملاحظات']) if pd.notna(df.at[i, 'ملاحظات']) else ""
                            if existing_note == "nan": existing_note = ""
                            df.at[i, 'ملاحظات'] = (existing_note + note).strip()
                        changed = True

        if changed:
            df.to_excel(f, index=False)
            print(f"Updated: {os.path.basename(f)}")
    except Exception as e:
        print(f"Error processing {os.path.basename(f)}: {e}")
