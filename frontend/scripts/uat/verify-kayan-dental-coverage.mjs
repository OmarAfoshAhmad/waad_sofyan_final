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

const calculate = (pricingItemId, price, categoryId) => api('/api/v1/claims/calculate-bulk', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ policyId: 1, memberId: 4, serviceYear: 2026,
    serviceDate: '2026-08-31', encounterType: 'OUTPATIENT', claimContextCode: 'OUTPATIENT',
    lines: [{ lineId: `KAYAN-${pricingItemId}`, pricingItemId, quantity: 1,
      enteredUnitPrice: price, contractPrice: price,
      categoryId, serviceCategoryId: categoryId, rejected: false, manualRefusedAmount: 0 }] })
});

const cosmetic = (await calculate(7695, 50, 5351))[0];
if (!cosmetic?.notCovered || Number(cosmetic.coveragePercent) !== 0
    || Number(cosmetic.companyShare) !== 0 || Number(cosmetic.patientShare) !== 50) {
  throw new Error(`Cosmetic dental must be explicitly uncovered: ${JSON.stringify(cosmetic)}`);
}

const routine = (await calculate(7608, 10, 4151))[0];
if (routine?.notCovered || Number(routine.coveragePercent) !== 75
    || Number(routine.companyShare) !== 7.5 || Number(routine.patientShare) !== 2.5) {
  throw new Error(`Routine dental 75/25 rule failed: ${JSON.stringify(routine)}`);
}

console.log(JSON.stringify({
  cosmetic: { service: 'إزالة تاج أو جسر (1)', pricingItemId: 7695,
    category: 'CAT-DENT-COSMETIC', coveragePercent: cosmetic.coveragePercent,
    companyShare: cosmetic.companyShare, patientShare: cosmetic.patientShare, notCovered: cosmetic.notCovered },
  routine: { service: 'كشف الطبيب العام', pricingItemId: 7608,
    category: 'CAT-DENT-ROUTINE', coveragePercent: routine.coveragePercent,
    companyShare: routine.companyShare, patientShare: routine.patientShare, notCovered: routine.notCovered }
}, null, 2));
