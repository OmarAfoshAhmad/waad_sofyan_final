import assert from 'node:assert/strict';
import {
  failedCoverageResult,
  normalizeCoverageResult
} from '../src/pages/claims/batches/hooks/coverageContract.mjs';

const missing = normalizeCoverageResult(null);
assert.equal(missing.coveragePercent, 0);
assert.equal(missing.byCompany, 0);
assert.equal(missing.notCovered, true);
assert.equal(missing.coveragePending, true);

const incomplete = normalizeCoverageResult({ coveragePercent: 80 });
assert.equal(incomplete.byCompany, 0);
assert.equal(incomplete.coveragePending, true);

const explicit = normalizeCoverageResult({
  coveragePercent: 80,
  companyShare: 80,
  patientShare: 20,
  requestedTotal: 100,
  refusedAmount: 0,
  priceRefused: 0,
  limitRefused: 0,
  systemRefusedAmount: 0,
  notCovered: false,
  usageDetails: null
});
assert.equal(explicit.coveragePercent, 80);
assert.equal(explicit.byCompany, 80);
assert.equal(explicit.byEmployee, 20);
assert.equal(explicit.coveragePending, false);

const failed = failedCoverageResult('network error');
assert.equal(failed.byCompany, 0);
assert.equal(failed.rejectionReason, 'network error');

console.log('coverage-contract: 4/4 passed');
