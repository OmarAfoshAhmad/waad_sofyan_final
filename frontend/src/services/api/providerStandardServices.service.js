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

  previewProvisioning: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/preview`, request)),

  applyProvisioning: async (request) => unwrap(await axiosClient.post(`${BASE_URL}/apply`, request))
};

export default providerStandardServicesService;
