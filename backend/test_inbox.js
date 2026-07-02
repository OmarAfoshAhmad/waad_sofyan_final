const axios = require('axios');

async function testInbox() {
  try {
    const loginRes = await axios.post('http://localhost:8080/api/v1/auth/login', {
      username: 'superadmin',
      password: 'password'
    });
    
    const cookies = loginRes.headers['set-cookie'];
    
    const inboxRes = await axios.get('http://localhost:8080/api/v1/pre-authorizations/inbox/pending?status=PENDING&page=1&size=100', {
      headers: {
        Cookie: cookies.join('; ')
      }
    });
    
    console.log("INBOX STATUS:", inboxRes.status);
    console.log("INBOX DATA:", JSON.stringify(inboxRes.data, null, 2));
  } catch(e) {
    console.error("ERROR:", e.response ? e.response.data : e.message);
  }
}

testInbox();
