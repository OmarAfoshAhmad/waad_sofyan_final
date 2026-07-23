import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';

// ==============================|| VISITS SERVICE ||============================== //
// API Contract: VISIT_API_CONTRACT.md
// Base URL: /api/visits
// Pagination: 1-based, uses sortBy/sortDir

const BASE_URL = '/visits';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for visits service
 * Provides user-friendly Arabic error messages
 */
const handleVisitErrors = createErrorHandler('الزيارة', {
  404: 'الزيارة غير موجودة',
  409: 'يوجد تعارض في بيانات الزيارة',
  422: 'البيانات المُدخلة للزيارة غير صحيحة'
});

/**
 * Visit Types Enum (synced with Backend)
 */
export const VISIT_TYPES = {
  EMERGENCY: { value: 'EMERGENCY', labelAr: 'طوارئ', labelEn: 'Emergency' },
  OUTPATIENT: { value: 'OUTPATIENT', labelAr: 'عيادة خارجية', labelEn: 'Outpatient' },
  INPATIENT: { value: 'INPATIENT', labelAr: 'إقامة داخلية', labelEn: 'Inpatient' },
  ROUTINE: { value: 'ROUTINE', labelAr: 'روتينية', labelEn: 'Routine Check-up' },
  FOLLOW_UP: { value: 'FOLLOW_UP', labelAr: 'متابعة', labelEn: 'Follow-up' },
  PREVENTIVE: { value: 'PREVENTIVE', labelAr: 'وقائية', labelEn: 'Preventive' },
  SPECIALIZED: { value: 'SPECIALIZED', labelAr: 'تخصصية', labelEn: 'Specialized' },
  HOME_CARE: { value: 'HOME_CARE', labelAr: 'رعاية منزلية', labelEn: 'Home Care' },
  TELECONSULTATION: { value: 'TELECONSULTATION', labelAr: 'استشارة عن بُعد', labelEn: 'Teleconsultation' },
  DAY_SURGERY: { value: 'DAY_SURGERY', labelAr: 'جراحة يومية', labelEn: 'Day Surgery' }
};

/**
 * Visit Attachment Types Enum (synced with Backend)
 */
export const VISIT_ATTACHMENT_TYPES = {
  XRAY: { value: 'XRAY', labelAr: 'أشعة سينية', labelEn: 'X-Ray' },
  MRI: { value: 'MRI', labelAr: 'رنين مغناطيسي', labelEn: 'MRI Scan' },
  CT_SCAN: { value: 'CT_SCAN', labelAr: 'أشعة مقطعية', labelEn: 'CT Scan' },
  ULTRASOUND: { value: 'ULTRASOUND', labelAr: 'موجات فوق صوتية', labelEn: 'Ultrasound' },
  LAB_RESULT: { value: 'LAB_RESULT', labelAr: 'نتيجة مختبر', labelEn: 'Lab Result' },
  ECG: { value: 'ECG', labelAr: 'تخطيط قلب', labelEn: 'ECG/EKG' },
  PRESCRIPTION: { value: 'PRESCRIPTION', labelAr: 'وصفة طبية', labelEn: 'Prescription' },
  MEDICAL_REPORT: { value: 'MEDICAL_REPORT', labelAr: 'تقرير طبي', labelEn: 'Medical Report' },
  OTHER: { value: 'OTHER', labelAr: 'أخرى', labelEn: 'Other' }
};

export const visitsService = {
  /**
   * Get paginated visits list
   * CONTRACT: GET /api/visits
   * @param {Object} params - { page, size, search, sortBy, sortDir, employerId }
   * @returns {Promise<{items, total, page, size}>}
   */
  getPaginated: async (params = {}) => {
    try {
      const { page = 1, size = 10, search, sortBy = 'createdAt', sortDir = 'desc', employerId } = params;
      const queryParams = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sortBy,
        sortDir,
        ...(search && { search }),
        ...(employerId && { employerId: employerId.toString() })
      });
      const response = await axiosClient.get(`${BASE_URL}?${queryParams}`);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Get all visits (deprecated - use getPaginated)
   * @deprecated Use getPaginated instead
   * @returns {Promise<Array>} List of visits
   */
  getAll: async () => {
    try {
      const response = await axiosClient.get(`${BASE_URL}/all`);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Get visit by ID
   * CONTRACT: GET /api/visits/{id}
   * @param {number} id - Visit ID
   * @returns {Promise<VisitResponseDto>} Visit details
   */
  getById: async (id) => {
    try {
      if (!id) throw new Error('معرف الزيارة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Create new visit
   * CONTRACT: POST /api/visits
   * @param {VisitCreateDto} data - Visit data
   * @returns {Promise<VisitResponseDto>} Created visit
   */
  create: async (data) => {
    try {
      if (!data) throw new Error('بيانات الزيارة مطلوبة');
      // The caller displays the backend's precise validation/conflict message.
      // Suppress the global interceptor here to avoid two error snackbars for
      // the same failed visit creation.
      const response = await axiosClient.post(BASE_URL, data, { suppressGlobalError: true });
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Update visit
   * CONTRACT: PUT /api/visits/{id}
   * @param {number} id - Visit ID
   * @param {VisitCreateDto} data - Updated visit data
   * @returns {Promise<VisitResponseDto>} Updated visit
   */
  update: async (id, data) => {
    try {
      if (!id) throw new Error('معرف الزيارة مطلوب');
      if (!data) throw new Error('بيانات التحديث مطلوبة');
      const response = await axiosClient.put(`${BASE_URL}/${id}`, data);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Delete visit
   * CONTRACT: DELETE /api/visits/{id}
   * @param {number} id - Visit ID
   * @returns {Promise<void>}
   */
  remove: async (id) => {
    try {
      if (!id) throw new Error('معرف الزيارة مطلوب');
      const response = await axiosClient.delete(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Get visits count
   * CONTRACT: GET /api/visits/count
   * @param {number} employerId - Optional employer filter
   * @returns {Promise<number>} Total count
   */
  getCount: async (employerId) => {
    try {
      const url = employerId ? `${BASE_URL}/count?employerId=${employerId}` : `${BASE_URL}/count`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  // ============ VISIT ATTACHMENTS API ============

  /**
   * Upload attachment to visit
   * CONTRACT: POST /api/visits/{visitId}/attachments
   * @param {number} visitId - Visit ID
   * @param {File} file - File to upload
   * @param {string} attachmentType - Attachment type enum
   * @param {string} description - Optional description
   * @returns {Promise<Object>} Uploaded attachment details
   */
  uploadAttachment: async (visitId, file, attachmentType, description) => {
    try {
      if (!visitId) throw new Error('معرف الزيارة مطلوب');
      if (!file) throw new Error('الملف مطلوب');
      if (!attachmentType) throw new Error('نوع المرفق مطلوب');

      const formData = new FormData();
      formData.append('file', file);
      formData.append('attachmentType', attachmentType);
      if (description) formData.append('description', description);

      const response = await axiosClient.post(`${BASE_URL}/${visitId}/attachments`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      return response.data;
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Get visit attachments
   * CONTRACT: GET /api/visits/{visitId}/attachments
   * @param {number} visitId - Visit ID
   * @returns {Promise<Array>} List of attachments
   */
  getAttachments: async (visitId) => {
    try {
      if (!visitId) throw new Error('معرف الزيارة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${visitId}/attachments`);
      return response.data;
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Download attachment
   * CONTRACT: GET /api/visits/{visitId}/attachments/{attachmentId}
   * @param {number} visitId - Visit ID
   * @param {number} attachmentId - Attachment ID
   * @returns {Promise<Blob>} File content
   */
  downloadAttachment: async (visitId, attachmentId) => {
    try {
      if (!visitId || !attachmentId) throw new Error('معرف الزيارة والمرفق مطلوبان');
      const response = await axiosClient.get(`${BASE_URL}/${visitId}/attachments/${attachmentId}`, {
        responseType: 'blob'
      });
      return response.data;
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  /**
   * Delete attachment
   * CONTRACT: DELETE /api/visits/{visitId}/attachments/{attachmentId}
   * @param {number} visitId - Visit ID
   * @param {number} attachmentId - Attachment ID
   * @returns {Promise<void>}
   */
  deleteAttachment: async (visitId, attachmentId) => {
    try {
      if (!visitId || !attachmentId) throw new Error('معرف الزيارة والمرفق مطلوبان');
      const response = await axiosClient.delete(`${BASE_URL}/${visitId}/attachments/${attachmentId}`);
      return response.data;
    } catch (error) {
      throw handleVisitErrors(error);
    }
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // PROVIDER PORTAL VISIT CONTEXT
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Get Visit Context (Decision Payload)
   *
   * This is the CANONICAL way to determine what to show for a visit.
   * Backend decides - Frontend just follows the decision.
   *
   * CONTRACT: GET /api/provider/visits/{visitId}/context
   *
   * @param {number} visitId - Visit ID
   * @returns {Promise<VisitContextDto>} Decision payload with:
   *   - hasClaim: boolean
   *   - claimId: Long (if hasClaim)
   *   - claimStatus: String (if hasClaim)
   *   - hasPreAuthorization: boolean
   *   - preAuthorizationId: Long (if hasPreAuthorization)
   *   - preAuthorizationStatus: String (if hasPreAuthorization)
   *   - eligibilityOnly: boolean (if neither claim nor preAuth exists)
   */
  getContext: async (visitId) => {
    try {
      if (!visitId) throw new Error('معرف الزيارة مطلوب');
      const response = await axiosClient.get(`/provider/visits/${visitId}/context`);
      return unwrap(response);
    } catch (error) {
      console.error('[VISITS] Error getting visit context:', error);
      throw handleVisitErrors(error);
    }
  }
};

export default visitsService;
