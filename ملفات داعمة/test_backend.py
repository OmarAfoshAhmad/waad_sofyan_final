import urllib.request
import json
import uuid

# Login
login_data = json.dumps({"username": "superadmin", "password": "password"}).encode('utf-8')
req = urllib.request.Request("http://localhost:8081/api/auth/login", data=login_data, headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(req) as response:
    resp_body = response.read().decode('utf-8')
    token = json.loads(resp_body).get("accessToken")

# File Upload
boundary = uuid.uuid4().hex
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': f'multipart/form-data; boundary={boundary}'
}

with open("Price_List_Contract_1_Output.xlsx", "rb") as f:
    file_content = f.read()

data = []
data.append(f'--{boundary}'.encode('utf-8'))
data.append('Content-Disposition: form-data; name="file"; filename="Price_List_Contract_1_Output.xlsx"'.encode('utf-8'))
data.append('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'.encode('utf-8'))
data.append(b'')
data.append(file_content)
data.append(f'--{boundary}--'.encode('utf-8'))
data.append(b'')

body = b'\r\n'.join(data)

req = urllib.request.Request("http://localhost:8081/api/provider-contracts/101/pricing/import/preview", data=body, headers=headers)
try:
    with urllib.request.urlopen(req) as response:
        print(response.status)
        print(response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(e.code)
    print(e.read().decode('utf-8'))
