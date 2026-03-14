import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

// ==============================|| EMAIL PRE-AUTH SERVICE ||============================== //

const BASE_URL = '/pre-auth/emails';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for email pre-auth service
 */
const handleError = createErrorHandler('طلبات البريد', {
  404: 'الطلب غير موجود',
  403: 'ليس لديك صلاحية الوصول',
  500: 'خطأ في الخادم أثناء العمل مع طلبات البريد'
});

export const emailPreAuthService = {
  /**
   * Get all email requests with pagination
   * @param {Object} params - Optional pagination params {page, size, processed}
   * @returns {Promise<Object>} Paginated requests
   */
  getAll: async (params = {}) => {
    try {
      const response = await axiosClient.get(BASE_URL, { params });
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Get specific request by ID
   * @param {number} id - Request ID
   * @returns {Promise<Object>} Request details
   */
  getById: async (id) => {
    try {
      if (!id) throw new Error('معرف الطلب مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Fetch new emails from server (Trigger IMAP process)
   */
  fetchFromEmailServer: async () => {
    try {
      await axiosClient.post(`${BASE_URL}/fetch`);
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Mark request as processed and link to pre-auth
   */
  markProcessed: async (id, preAuthId) => {
    try {
      await axiosClient.post(`${BASE_URL}/${id}/mark-processed`, null, { params: { preAuthId } });
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Approve email request and create pre-auth
   */
  approve: async (id, memberId, serviceId, notes) => {
    try {
      const params = { memberId, serviceId };
      if (notes) params.notes = notes;
      const response = await axiosClient.post(`${BASE_URL}/${id}/approve`, null, { params });
      return unwrap(response);
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Delete email request
   * @param {number} id - Request ID
   * @returns {Promise<void>}
   */
  remove: async (id) => {
    try {
      if (!id) throw new Error('معرف الطلب مطلوب');
      await axiosClient.delete(`${BASE_URL}/${id}`);
    } catch (error) {
      throw handleError(error);
    }
  },

  /**
   * Re-identify email request components (Provider, Member, Service)
   * @param {number} id - Request ID
   */
  reidentify: async (id) => {
    try {
      if (!id) throw new Error('معرف الطلب مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/reidentify`);
      return unwrap(response);
    } catch (error) {
      throw handleError(error);
    }
  }
};

export default emailPreAuthService;
