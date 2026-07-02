const http = require('http');

const req = http.request({
  hostname: 'localhost',
  port: 8081,
  path: '/api/v1/auth/session/login',
  method: 'POST',
  headers: { 'Content-Type': 'application/json' }
}, (res) => {
  const cookies = res.headers['set-cookie'];
  
  const req2 = http.request({
    hostname: 'localhost',
    port: 8081,
    path: '/api/v1/pre-authorizations/dashboard/high-priority?limit=10',
    method: 'GET',
    headers: { 'Cookie': cookies[0] }
  }, (res2) => {
    let data = '';
    res2.on('data', d => data += d);
    res2.on('end', () => console.log('DASHBOARD RESPONSE:', data));
  });
  req2.end();
});

req.write(JSON.stringify({email: 'superadmin@tba.com', password: 'password'}));
req.end();
