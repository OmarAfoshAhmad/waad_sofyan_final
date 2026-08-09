import axiosClient from 'utils/axios';

const unwrap = (response) => response.data?.data || response.data;
const baseUrl = (claimId) => `/claims/${claimId}/pending-services`;

export const claimPendingServicesService = {
  list: async (claimId) => unwrap(await axiosClient.get(baseUrl(claimId))),
  create: async (claimId, payload) => unwrap(await axiosClient.post(baseUrl(claimId), payload)),
  decide: async (claimId, pendingId, payload) => unwrap(await axiosClient.post(`${baseUrl(claimId)}/${pendingId}/decision`, payload))
};

export default claimPendingServicesService;
