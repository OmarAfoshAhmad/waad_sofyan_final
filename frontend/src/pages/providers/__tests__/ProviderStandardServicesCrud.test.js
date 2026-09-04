import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const pageSource = readFileSync('src/pages/providers/ProviderStandardServicesPage.jsx', 'utf8');
const serviceSource = readFileSync('src/services/api/providerStandardServices.service.js', 'utf8');
const dialogSource = readFileSync('src/pages/providers/components/StandardServiceFormDialog.jsx', 'utf8');

/**
 * P5: creating/editing the standard-service catalog itself (not just
 * assigning it to providers). code is a MedicalService business identifier
 * and, per its backend javadoc, immutable once created -- the create form
 * must not silently allow editing it.
 */
describe('provider standard services catalog CRUD', () => {
  it('wires dedicated create/update endpoints, not the provisioning ones', () => {
    expect(serviceSource).toContain('create: async (payload) => unwrap(await axiosClient.post(BASE_URL, payload))');
    expect(serviceSource).toContain('update: async (id, payload) => unwrap(await axiosClient.patch(');
    expect(serviceSource).toContain("listAll: async () => unwrap(await axiosClient.get(`${BASE_URL}/all`))");
  });

  it('disables the code field while editing an existing service', () => {
    expect(dialogSource).toContain('disabled={isEdit}');
  });

  it('requires code, name, and category before allowing submit', () => {
    expect(dialogSource).toContain('const isValid = form.code.trim() && form.nameAr.trim() && form.categoryId;');
  });

  it('omits code from the update payload -- the backend treats it as immutable', () => {
    expect(dialogSource).toContain('...(isEdit ? {} : { code: form.code.trim() })');
  });

  it('invalidates both the admin and the active-only catalog query after create/update', () => {
    expect(pageSource).toContain("queryClient.invalidateQueries({ queryKey: STANDARD_SERVICES_QUERY_KEY })");
    expect(pageSource).toContain("queryClient.invalidateQueries({ queryKey: STANDARD_SERVICES_ADMIN_QUERY_KEY })");
    expect(pageSource).toContain('invalidateCatalog();');
    expect(pageSource).toContain('mutationFn: providerStandardServicesService.create');
    expect(pageSource).toContain('mutationFn: ({ id, payload }) => providerStandardServicesService.update(id, payload)');
  });
});
