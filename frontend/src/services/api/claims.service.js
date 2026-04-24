import axiosClient from 'utils/axios';
import { createErrorHandler } from 'utils/api-error-handler';
import { validateClaimNumber, validateAmount } from 'utils/api-validators';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

// ==============================|| CLAIMS SERVICE ||============================== //

const BASE_URL = '/claims';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Error handler for claims service
 * Provides user-friendly Arabic error messages
 */
const handleClaimErrors = createErrorHandler('المطالبة', {
  404: 'المطالبة غير موجودة',
  409: 'تعارض في حالة المطالبة أو البيانات الحالية',
  422: 'البيانات المُدخلة للمطالبة غير صحيحة'
});

export const claimsService = {
  /**
   * Get all claims with pagination and filtering
   * @param {Object} params - Query parameters {page, size, employerId, sort, search}
   * @returns {Promise<Object>} Paginated claims list
   */
  getAll: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page !== undefined) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.employerId) queryParams.append('employerId', params.employerId);
      if (params.sort) {
        const [sortBy, sortDir] = params.sort.split(',');
        queryParams.append('sortBy', sortBy);
        queryParams.append('sortDir', sortDir || 'desc');
      }
      if (params.search) queryParams.append('search', params.search);

      const url = queryParams.toString() ? `${BASE_URL}?${queryParams.toString()}` : BASE_URL;
      const response = await axiosClient.get(url);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * List claims with full filtering support
   * @param {Object} params - Filter params {employerId, providerId, status, dateFrom, dateTo, page, size, sortBy, sortDir}
   */
  list: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      Object.keys(params).forEach((key) => {
        if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
          queryParams.append(key, params[key]);
        }
      });
      const response = await axiosClient.get(`${BASE_URL}?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get claim by ID
   * @param {number} id - Claim ID
   * @returns {Promise<Object>} Claim details
   */
  getById: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Coverage Engine - bulk calculate claim lines (backend-authoritative).
   * @param {Object} payload - BulkCoverageEngineRequest
   * @returns {Promise<Array>} Calculated line results
   */
  calculateCoverageBulk: async (payload) => {
    try {
      if (!payload) throw new Error('بيانات الحساب مطلوبة');
      const response = await axiosClient.post(`${BASE_URL}/calculate-bulk`, payload);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Coverage Engine - single line calculate.
   * @param {Object} payload - BulkCoverageEngineRequest with one line
   * @returns {Promise<Object>} Calculated line result
   */
  calculateCoverageSingle: async (payload) => {
    try {
      if (!payload) throw new Error('بيانات الحساب مطلوبة');
      const response = await axiosClient.post(`${BASE_URL}/calculate`, payload);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },



  /**
   * Get claim by claim number
   * @param {string} claimNumber - Claim number (format: CLM-YYYYMMDD-XXXX)
   * @returns {Promise<Object>} Claim details
   */
  getByClaimNumber: async (claimNumber) => {
    try {
      validateClaimNumber(claimNumber);
      const response = await axiosClient.get(`${BASE_URL}/number/${claimNumber}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Create new claim
   * @param {Object} data - Claim data
   * @returns {Promise<Object>} Created claim
   */
  create: async (data) => {
    try {
      if (!data) throw new Error('بيانات المطالبة مطلوبة');
      if (data.requestedAmount !== undefined) {
        validateAmount(data.requestedAmount, 'المبلغ المطلوب');
      }
      const response = await axiosClient.post(BASE_URL, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Update claim data (DRAFT/NEEDS_CORRECTION)
   * @param {number} id - Claim ID
   * @param {Object} data - Updated claim data
   * @returns {Promise<Object>} Updated claim
   */
  updateData: async (id, data) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      if (!data) throw new Error('بيانات التحديث مطلوبة');
      if (data.requestedAmount !== undefined) {
        validateAmount(data.requestedAmount, 'المبلغ المطلوب');
      }
      const response = await axiosClient.put(`${BASE_URL}/${id}/data`, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Reviewer update for claim review actions
   * @param {number} id - Claim ID
   * @param {Object} data - Review payload
   * @returns {Promise<Object>} Updated claim
   */
  updateReview: async (id, data) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      if (!data) throw new Error('بيانات المراجعة مطلوبة');
      const response = await axiosClient.put(`${BASE_URL}/${id}/review`, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // Backward-compatible alias (internally aligned to /data)
  update: async (id, data) => claimsService.updateData(id, data),

  /**
   * Delete claim
   * @param {number} id - Claim ID
   * @returns {Promise<void>}
   */
  remove: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.delete(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get claims by visit
   * @param {number} visitId - Visit ID
   * @returns {Promise<Array>} List of claims
   */
  getByVisit: async (visitId) => {
    try {
      if (!visitId) throw new Error('معرف الزيارة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/visit/${visitId}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get claims by status
   * @param {string} status - Claim status (PENDING, APPROVED, REJECTED)
   * @returns {Promise<Array>} List of claims
   */
  getByStatus: async (status) => {
    try {
      if (!status) throw new Error('حالة المطالبة مطلوبة');
      const response = await axiosClient.get(`${BASE_URL}/status/${status}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Approve claim (Async - Split Phase)
   * @param {number} id - Claim ID
   * @param {Object} data - Approval data (approvedAmount, notes)
   * @returns {Promise<Object>} Claim with APPROVAL_IN_PROGRESS status
   *
   * Returns immediately with status APPROVAL_IN_PROGRESS.
   * Client should poll getById() to check for final status (APPROVED/REJECTED).
   */
  approve: async (id, data) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      // Only validate approvedAmount if it's a real value (not null/undefined)
      // When useSystemCalculation=true, approvedAmount is null (backend calculates it)
      if (data?.approvedAmount !== undefined && data?.approvedAmount !== null) {
        validateAmount(data.approvedAmount, 'المبلغ المعتمد');
      }
      // Async approval - returns immediately (no long timeout needed)
      const response = await axiosClient.post(`${BASE_URL}/${id}/approve`, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Reject claim
   * @param {number} id - Claim ID
   * @param {Object} data - Rejection data (rejectionReason)
   * @returns {Promise<Object>} Rejected claim
   */
  reject: async (id, data) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      if (!data?.rejectionReason) {
        throw new Error('سبب الرفض مطلوب');
      }
      const response = await axiosClient.post(`${BASE_URL}/${id}/reject`, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Search claims
   * @param {string} searchTerm - Search term
   * @returns {Promise<Array>} Filtered claims
   */
  search: async (searchTerm) => {
    try {
      const response = await axiosClient.get(`${BASE_URL}/search?q=${encodeURIComponent(searchTerm || '')}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // ======================= INBOX OPERATIONS =======================

  /**
   * Get pending claims for inbox (operations review)
   * @param {Object} params - Pagination params {page, size, sortBy, sortDir, employerId, fromDate, toDate}
   * @returns {Promise<Object>} Paginated pending claims {items, total, page, size}
   */
  getPendingClaims: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDir', params.sortDir);
      if (params.employerId) queryParams.append('employerId', params.employerId);
      if (params.providerId) queryParams.append('providerId', params.providerId);
      if (params.fromDate) queryParams.append('fromDate', params.fromDate);
      if (params.toDate) queryParams.append('toDate', params.toDate);
      if (params.status) queryParams.append('status', params.status);
      if (params.search) queryParams.append('search', params.search);

      const response = await axiosClient.get(`${BASE_URL}/inbox/pending?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get approved claims ready for settlement
   * @param {Object} params - Pagination params {page, size, sortBy, sortDir, employerId}
   * @returns {Promise<Object>} Paginated approved claims {items, total, page, size}
   */
  getApprovedClaims: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDir', params.sortDir);
      if (params.employerId) queryParams.append('employerId', params.employerId);
      if (params.providerId) queryParams.append('providerId', params.providerId);

      const response = await axiosClient.get(`${BASE_URL}/inbox/approved?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // ======================= SETTLED CLAIMS =======================

  /**
   * Get settled claims (for Invoices/Payments/Completed tabs)
   * ⚠️ CANONICAL: Uses backend filter instead of client-side filtering
   *
   * @param {Object} params - Pagination params {page, size, sortBy, sortDir, employerId}
   * @returns {Promise<Object>} Paginated settled claims {items, total, page, size}
   */
  getSettledClaims: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.page) queryParams.append('page', params.page);
      if (params.size) queryParams.append('size', params.size);
      queryParams.append('sortBy', params.sortBy || 'settledAt');
      queryParams.append('sortDir', params.sortDir || 'desc');
      // Filter by SETTLED status
      queryParams.append('status', 'SETTLED');
      if (params.employerId) queryParams.append('employerId', params.employerId);
      if (params.providerId) queryParams.append('providerId', params.providerId);

      const response = await axiosClient.get(`${BASE_URL}?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get cost breakdown (Financial Snapshot) for a claim;
      if (params.sortBy) queryParams.append('sortBy', params.sortBy);
      if (params.sortDir) queryParams.append('sortDir', params.sortDir);

      const response = await axiosClient.get(`${BASE_URL}/inbox/approved?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get cost breakdown (Financial Snapshot) for a claim
   * @param {number} id - Claim ID
   * @returns {Promise<Object>} Cost breakdown {requestedAmount, patientCoPay, netProviderAmount, ...}
   */
  getCostBreakdown: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      // Increase timeout for cost breakdown (complex calculations)
      const response = await axiosClient.get(`${BASE_URL}/${id}/cost-breakdown`, {
        timeout: 60000 // 1 minute timeout
      });
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get claim attachments
   * @param {number} id - Claim ID
   * @returns {Promise<Array>} List of attachments
   */
  getAttachments: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${id}/attachments`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Upload attachment to claim
   * @param {number} id - Claim ID
   * @param {FormData} formData - Form data with file and attachmentType
   * @returns {Promise<Object>} Uploaded attachment metadata
   */
  uploadAttachment: async (id, formData) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      if (!formData) throw new Error('بيانات الملف مطلوبة');
      const response = await axiosClient.post(`${BASE_URL}/${id}/attachments`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Download claim attachment
   * @param {number} claimId - Claim ID
   * @param {number} attachmentId - Attachment ID
   * @returns {Promise<Blob>} File blob
   */
  downloadAttachment: async (claimId, attachmentId) => {
    try {
      if (!claimId) throw new Error('معرف المطالبة مطلوب');
      if (!attachmentId) throw new Error('معرف المرفق مطلوب');
      const response = await axiosClient.get(`${BASE_URL}/${claimId}/attachments/${attachmentId}`, {
        responseType: 'blob'
      });
      return response.data;
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Submit claim for review (change status from DRAFT to SUBMITTED)
   * @param {number} id - Claim ID
   * @returns {Promise<Object>} Updated claim
   */
  submit: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/submit`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Start review of a submitted claim (SUBMITTED → UNDER_REVIEW)
   * @param {number} id - Claim ID
   * @returns {Promise<Object>} Updated claim
   */
  startReview: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/start-review`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * ════════════════════════════════════════════════════════════════════════════
   * ⛔ LEGACY SETTLEMENT - DISABLED (2026-02-01)
   * ════════════════════════════════════════════════════════════════════════════
   * Per-claim settlement is NO LONGER SUPPORTED.
   * All settlements must go through Settlement Batches:
   * - POST /api/settlement/batches (create batch)
   * - POST /api/settlement/batches/{id}/add-claims (add claims)
   * - POST /api/settlement/batches/{id}/confirm (confirm)
   * - POST /api/settlement/batches/{id}/pay (finalize payment)
   *
   * This function throws an error to prevent legacy usage.
   * @deprecated Use Settlement Batches API instead
   * ════════════════════════════════════════════════════════════════════════════
   */
  settle: async (id, data) => {
    throw new Error(
      'تم إيقاف التسوية المباشرة للمطالبات. يرجى استخدام دفعات التسوية من: /settlement/batches - ' +
      'Legacy per-claim settlement is DISABLED. Use Settlement Batches API.'
    );
  },

  /**
  * Return claim for correction (UNDER_REVIEW → NEEDS_CORRECTION)
   * @param {number} id - Claim ID
   * @param {Object} data - Return data {reason, requiredDocuments}
   * @returns {Promise<Object>} Updated claim
   */
  returnForInfo: async (id, data) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      if (!data?.reason) throw new Error('سبب طلب المعلومات مطلوب');
      const response = await axiosClient.post(`${BASE_URL}/${id}/return-for-info`, data);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // ======================= REPORTS =======================

  /**
   * Get adjudication report
   * @param {Object} params - Report params {startDate, endDate, providerId, status}
   * @returns {Promise<Object>} Adjudication report
   */
  getAdjudicationReport: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.startDate) queryParams.append('startDate', params.startDate);
      if (params.endDate) queryParams.append('endDate', params.endDate);
      if (params.providerId) queryParams.append('providerId', params.providerId);
      if (params.status) queryParams.append('status', params.status);

      const response = await axiosClient.get(`/reports/adjudication?${queryParams.toString()}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get provider settlement report
   * @param {number} providerId - Provider ID
   * @param {Object} params - Report params {startDate, endDate}
   * @returns {Promise<Object>} Provider settlement report
   */
  getProviderSettlementReport: async (providerId, params = {}) => {
    try {
      if (!providerId) throw new Error('معرف المزود مطلوب');
      const queryParams = new URLSearchParams();
      if (params.startDate) queryParams.append('startDate', params.startDate);
      if (params.endDate) queryParams.append('endDate', params.endDate);

      const response = await axiosClient.get(`/reports/provider-settlement/${providerId}?${queryParams.toString()}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get member statement
   * @param {number} memberId - Member ID
   * @returns {Promise<Object>} Member claims statement
   */
  getMemberStatement: async (memberId) => {
    try {
      if (!memberId) throw new Error('معرف العضو مطلوب');
      const response = await axiosClient.get(`/reports/member-statement/${memberId}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // ═══════════════════════════════════════════════════════════════════════════════
  // FINANCIAL SUMMARY ENDPOINTS - SINGLE SOURCE OF TRUTH
  // ═══════════════════════════════════════════════════════════════════════════════
  // THESE ENDPOINTS MUST BE USED FOR ALL FINANCIAL TOTALS.
  // FRONTEND IS FORBIDDEN FROM USING .reduce() ON FINANCIAL FIELDS.
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Get comprehensive financial summary from backend.
   * ⚠️ SINGLE SOURCE OF TRUTH - Never calculate totals in frontend!
   *
   * @param {Object} params - Optional filters {employerOrgId, providerId, fromDate, toDate}
   * @returns {Promise<Object>} Financial summary with all totals
   */
  getFinancialSummary: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      // Match backend ClaimController @RequestParam names: employerId, providerId, status, dateFrom, dateTo
      if (params.employerId) queryParams.append('employerId', params.employerId);
      else if (params.employerOrgId) queryParams.append('employerId', params.employerOrgId); // Fallback

      if (params.providerId) queryParams.append('providerId', params.providerId);

      if (params.status) queryParams.append('status', params.status);

      if (params.dateFrom) queryParams.append('dateFrom', params.dateFrom);
      else if (params.fromDate) queryParams.append('dateFrom', params.fromDate); // Fallback

      if (params.dateTo) queryParams.append('dateTo', params.dateTo);
      else if (params.toDate) queryParams.append('dateTo', params.toDate); // Fallback

      const queryString = queryParams.toString();
      const url = `${BASE_URL}/financial-summary${queryString ? `?${queryString}` : ''}`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /**
   * Get settlement-focused summary for Settlement Inbox.
   * ⚠️ SINGLE SOURCE OF TRUTH - Never calculate settlement totals in frontend!
   *
   * @param {Object} params - Optional filters {employerOrgId}
   * @returns {Promise<Object>} Settlement summary with authoritative totals
   */
  getSettlementSummary: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.employerOrgId) queryParams.append('employerOrgId', params.employerOrgId);

      const queryString = queryParams.toString();
      const url = `${BASE_URL}/settlement-summary${queryString ? `?${queryString}` : ''}`;
      const response = await axiosClient.get(url);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  // ═══════════════════════════════════════════════════════════════════════════════
  // SOFT DELETE / RESTORE / HARD DELETE
  // ═══════════════════════════════════════════════════════════════════════════════

  /** حذف ناعم — تعود الأموال للسقف تلقائياً */
  softDelete: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.delete(`${BASE_URL}/${id}`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /** استعادة مطالبة محذوفة */
  restore: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.put(`${BASE_URL}/${id}/restore`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /** حذف نهائي — SUPER_ADMIN فقط */
  hardDelete: async (id) => {
    try {
      if (!id) throw new Error('معرف المطالبة مطلوب');
      const response = await axiosClient.delete(`${BASE_URL}/${id}/hard`);
      return unwrap(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  },

  /** قائمة المطالبات المحذوفة */
  listDeleted: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      Object.keys(params).forEach((key) => {
        if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
          queryParams.append(key, params[key]);
        }
      });
      const response = await axiosClient.get(`${BASE_URL}/deleted?${queryParams.toString()}`);
      return normalizePaginatedResponse(response);
    } catch (error) {
      throw handleClaimErrors(error);
    }
  }
};

export default claimsService;
