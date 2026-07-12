import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

const BASE_URL = '/reviewer/preauths';

const handleReviewerErrors = createErrorHandler('المراجع', {
  404: 'الموافقة المسبقة غير موجودة',
  403: 'ليس لديك صلاحية مراجعة هذا الطلب',
  400: 'خطأ في العملية أو الطلب'
});

const unwrap = (response) => response.data?.data || response.data;

export const reviewerPreAuthService = {
  /**
   * جلب صندوق الوارد للمراجع (Inbox)
   */
  getInbox: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.filterStatus) queryParams.append('filterStatus', params.filterStatus);
      if (params.hasVariance) queryParams.append('hasVariance', params.hasVariance);

      const url = queryParams.toString() ? `${BASE_URL}/inbox?${queryParams.toString()}` : `${BASE_URL}/inbox`;
      const response = await axiosClient.get(url);
      
      // Temporary fallback until backend getInbox is fully implemented, we unwrap the data
      const data = unwrap(response);
      return Array.isArray(data) ? { items: data, total: data.length, page: 1, size: data.length } : data;
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * جلب قائمة سطور الموافقة
   */
  getLines: async (id) => {
    try {
      const response = await axiosClient.get(`${BASE_URL}/${id}/lines`);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * بدء المراجعة (PENDING -> UNDER_REVIEW)
   */
  startReview: async (id) => {
    try {
      const response = await axiosClient.post(`${BASE_URL}/${id}/start-review`);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * اتخاذ قرار على سطر خدمة محدد
   */
  makeLineDecision: async (id, lineId, decisionDto) => {
    try {
      const response = await axiosClient.post(`${BASE_URL}/${id}/lines/${lineId}/decision`, decisionDto);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * إنهاء المراجعة
   */
  finalizeReview: async (id) => {
    try {
      const response = await axiosClient.post(`${BASE_URL}/${id}/finalize`);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * رفض كلي (Shortcut)
   */
  rejectAll: async (id, reason) => {
    try {
      const response = await axiosClient.post(`${BASE_URL}/${id}/reject?reason=${encodeURIComponent(reason)}`);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  },

  /**
   * طلب معلومات إضافية
   */
  requestInfo: async (id, notes) => {
    try {
      const response = await axiosClient.post(`${BASE_URL}/${id}/request-info?notes=${encodeURIComponent(notes)}`);
      return unwrap(response);
    } catch (error) {
      throw handleReviewerErrors(error);
    }
  }
};

export default reviewerPreAuthService;
