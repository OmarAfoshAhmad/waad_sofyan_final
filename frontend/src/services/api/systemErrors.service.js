/**
 * System Error Log API Service
 * Backend (500) and frontend errors surfaced to users, with resolve/reopen tracking.
 *
 * API Base: /api/v1/system-errors  (listing/detail/resolve: SUPER_ADMIN only)
 */

import axiosClient from 'utils/axios';

const BASE_URL = '/system-errors';

const unwrap = (response) => response?.data?.data ?? response?.data;

const systemErrorsService = {
  list: async (params = {}) => {
    const response = await axiosClient.get(BASE_URL, { params });
    return unwrap(response);
  },

  get: async (id) => {
    const response = await axiosClient.get(`${BASE_URL}/${id}`);
    return unwrap(response);
  },

  unresolvedCount: async () => {
    const response = await axiosClient.get(`${BASE_URL}/unresolved-count`);
    return unwrap(response);
  },

  resolve: async (id, { resolved = true, notes } = {}) => {
    const response = await axiosClient.patch(`${BASE_URL}/${id}/resolve`, { resolved, notes });
    return unwrap(response);
  }
};

export default systemErrorsService;
