import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const pageSource = readFileSync('src/pages/providers/ProviderStandardServicesPage.jsx', 'utf8');
const serviceSource = readFileSync('src/services/api/providerStandardServices.service.js', 'utf8');

/**
 * Bulk revoke is the inverse of bulk apply, but with a hard rule: it must
 * never touch an assignment that already has claim history, and any refusal
 * must name the exact provider and service, never a bare count. This pins
 * that the UI actually surfaces that reason text and wires the dedicated
 * revoke endpoints rather than reusing the apply ones with a flag.
 */
describe('provider standard services bulk revoke', () => {
  it('calls dedicated revoke endpoints, not the apply ones', () => {
    expect(serviceSource).toContain("axiosClient.post(`${BASE_URL}/revoke/preview`");
    expect(serviceSource).toContain("axiosClient.post(`${BASE_URL}/revoke/apply`");
  });

  it('requires an explicit confirmation dialog before an actual revoke write', () => {
    expect(pageSource).toContain('confirmRevokeOpen');
    expect(pageSource).toContain('setConfirmRevokeOpen(true)');
    expect(pageSource).toContain('تأكيد السحب الجماعي');
  });

  it('surfaces the no-financial-effect rule explicitly, naming provider and service', () => {
    expect(pageSource).toContain('BlockedAssignmentsList');
    expect(pageSource).toContain('b.providerName');
    expect(pageSource).toContain('b.serviceName');
    expect(pageSource).toContain('b.reason');
  });

  it('disables the revoke apply button when preview found nothing revokable', () => {
    expect(pageSource).toContain("mode !== 'REVOKE' || previewResult.assignmentsToRevoke > 0");
  });
});
