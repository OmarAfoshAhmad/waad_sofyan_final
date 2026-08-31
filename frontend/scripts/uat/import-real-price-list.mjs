import fs from 'node:fs';
import process from 'node:process';
import * as XLSX from 'xlsx';
import { extractRowsFromWorkbook } from '../../src/pages/price-list-classifier/price-list-workbook.mjs';

const args = Object.fromEntries(process.argv.slice(2).map((arg, index, all) => {
  if (!arg.startsWith('--')) return null;
  return [arg.slice(2), all[index + 1]?.startsWith('--') ? true : all[index + 1]];
}).filter(Boolean));

for (const required of ['file', 'provider-id', 'provider-name', 'contract-id', 'contract-code', 'password']) {
  if (!args[required]) throw new Error(`Missing --${required}`);
}

const baseUrl = args['base-url'] || 'http://localhost:8081';
const loginIdentifier = args.user || 'superadmin@tba.sa';
let cookie = '';
let xsrf = '';

const api = async (path, options = {}) => {
  const headers = { ...(options.headers || {}) };
  if (cookie) headers.Cookie = cookie;
  if (xsrf && options.method && options.method !== 'GET') headers['X-XSRF-TOKEN'] = xsrf;
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  const setCookies = response.headers.getSetCookie?.() || [];
  if (setCookies.length) {
    const values = new Map(cookie.split('; ').filter(Boolean).map((item) => item.split(/=(.*)/s).slice(0, 2)));
    for (const line of setCookies) {
      const [pair] = line.split(';');
      const [name, value] = pair.split(/=(.*)/s).slice(0, 2);
      values.set(name, value);
      if (name === 'XSRF-TOKEN') xsrf = decodeURIComponent(value);
    }
    cookie = [...values].map(([name, value]) => `${name}=${value}`).join('; ');
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${options.method || 'GET'} ${path}: ${response.status} ${JSON.stringify(body)}`);
  return body.data;
};

await api('/api/v1/auth/session/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ identifier: loginIdentifier, password: args.password, rememberMe: false })
});

const workbook = XLSX.read(fs.readFileSync(args.file), { type: 'buffer', cellDates: true });
const rows = extractRowsFromWorkbook(workbook, XLSX);
if (!rows.length) throw new Error('The workbook produced no valid priced service rows');

const classified = [];
for (let offset = 0; offset < rows.length; offset += 1000) {
  const result = await api('/api/v1/medical-dictionary/price-lists/classify', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ providerId: Number(args['provider-id']), providerName: args['provider-name'],
      rows: rows.slice(offset, offset + 1000) })
  });
  classified.push(...result.items);
}

const reviewRows = classified.filter((item) => !item.postable);
if (reviewRows.length) {
  const classifications = [...new Set(reviewRows.map((item) => item.sourceClassification))];
  throw new Error(`Fail closed: ${reviewRows.length} rows need review; source classifications: ${classifications.join(' | ')}`);
}

const session = await api('/api/v1/medical-dictionary/price-lists/sessions', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sessionName: `UAT حقيقي - ${args['provider-name']}`,
    originalFileName: args.file.split(/[\\/]/).pop(),
    providerId: Number(args['provider-id']), providerName: args['provider-name'],
    contractId: Number(args['contract-id']), contractCode: args['contract-code'],
    notes: 'استيراد UAT عبر المسار الإنتاجي من تصنيف قائمة المرفق إلى سياق المطالبة وتصنيف التغطية',
    items: classified.map((item) => ({
      rowNumber: item.rowNumber, sourceSheet: item.sourceSheet,
      sourceClassification: item.sourceClassification, serviceCode: item.serviceCode,
      serviceName: item.serviceName, canonicalName: item.canonicalName,
      claimContextCode: item.claimContextCode,
      medicalCategoryId: item.bestMatch?.medicalCategoryId,
      medicalCategoryCode: item.categoryCode, medicalCategoryName: item.categoryName,
      confidence: item.bestMatch?.confidence, classificationMethod: item.matchMethod,
      classificationReason: item.reason, classificationExcludePrecision: false,
      status: item.status, price: item.price, minPrice: item.minPrice,
      maxPrice: item.maxPrice, priceLabel: item.priceLabel,
      duplicateName: item.duplicateName, mergedDuplicate: false, mergedSourceCount: 1
    }))
  })
});

const postRequest = { contractId: Number(args['contract-id']), effectiveFrom: '2026-08-31',
  replaceEffectivePrices: false, onlyReviewedItems: true };
const diff = await api(`/api/v1/medical-dictionary/price-lists/sessions/${session.id}/diff-contract`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(postRequest)
});
if (diff.rejectedCount) {
  const rejected = diff.items.filter((item) => item.action === 'REJECTED')
    .map((item) => `row=${item.rowNumber} code=${item.serviceCode || '-'} name=${item.serviceName}: ${item.message}`);
  throw new Error(`Fail closed before posting: ${diff.rejectedCount} rows rejected\n${rejected.join('\n')}`);
}

const posted = await api(`/api/v1/medical-dictionary/price-lists/sessions/${session.id}/post-to-contract`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(postRequest)
});
if (posted.rejected) throw new Error(`Posting rejected ${posted.rejected} rows after a clean diff`);

console.log(JSON.stringify({ provider: args['provider-name'], extracted: rows.length,
  sessionId: session.id, created: posted.created, updated: posted.updated,
  skipped: posted.skipped, rejected: posted.rejected }, null, 2));
