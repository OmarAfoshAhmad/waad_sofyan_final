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

const providers = [
  { id: 1, name: 'بنغازي التخصصي' },
  { id: 51, name: 'دار الحكمة' },
  { id: 101, name: 'دار الشفاء' }
];
const results = [];
for (const provider of providers) {
  const items = [];
  for (let page = 0; ; page += 1) {
    const data = await api(`/api/v1/claims/entry-services?memberId=4&providerId=${provider.id}&employerId=1&serviceDate=2026-08-31&page=${page}&size=500`);
    items.push(...data.content);
    if (data.last) break;
  }
  const representatives = new Map();
  for (const item of items) {
    const category = item.effectiveCategory;
    if (!category?.id || !item.claimContextCode) throw new Error(`${provider.name}: item ${item.id} lacks category/context`);
    representatives.set(`${item.claimContextCode}|${category.code}`, item);
  }
  for (const [key, item] of representatives) {
    const response = await api('/api/v1/claims/calculate-bulk', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        policyId: 1, memberId: 4, serviceYear: 2026, serviceDate: '2026-08-31',
        encounterType: item.encounterType, claimContextCode: item.claimContextCode,
        lines: [{ lineId: `${provider.id}-${item.id}`, pricingItemId: item.id,
          quantity: 1, enteredUnitPrice: item.contractPrice, contractPrice: item.contractPrice,
          categoryId: item.effectiveCategory.id, serviceCategoryId: item.effectiveCategory.id,
          rejected: false, manualRefusedAmount: 0 }]
      })
    });
    const line = response[0];
    if (item.effectiveCategory.code === 'CAT-DENT-COSMETIC') {
      if (!line?.notCovered || Number(line.coveragePercent) !== 0
          || Number(line.companyShare) !== 0 || Number(line.patientShare) !== Number(item.contractPrice)) {
        throw new Error(`${provider.name} ${key}: cosmetic dental was not fully assigned to patient ${JSON.stringify(line)}`);
      }
      results.push({ provider: provider.name, key, service: item.serviceName,
        price: item.contractPrice, coverage: 0, expected: 'NOT_COVERED' });
      continue;
    }
    if (!line || line.coveragePercent == null || Number(line.coveragePercent) <= 0 || line.notCovered) {
      throw new Error(`${provider.name} ${key}: uncovered ${JSON.stringify(line)}`);
    }
    results.push({ provider: provider.name, key, service: item.serviceName,
      price: item.contractPrice, coverage: line.coveragePercent,
      bucket: line.usageDetails?.bucketName || null,
      limit: line.usageDetails?.amountLimit ?? null,
      basis: line.usageDetails?.consumptionBasis ?? null });
  }
}

const calculate = (claimContextCode, encounterType, items) => api('/api/v1/claims/calculate-bulk', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    policyId: 1, memberId: 4, serviceYear: 2026, serviceDate: '2026-08-31',
    encounterType, claimContextCode,
    lines: items.map((item, index) => ({ lineId: `${claimContextCode}-${index}`,
      pricingItemId: item.id, quantity: item.quantity || 1,
      enteredUnitPrice: item.price, contractPrice: item.price,
      categoryId: item.categoryId, serviceCategoryId: item.categoryId,
      rejected: false, manualRefusedAmount: 0 }))
  })
});

const maternity = await calculate('MATERNITY', 'INPATIENT', [
  { id: 6634, price: 1650, categoryId: 5251, quantity: 1 },
  { id: 5376, price: 300, categoryId: 5251, quantity: 10 }
]);
if (Number(maternity[1].limitRefused) !== 650 || Number(maternity[1].companyShare) !== 1762.5
    || Number(maternity[1].patientShare) !== 587.5) {
  throw new Error(`Maternity 4,000 gross cap failed: ${JSON.stringify(maternity)}`);
}

const mri = await calculate('OUTPATIENT', 'OUTPATIENT', [
  { id: 6328, price: 1300, categoryId: 3551 },
  { id: 5649, price: 1000, categoryId: 3551 }
]);
if (Number(mri[1].limitRefused) !== 800 || Number(mri[1].companyShare) !== 150
    || Number(mri[1].patientShare) !== 50
    || mri[1].usageDetails?.consumptionBasis !== 'ELIGIBLE_AMOUNT') {
  throw new Error(`MRI 1,500 gross cap failed: ${JSON.stringify(mri)}`);
}

const physioLines = Array.from({ length: 21 }, () => ({ id: 6534, price: 160, categoryId: 3501 }));
const physio = await calculate('OUTPATIENT', 'OUTPATIENT', physioLines);
if (physio[20].usageDetails?.usedCount !== 1 || physio[20].usageDetails?.timesExceeded
    || Number(physio[20].companyShare) !== 120) {
  throw new Error(`Physiotherapy PER_VISIT batch semantics failed: ${JSON.stringify(physio.slice(-2))}`);
}

const diagnostics = await calculate('OUTPATIENT', 'OUTPATIENT', [
  { id: 6289, price: 10800, categoryId: 5301 }
]);
if (Number(diagnostics[0].companyShare) !== 2250 || Number(diagnostics[0].patientShare) !== 750
    || Number(diagnostics[0].limitRefused) !== 7800
    || diagnostics[0].usageDetails?.consumptionBasis !== 'ELIGIBLE_AMOUNT') {
  throw new Error(`Shared diagnostics/fees 3,000 gross cap failed: ${JSON.stringify(diagnostics)}`);
}

console.log(JSON.stringify({ checked: results.length, scenarios: {
  maternityGrossCap: '4000', mriGrossCap: '1500',
  physiotherapyBatch: '21 lines = 1 visit; historical visit 21 is covered by backend test',
  diagnosticsSharedGrossCap: '3000'
}, results }, null, 2));
