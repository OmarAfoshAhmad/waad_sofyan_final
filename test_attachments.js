const http = require('http');

// Step 1: Login with JWT
const loginData = JSON.stringify({ identifier: 'superadmin', password: 'Admin@123' });
const loginReq = http.request(
  { hostname: 'localhost', port: 8081, path: '/api/v1/auth/login', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': loginData.length } },
  (res) => {
    let body = '';
    res.on('data', d => body += d);
    res.on('end', () => {
      console.log('Login status:', res.statusCode);
      try {
        const parsed = JSON.parse(body);
        const token = parsed?.data?.token || parsed?.token;
        console.log('Token obtained:', token ? 'YES' : 'NO');

        if (!token) {
          console.log('Login response:', body.substring(0, 300));
          return;
        }

        // Step 2: Test attachments endpoint
        const req2 = http.request(
          { hostname: 'localhost', port: 8081, path: '/api/v1/pre-authorizations/3/attachments', method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` } },
          (res2) => {
            let body2 = '';
            res2.on('data', d => body2 += d);
            res2.on('end', () => {
              console.log('Attachments status:', res2.statusCode);
              console.log('Response:', body2.substring(0, 500));
            });
          }
        );
        req2.on('error', e => console.error('Req2 error:', e.message));
        req2.end();
      } catch(e) {
        console.log('Parse error:', e.message, 'Body:', body.substring(0,200));
      }
    });
  }
);
loginReq.on('error', e => console.error('Login error:', e.message));
loginReq.write(loginData);
loginReq.end();
