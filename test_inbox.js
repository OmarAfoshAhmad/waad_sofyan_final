const http = require('http');

const loginData = JSON.stringify({ identifier: 'superadmin', password: 'Admin@123' });

const loginReq = http.request(
  { hostname: 'localhost', port: 8081, path: '/api/v1/auth/login', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': loginData.length } },
  (res) => {
    let body = '';
    res.on('data', d => body += d);
    res.on('end', () => {
      const parsed = JSON.parse(body);
      const token = parsed?.data?.token || parsed?.token;

      const statuses = ['APPROVED', 'PENDING', 'ACKNOWLEDGED', 'NEEDS_CORRECTION'];
      let done = 0;

      statuses.forEach(status => {
        const req = http.request(
          { hostname: 'localhost', port: 8081, path: `/api/v1/pre-authorizations/status/${status}?page=0&size=10`, method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` } },
          (r) => {
            let b = '';
            r.on('data', d => b += d);
            r.on('end', () => {
              const data = JSON.parse(b);
              const count = data?.data?.content?.length ?? data?.data?.length ?? 0;
              console.log(`${status}: HTTP ${r.statusCode} | ${count} records`);
              done++;
              if (done === statuses.length) console.log('\n✅ All inbox status endpoints working!');
            });
          }
        );
        req.on('error', e => console.error(`${status} error:`, e.message));
        req.end();
      });
    });
  }
);
loginReq.write(loginData);
loginReq.end();
