const http = require('http');

const loginData = JSON.stringify({ identifier: 'superadmin', password: 'Admin@123' });

http.request(
  { hostname: 'localhost', port: 8081, path: '/api/v1/auth/login', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': loginData.length } },
  (res) => {
    let body = '';
    res.on('data', d => body += d);
    res.on('end', () => {
      const token = JSON.parse(body)?.data?.token;
      const endpoints = [
        '/api/v1/medical-categories/all',
        '/api/v1/medical-categories?page=0&size=5',
        '/api/v1/provider-contracts?page=0&size=5',
      ];
      endpoints.forEach(path => {
        http.request(
          { hostname: 'localhost', port: 8081, path, method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` } },
          (r) => {
            let b = '';
            r.on('data', d => b += d);
            r.on('end', () => {
              const data = JSON.parse(b);
              const first = data?.data?.[0] || data?.data?.content?.[0];
              console.log(`\n${path}: HTTP ${r.statusCode}`);
              if (first) console.log('Sample:', JSON.stringify(first).substring(0, 200));
            });
          }
        ).end();
      });
    });
  }
).end(loginData);
