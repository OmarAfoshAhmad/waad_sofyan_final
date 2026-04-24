import axiosClient from 'utils/axios';

const BASE_URL = '/admin/claim-coverage-rules';

const coverageRuleEngineService = {
  getAll: async () => {
    const response = await axiosClient.get(BASE_URL);
    return response.data;
  },

  create: async (payload) => {
    const response = await axiosClient.post(BASE_URL, payload);
    return response.data;
  },

  update: async (id, payload) => {
    const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
    return response.data;
  },

  remove: async (id) => {
    await axiosClient.delete(`${BASE_URL}/${id}`);
  }
};

export default coverageRuleEngineService;
