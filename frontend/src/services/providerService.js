import api from '../utils/axios';

/**
 * Provider Portal API Service
 *
 * Handles all provider-related API calls:
 * - Eligibility checks
 * - Claims submission (future)
 * - Pre-authorization requests (future)
 *
 * @since Phase 1 - Provider Portal
 */

// Note: axios baseURL already includes /api, so we use /provider not /api/provider
const PROVIDER_BASE_URL = '/provider';

/**
 * Utility: Clean payload by removing null, undefined, and empty string values
 * This prevents validation errors from empty fields being sent to backend
 *
 * @param {Object} obj - Object to clean
 * @returns {Object} Cleaned object with only valid values
 */
const cleanPayload = (obj) => {
  return Object.fromEntries(
    Object.entries(obj).filter(([_, v]) => v !== null && v !== undefined && v !== '' && !(typeof v === 'string' && v.trim() === ''))
  );
};

/**
 * Parse validation error response into user-friendly message
 *
 * @param {Object} errorData - Error response from API
 * @returns {Object} Formatted error with message and field details
 */
const parseValidationError = (errorData) => {
  const result = {
    message: '',
    fields: {},
    isValidationError: false
  };

  if (!errorData) {
    result.message = 'تعذر الاتصال بالخادم أو حدث خطأ داخلي غير متوقع';
    return result;
  }

  // Check if it's a validation error
  if (errorData.errorCode === 'VALIDATION_ERROR' || errorData.code === 'VALIDATION_ERROR') {
    result.isValidationError = true;

    // Extract field-specific errors
    if (errorData.details && typeof errorData.details === 'object') {
      result.fields = errorData.details;

      // Build user-friendly message from field errors
      const fieldMessages = Object.entries(errorData.details).map(([field, msg]) => {
        const arabicFieldNames = {
          barcode: 'الباركود',
          cardNumber: 'رقم البطاقة',
          serviceDate: 'تاريخ الخدمة'
        };
        const arabicField = arabicFieldNames[field] || field;
        return `${arabicField}: ${msg}`;
      });

      result.message = fieldMessages.length > 0 ? fieldMessages.join('، ') : 'يرجى التحقق من البيانات المدخلة';
    } else {
      result.message = errorData.message || 'فشل التحقق من صحة البيانات';
    }
  } else {
    result.message = errorData.message || errorData.messageAr || errorData.error || 'عذراً، حدث خطأ أثناء معالجة طلبك';
  }

  return result;
};

export const providerApi = {
  /**
   * Check member eligibility (POST)
   *
   * الفحص يتم فقط بـ:
   * - الباركود (WAD-2026-XXXXXXXX)
   * - رقم البطاقة (Card Number)
   *
   * ملاحظة: الرقم الوطني لا يُستخدم للفحص - يظهر فقط كمعلومات أساسية
   *
   * @param {Object} request - Eligibility check request
   * @param {string} request.barcode - Member barcode or card number
   * @param {string} request.serviceDate - Service date (ISO format) - optional
   * @returns {Promise<Object>} Eligibility response
   * @throws {Object} Enhanced error with parsed validation details
   */
  checkEligibility: async (request) => {
    try {
      // Clean payload - remove null, undefined, and empty values
      const cleanedRequest = cleanPayload(request);

      // Debug logging (only in development)
      if (import.meta.env.DEV) {
        console.log('[Provider API] Eligibility check request:', cleanedRequest);
      }

      // Validate that barcode is provided (barcode or card number)
      if (!cleanedRequest.barcode) {
        throw {
          response: {
            status: 400,
            data: {
              errorCode: 'VALIDATION_ERROR',
              message: 'يجب إدخال الباركود أو رقم البطاقة',
              details: {
                barcode: 'الباركود أو رقم البطاقة مطلوب'
              }
            }
          }
        };
      }

      const response = await api.post(`${PROVIDER_BASE_URL}/eligibility-check`, cleanedRequest);
      return response.data;
    } catch (error) {
      // Parse and enhance error response
      const errorData = error.response?.data;
      const parsedError = parseValidationError(errorData);

      // Create enhanced error object
      const enhancedError = new Error(parsedError.message);
      enhancedError.response = error.response;
      enhancedError.parsedError = parsedError;
      enhancedError.isValidationError = parsedError.isValidationError;
      enhancedError.fields = parsedError.fields;

      throw enhancedError;
    }
  },

  /**
   * Quick eligibility check by barcode or card number
   *
   * @param {string} barcode - Member barcode or card number
   * @param {number} [memberId] - Optional explicit member ID for exact matching
   * @returns {Promise<Object>} Eligibility response
   */
  checkEligibilityByBarcode: async (barcode, memberId = null) => {
    const payload = { barcode: barcode };
    if (memberId) {
      payload.memberId = memberId;
    }
    const response = await api.post(`/provider/eligibility-check`, payload);
    return response.data?.data;
  },

  /**
   * Search members for autocomplete
   *
   * @param {string} query - Search query (name, card, civil ID)
   * @returns {Promise<Array>} List of matching members
   */
  searchMembers: async (query) => {
    try {
      const response = await api.get(`/unified-members/unified-search`, { params: { query } });
      return response.data?.data || [];
    } catch (error) {
      console.error('Member search failed:', error);
      return [];
    }
  },

  /**
   * Submit claim (Prompt 2)
   *
   * @param {Object} claimData - Claim submission data
   * @param {number} claimData.memberId - Member ID
   * @param {string} claimData.claimType - CASH or DIRECT_BILLING
   * @param {string} claimData.serviceType - OUTPATIENT, INPATIENT, or EMERGENCY
   * @param {string} claimData.serviceDate - Service date (YYYY-MM-DD)
   * @param {string} claimData.serviceName - Service/procedure name
   * @param {number} claimData.claimedAmount - Claimed amount
   * @param {string} [claimData.diagnosis] - Diagnosis code/description
   * @param {string} [claimData.notes] - Additional notes
   * @param {number} [claimData.attachmentCount] - Number of attachments
   * @returns {Promise<Object>} Claim response with validation results
   */
  submitClaim: async (claimData) => {
    const response = await api.post(`${PROVIDER_BASE_URL}/claims/submit`, claimData);
    return response.data;
  },

  /**
   * Request pre-authorization (Placeholder for Prompt 3)
   *
   * @param {Object} preAuthData - Pre-authorization request data
   * @returns {Promise<Object>} Pre-auth response
   */
  requestPreAuthorization: async (preAuthData) => {
    // TODO: Implement in Prompt 3
    throw new Error('Not implemented yet - will be added in Prompt 3');
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // VISIT MANAGEMENT (NEW FLOW 2026-01-13)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Register a new visit for a member.
   *
   * NEW FLOW:
   * 1. Provider performs eligibility check
   * 2. Selects eligible member
   * 3. Clicks "Register Visit" → this API is called
   * 4. Visit is created and linked to member
   * 5. Provider can then create Claim or Pre-Auth from Visit Log
   *
   * @param {Object} visitData - Visit registration data
   * @param {number} visitData.memberId - Member ID (required)
   * @param {number} [visitData.eligibilityCheckId] - Eligibility check ID
   * @param {string} [visitData.visitDate] - Visit date (YYYY-MM-DD)
   * @param {string} [visitData.visitType] - OUTPATIENT, INPATIENT, EMERGENCY, etc.
   * @param {string} [visitData.doctorName] - Doctor name
   * @param {string} [visitData.specialty] - Medical specialty
   * @param {string} [visitData.diagnosis] - Initial diagnosis
   * @param {string} [visitData.notes] - Notes
   * @returns {Promise<Object>} ProviderVisitResponse
   */
  registerVisit: async (visitData) => {
    try {
      const cleanedData = cleanPayload(visitData);

      if (!cleanedData.memberId) {
        throw {
          response: {
            status: 400,
            data: {
              errorCode: 'VALIDATION_ERROR',
              message: 'معرف العضو مطلوب',
              details: { memberId: 'معرف العضو مطلوب' }
            }
          }
        };
      }

      const response = await api.post(`${PROVIDER_BASE_URL}/visits/register`, cleanedData);
      return response.data;
    } catch (error) {
      const errorData = error.response?.data;
      const parsedError = parseValidationError(errorData);

      const enhancedError = new Error(parsedError.message);
      enhancedError.response = error.response;
      enhancedError.parsedError = parsedError;
      throw enhancedError;
    }
  },

  /**
   * Get visit log (paginated with filters).
   *
   * @param {Object} params - Query parameters
   * @param {number} [params.memberId] - Filter by member ID
   * @param {string} [params.status] - Filter by status (REGISTERED, CLAIM_SUBMITTED, etc.)
   * @param {string} [params.fromDate] - From date filter (YYYY-MM-DD)
   * @param {string} [params.toDate] - To date filter (YYYY-MM-DD)
   * @param {number} [params.page=0] - Page number (0-based)
   * @param {number} [params.size=10] - Page size
   * @param {string} [params.sortBy='visitDate'] - Sort field
   * @param {string} [params.sortDir='desc'] - Sort direction
   * @returns {Promise<Object>} Paginated ProviderVisitResponse
   */
  getVisitLog: async (params = {}) => {
    const response = await api.get(`${PROVIDER_BASE_URL}/visits`, { params });
    return response.data;
  },

  /**
   * Get visit details by ID.
   *
   * @param {number} visitId - Visit ID
   * @returns {Promise<Object>} ProviderVisitResponse
   */
  getVisitById: async (visitId) => {
    if (!visitId) throw new Error('معرف الزيارة مطلوب');
    const response = await api.get(`${PROVIDER_BASE_URL}/visits/${visitId}`);
    return response.data;
  },

  /**
   * Cancel a registered visit (Provider Portal).
   * 
   * @param {number} visitId - Visit ID
   * @returns {Promise<Object>} ProviderVisitResponse
   */
  cancelVisit: async (visitId) => {
    if (!visitId) throw new Error('معرف الزيارة مطلوب');
    const response = await api.delete(`${PROVIDER_BASE_URL}/visits/${visitId}`);
    return response.data;
  }
};

export default providerApi;
