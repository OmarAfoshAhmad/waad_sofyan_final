
import re
import os

v88_f = r"backend/migration_archive/V88__seed_root_categories_and_specialties.sql"
v111_f = r"backend/migration_archive/V20260304_01__add_context_to_medical_categories.sql"

def get_categories(filename):
    if not os.path.exists(filename):
        return []
    with open(filename, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
        # Find tuples like ('CAT-CODE', 'NAME-AR', 'NAME-EN', ...)
        # We want the Arabic name and code
        # Format in V88: ('CAT-OPER', 'عمليات', 'عمليات', 'Surgeries', FALSE)
        # Format in V111: ('CAT-IP-GS', 'جراحة عامة - إيواء', 'جراحة عامة - إيواء', 'General Surgery (IP)', 'INPATIENT', true, NOW(), NOW())
        matches = re.findall(r"'\s*(CAT-[^']+)\s*'\s*,\s*'\s*([^']+)\s*'", content)
        return matches

cats_v88 = get_categories(v88_f)
cats_v111 = get_categories(v111_f)

all_cats = cats_v88 + cats_v111
print(f"Total categories: {len(all_cats)}")
for code, name in all_cats:
    print(f"{code} | {name}")
