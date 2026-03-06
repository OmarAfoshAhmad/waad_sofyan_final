import requests
import json

url_login = "http://localhost:8080/api/v1/auth/login"
login_data = {"identifier": "superadmin", "password": "Admin@123"}

session = requests.Session()
resp = session.post(url_login, json=login_data)

if resp.status_code == 200:
    token = resp.json().get('data', {}).get('token')
    headers = {"Authorization": f"Bearer {token}"}
    
    # 1. Get policies for employer 1
    # Check what policies exist
    resp_policies = session.get("http://localhost:8080/api/v1/benefit-policies/employer/1", headers=headers)
    print("POLICIES for Employer 1:")
    print(json.dumps(resp_policies.json(), indent=2))
    
    p = resp_policies.json().get('data', [{}])[0]
    policy_id = p.get('id')
    default_cov = p.get('defaultCoveragePercent')
    print(f"Policy ID: {policy_id}, Default Coverage: {default_cov}")

    if policy_id:
        # 2. Check coverage for service (supp-2)
        # We need a service ID. Let's find services from the contract.
        # But we can just try some IDs or listing services.
        pass
else:
    print("Login failed:", resp.status_code, resp.text)
