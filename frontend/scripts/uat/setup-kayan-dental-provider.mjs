import process from 'node:process';

const baseUrl = process.env.BASE_URL || 'http://localhost:8081';
const password = process.env.UAT_PASSWORD;
if (!password) throw new Error('UAT_PASSWORD is required');
let cookie = '';
let xsrf = '';
const api = async (path, options = {}) => {
  const headers = { ...(options.headers || {}) };
  if (cookie) headers.Cookie = cookie;
  if (xsrf && options.method && options.method !== 'GET') headers['X-XSRF-TOKEN'] = xsrf;
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  for (const line of response.headers.getSetCookie?.() || []) {
    const [pair] = line.split(';');
    const [name, value] = pair.split(/=(.*)/s).slice(0, 2);
    const values = new Map(cookie.split('; ').filter(Boolean).map((item) => item.split(/=(.*)/s).slice(0, 2)));
    values.set(name, value);
    cookie = [...values].map(([key, stored]) => `${key}=${stored}`).join('; ');
    if (name === 'XSRF-TOKEN') xsrf = decodeURIComponent(value);
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${options.method || 'GET'} ${path}: ${response.status} ${JSON.stringify(body)}`);
  return body.data;
};

await api('/api/v1/auth/session/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ identifier: 'superadmin@tba.sa', password, rememberMe: false })
});

const provider = process.env.PROVIDER_ID
  ? { id: Number(process.env.PROVIDER_ID), name: 'مركز كيان للأسنان - UAT' }
  : await api('/api/v1/providers', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'مركز كيان للأسنان - UAT', city: 'بنغازي',
    licenseNumber: 'UAT-KAYAN-DENTAL-001', providerType: 'CLINIC',
    networkStatus: 'IN_NETWORK', allowAllEmployers: true })
  });
const contract = await api('/api/v1/provider-contracts', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ providerId: provider.id, pricingScope: 'GLOBAL',
    contractCode: `UAT-KAYAN-${provider.id}`, status: 'ACTIVE', pricingModel: 'FIXED',
    discountPercent: 0, startDate: '2026-08-31', endDate: '2027-08-31',
    signedDate: '2026-08-31', currency: 'LYD', notes: 'بيئة اختبار قائمة كيان للأسنان' })
});
console.log(JSON.stringify({ providerId: provider.id, providerName: provider.name,
  contractId: contract.id, contractCode: contract.contractCode }, null, 2));
