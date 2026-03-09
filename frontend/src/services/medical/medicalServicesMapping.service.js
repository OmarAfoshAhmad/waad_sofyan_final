/**
 * Medical Services Mapping API Service
 * Backend: /api/v1/medical-services-mapping
 */
import axiosClient from 'utils/axios';

const BASE = '/medical-services-mapping';
const unwrap = (res) => res.data?.data ?? res.data;

const medicalServicesMappingService = {
  /**
   * GET /stats — aggregate counts
   * @returns {Promise<{total,pending,mapped,rejected,providersWithRawServices,medicalServicesTotal}>}
   */
  getStats: async () => {
    const res = await axiosClient.get(`${BASE}/stats`);
    return unwrap(res);
  },

  /**
   * POST /create-and-map — create new service + map raw services
   * @param {{ code, name, categoryId, rawServiceIds: number[] }} payload
   */
  createAndMap: async (payload) => {
    const res = await axiosClient.post(`${BASE}/create-and-map`, payload);
    return unwrap(res);
  },

  /**
   * POST /link-and-map — link raw services to existing service
   * @param {{ medicalServiceId: number, rawServiceIds: number[] }} payload
   */
  linkAndMap: async (payload) => {
    const res = await axiosClient.post(`${BASE}/link-and-map`, payload);
    return unwrap(res);
  }
};

export default medicalServicesMappingService;
