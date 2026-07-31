with open('backend/logs/app-error.log', 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()
print("--- LATEST ERROR LOGS ---")
for line in lines[-40:]:
    print(line.strip())
