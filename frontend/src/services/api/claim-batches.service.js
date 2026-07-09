import axiosClient from 'utils/axios';

const BASE = '/claim-batches';

const claimBatchesService = {
  /**
   * READ-ONLY: returns existing batch (null/404 if not opened yet).
   * Does NOT auto-create a new batch.
   */
  getCurrentBatch: async (providerId, employerId, year, month) => {
    try {
      const response = await axiosClient.get(`${BASE}/current`, {
        params: { providerId, employerId, year, month }
      });
      return response.data;
    } catch (err) {
      // 404 = no batch exists yet (normal state before first claim)
      if (err?.response?.status === 404) return null;
      throw err;
    }
  },

  /**
   * CREATES (or retrieves) the monthly batch for this provider+employer+period.
   * Call this when the user intentionally wants to open a batch (e.g., first save).
   */
  openOrGetBatch: async (providerId, employerId, year, month) => {
    const response = await axiosClient.post(`${BASE}/current`, null, {
      params: { providerId, employerId, year, month }
    });
    return response.data;
  },

  /**
   * Search batches by employer and period.
   */
  list: async (params) => {
    const response = await axiosClient.get(BASE, { params });
    return response.data;
  }
};

export default claimBatchesService;
