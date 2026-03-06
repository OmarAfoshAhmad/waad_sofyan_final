import requests
import json

url = "http://localhost:8080/api/v1/claims?employerId=1&providerId=1&dateFrom=2026-03-01&dateTo=2026-03-31&size=20&page=0&sortBy=createdAt&sortDir=desc"

# We need a token. Let's login as superadmin first.
login_data = {"identifier": "superadmin", "password": "password"}
resp = requests.post("http://localhost:8080/api/v1/auth/login", json=login_data)

if resp.status_code == 200:
    token = resp.json().get('data', {}).get('token')
    headers = {"Authorization": f"Bearer {token}"}
    
    claims_resp = requests.get(url, headers=headers)
    print("STATUS CODE:", claims_resp.status_code)
    try:
        print(json.dumps(claims_resp.json(), indent=2))
    except Exception as e:
        print("Error parsing JSON:", e)
        print(claims_resp.text)
else:
    print("Login failed:", resp.status_code, resp.text)
