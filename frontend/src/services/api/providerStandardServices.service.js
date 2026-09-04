import axiosClient from 'utils/axios';

const BASE_URL = '/provider-standard-services';

const unwrap = (response) => response.data?.data || response.data;

/**
 * Bulk provisioning of standard (invoice-priced) services across providers.
 * Preview and apply share one request shape -- the operation is
 * deterministic, so there is no upload/session state between the two calls.
 */
export const providerStandardServicesService = {
  list: async () => unwrap(await axiosClient.get(BASE_URL)),

  // Includes inactive services -- the admin catalog-management table, not
  // the assignment picker (which must only ever offer active ones).
  listAll: async () => unwrap(await axiosClient.get(`${BASE_URL}/all`)),

  create: async (payload) => unwrap(await axiosClient.post(BASE_URL, payload)),

  update: async (id, payload) => unwrap(await axiosClient.patch(`${BASE_URL}/${id}`, payload)),

  previewProvisioning: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/preview`, request)),

  applyProvisioning: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/apply`, request)),

  previewRevoke: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/revoke/preview`, request)),

  applyRevoke: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/revoke/apply`, request))
};

export default providerStandardServicesService;
