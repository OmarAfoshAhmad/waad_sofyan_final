const http = require('http');

const loginData = JSON.stringify({ identifier: 'superadmin', password: 'Admin@123' });

const loginReq = http.request(
  { hostname: 'localhost', port: 8081, path: '/api/v1/auth/login', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': loginData.length } },
  (res) => {
    let body = '';
    res.on('data', d => body += d);
    res.on('end', () => {
      const token = JSON.parse(body)?.data?.token;

      // Test medical services
      const req = http.request(
        { hostname: 'localhost', port: 8081, path: '/api/v1/medical-services?page=0&size=5', method: 'GET',
          headers: { 'Authorization': `Bearer ${token}` } },
        (r) => {
          let b = '';
          r.on('data', d => b += d);
          r.on('end', () => {
            const data = JSON.parse(b);
            const items = data?.data?.content || data?.data || [];
            console.log(`Medical Services: HTTP ${r.statusCode} | ${Array.isArray(items) ? items.length : 'N/A'} records`);
            if (Array.isArray(items) && items.length > 0) {
              const s = items[0];
              console.log('Sample service:', { id: s.id, code: s.code || s.serviceCode, name: s.name || s.serviceName, price: s.price || s.basePrice });
            }

            // Test eligibility check
            const eligData = JSON.stringify({ barcode: '20250011' });
            const req2 = http.request(
              { hostname: 'localhost', port: 8081, path: '/api/v1/provider/eligibility-check', method: 'POST',
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json', 'Content-Length': eligData.length } },
              (r2) => {
                let b2 = '';
                r2.on('data', d => b2 += d);
                r2.on('end', () => {
                  console.log(`\nEligibility check: HTTP ${r2.statusCode}`);
                  console.log('Response:', b2.substring(0, 300));
                });
              }
            );
            req2.write(eligData);
            req2.end();
          });
        }
      );
      req.end();
    });
  }
);
loginReq.write(loginData);
loginReq.end();
