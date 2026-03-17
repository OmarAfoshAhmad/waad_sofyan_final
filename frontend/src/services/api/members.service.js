import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

/**
 * Members API Service
 * Provides CRUD operations for Members module
 * Backend: MemberController.java
 *
 * Phase 2 Features:
 * - Status Management: suspend, activate, terminate
 * - Card Management: block, activate
 * - Eligibility Check: real-time eligibility verification
 * - Field Normalization: name ↔ fullName, name ↔ fullName
 */
const BASE_URL = '/members';

/**
 * Helper function to unwrap ApiResponse
 * Backend returns: { status: "success", data: {...}, message: "...", timestamp: "..." }
 */
const unwrap = (response) => response.data?.data || response.data;

// ============================================================================
// PHASE 2: FIELD NORMALIZERS
// ============================================================================

/**
 * Normalize member request (Frontend → Backend)
 * Converts: name → fullName, name → fullName
 *
 * ✅ ENHANCED: Remove backend-generated fields and handle optional fields
 *
 * @param {Object} payload - Frontend payload with name/name
 * @returns {Object} Backend payload with fullName/fullName
 */
export const normalizeMemberRequest = (payload) => {
  if (!payload) return payload;

  const normalized = { ...payload };

  // Map frontend field names to backend field names
  if (payload.name !== undefined) {
    normalized.fullName = payload.name;
    delete normalized.name;
  }
  if (payload.name !== undefined) {
    normalized.fullName = payload.name;
    delete normalized.name;
  }
  if (payload.full_name !== undefined) {
    normalized.fullName = payload.full_name;
    delete normalized.full_name;
  }
  if (payload.full_name !== undefined) {
    normalized.fullName = payload.full_name;
    delete normalized.full_name;
  }

  // ✅ FIX: Remove backend-generated fields
  delete normalized.barcode; // Backend generates this automatically
  delete normalized.id; // Should not be sent on create
  delete normalized.createdAt;
  delete normalized.updatedAt;
  delete normalized.createdBy;
  delete normalized.updatedBy;

  // ✅ FIX: Handle optional fields - convert empty strings to null
  const optionalFields = [
    'nationalNumber',
    'birthDate',
    'gender',
    'maritalStatus',
    'nationality',
    'phone',
    'email',
    'address',
    'civilId',
    'policyNumber',
    'benefitPolicyId',
    'startDate',
    'endDate',
    'notes'
  ];

  optionalFields.forEach((field) => {
    if (normalized[field] === '' || normalized[field] === undefined) {
      normalized[field] = null;
    }
  });

  // ✅ FIX: Handle family members - remove barcode and handle optional fields
  if (normalized.familyMembers && Array.isArray(normalized.familyMembers)) {
    normalized.familyMembers = normalized.familyMembers.map((fm) => {
      const cleanedFm = { ...fm };
      delete cleanedFm.barcode; // Backend generates this
      delete cleanedFm.id; // Should not be sent

      // Convert empty strings to null for optional fields
      ['nationalNumber', 'birthDate', 'gender'].forEach((field) => {
        if (cleanedFm[field] === '' || cleanedFm[field] === undefined) {
          cleanedFm[field] = null;
        }
      });

      return cleanedFm;
    });
  }

  return normalized;
};

/**
 * Normalize member response (Backend → Frontend)
 * Converts: fullName → name, fullName → name
 *
 * @param {Object} data - Backend response with fullName/fullName
 * @returns {Object} Frontend data with name/name
 */
export const normalizeMemberResponse = (data) => {
  if (!data) return data;

  const normalized = { ...data };

  // Map backend field names to frontend field names
  if (data.fullName !== undefined) {
    normalized.name = data.fullName;
    // Keep both for compatibility
  }
  if (data.fullName !== undefined) {
    normalized.name = data.fullName;
    // Keep both for compatibility
  }

  // Handle nested family members
  if (normalized.familyMembers && Array.isArray(normalized.familyMembers)) {
    normalized.familyMembers = normalized.familyMembers.map(normalizeMemberResponse);
  }

  return normalized;
};

/**
 * Delete multiple members (Bulk Delete)
 * Endpoint: DELETE /api/members/bulk
 * @param {Array<number>} ids - List of member IDs to delete
 * @returns {Promise<void>}
 */
export const bulkDeleteMembers = async (ids) => {
  if (!ids || ids.length === 0) return;
  return await axiosClient.delete(`${BASE_URL}/bulk`, { data: ids });
};

/**
 * Delete all members for a specific employer/partner
 * WARNING: This deletes ALL members under the employer!
 * Endpoint: DELETE /api/members/employer/{employerId}
 * @param {number} employerId - Employer ID
 * @returns {Promise<Object>} Response with deletion count
 */
export const deleteAllMembersByEmployer = async (employerId) => {
  const response = await axiosClient.delete(`${BASE_URL}/employer/${employerId}`);
  return unwrap(response);
};



/**
 * Get paginated members list
 * Endpoint: GET /api/members
 * @param {Object} params - Query parameters
 * @param {number} params.page - Page number (1-based, default: 1)
 * @param {number} params.size - Page size (default: 20)
 * @param {string} params.sortBy - Sort field (default: 'createdAt')
 * @param {string} params.sortDir - Sort direction: 'asc' or 'desc' (default: 'desc')
 * @param {string} params.search - Search query (optional)
 * @returns {Promise<Object>} Paginated response with items, total, page, size
 */
export const getMembers = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return normalizePaginatedResponse(response);
};

/**
 * Get member by ID
 * Endpoint: GET /api/members/{id}
 * @param {number} id - Member ID
 * @returns {Promise<Object>} MemberViewDto with family members
 */
export const getMemberById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Create new member
 * Endpoint: POST /api/members
 *
 * ✅ ENHANCED: Better logging and error handling
 *
 * @param {Object} payload - MemberCreateDto
 * @param {string} payload.name - Full name in Arabic (required) - will be normalized to fullName
 * @param {string} payload.name - Full name in English (optional) - will be normalized to fullName
 * @param {string} payload.civilId - Civil ID (OPTIONAL - can be null)
 * @param {string} payload.birthDate - Birth date yyyy-MM-dd (OPTIONAL)
 * @param {string} payload.gender - Gender: MALE, FEMALE, UNDEFINED (OPTIONAL)
 * @param {number} payload.employerId - Employer ID (required)
 * @param {Array} payload.familyMembers - Family members array (optional)
 * @returns {Promise<Object>} Created MemberViewDto (normalized with name/name)
 */
export const createMember = async (payload) => {
  console.log('🆕 [Create Member] Original payload:', JSON.stringify(payload, null, 2));

  const normalizedPayload = normalizeMemberRequest(payload);

  console.log('🆕 [Create Member] Normalized payload (after cleanup):', JSON.stringify(normalizedPayload, null, 2));

  try {
    const response = await axiosClient.post(BASE_URL, normalizedPayload);
    const result = normalizeMemberResponse(unwrap(response));

    console.log('✅ [Create Member] Member created successfully:', result);
    return result;
  } catch (error) {
    console.error('❌ [Create Member] Failed to create member:', {
      message: error.message,
      status: error.response?.status,
      statusText: error.response?.statusText,
      validationErrors: error.response?.data?.errors,
      backendMessage: error.response?.data?.message,
      payload: normalizedPayload
    });
    throw error;
  }
};

/**
 * Update existing member
 * Endpoint: PUT /api/members/{id}
 *
 * ✅ ENHANCED: Better logging and error handling
 *
 * @param {number} id - Member ID
 * @param {Object} payload - MemberUpdateDto
 * @returns {Promise<Object>} Updated MemberViewDto
 */
export const updateMember = async (id, payload) => {
  console.log(`📝 [Update Member] Updating member ID ${id}, Original payload:`, JSON.stringify(payload, null, 2));

  const normalizedPayload = normalizeMemberRequest(payload);

  console.log(`📝 [Update Member] Normalized payload (after cleanup):`, JSON.stringify(normalizedPayload, null, 2));

  try {
    const response = await axiosClient.put(`${BASE_URL}/${id}`, normalizedPayload);
    const result = unwrap(response);

    console.log(`✅ [Update Member] Member ${id} updated successfully:`, result);
    return result;
  } catch (error) {
    console.error(`❌ [Update Member] Failed to update member ${id}:`, {
      message: error.message,
      status: error.response?.status,
      statusText: error.response?.statusText,
      validationErrors: error.response?.data?.errors,
      backendMessage: error.response?.data?.message,
      payload: normalizedPayload
    });
    throw error;
  }
};

/**
 * Delete member (soft delete)
 * Endpoint: DELETE /api/members/{id}
 * @param {number} id - Member ID
 * @returns {Promise<void>}
 */
export const deleteMember = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get members selector options (for dropdowns)
 * Endpoint: GET /api/members/selector
 * @returns {Promise<Array>} Active members list for selection
 */
export const getMembersSelector = async () => {
  const response = await axiosClient.get(`${BASE_URL}/selector`);
  return unwrap(response);
};

/**
 * Get total members count
 * Endpoint: GET /api/members/count
 * @param {number} employerId - Optional employer ID for filtering
 * @returns {Promise<number>} Total number of members
 */
export const getMembersCount = async (employerId = null) => {
  const params = employerId ? { employerId } : {};
  const response = await axiosClient.get(`${BASE_URL}/count`, { params });
  return unwrap(response);
};

/**
 * 🔍 CRITICAL: Search members for Eligibility Check (PRODUCTION-READY)
 * Endpoint: GET /api/members/search
 *
 * Priority-based search (returns List):
 * 1. memberNumber → Card number (exact match)
 * 2. barcode → Barcode/QR (exact match)
 * 3. name → Full name (partial match, case-insensitive)
 *
 * @param {Object} params - Search parameters (at least one required)
 * @param {string} params.memberNumber - Card/member number (Priority 1)
 * @param {string} params.name - Member full name (Priority 3)
 * @param {string} params.barcode - Barcode/QR value (Priority 2)
 * @returns {Promise<Array>} List of matching members (empty array [] if no match)
 */
export const searchForEligibility = async (params = {}) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/search`, { params });
    const data = response.data?.data;
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('[searchForEligibility] Error:', error);
    // Return empty array instead of throwing - stability for eligibility
    return [];
  }
};

/**
 * @deprecated Use searchForEligibility instead - clearer naming
 */
export const findOneForEligibility = async (params = {}) => {
  const results = await searchForEligibility(params);
  return results.length > 0 ? results[0] : null;
};

/**
 * @deprecated Use searchForEligibility instead - clearer naming
 */
export const unifiedSearchMembers = async (params = {}) => {
  return searchForEligibility(params);
};

/**
 * Search members by query
 * Endpoint: GET /api/members/search
 * @param {string} query - Search query
 * @returns {Promise<Array>} Filtered members list
 */
export const searchMembers = async (query) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, {
    params: { query }
  });
  return unwrap(response);
};

/**
 * Advanced member search by specific field type
 * Endpoint: GET /api/members/search/advanced
 *
 * Search Types:
 * - CARD_NUMBER: Search by card number (exact match)
 * - BARCODE: Search by barcode/QR code (exact match, same as card number)
 * - NAME: Search by name (Arabic or English, partial match)
 * - CIVIL_ID: Search by civil ID (exact match)
 * - PHONE: Search by phone number (partial match)
 *
 * @param {string} searchType - Type of search: CARD_NUMBER, BARCODE, NAME, CIVIL_ID, PHONE
 * @param {string} searchValue - Value to search for
 * @param {number} employerId - Optional employer ID for filtering
 * @returns {Promise<Array>} Matching members list
 */
export const advancedSearchMembers = async (searchType, searchValue, employerId = null) => {
  const params = { searchType, searchValue };
  if (employerId) {
    params.employerId = employerId;
  }
  const response = await axiosClient.get(`${BASE_URL}/search/advanced`, { params });
  return unwrap(response);
};

/**
 * Get member by card number
 * Endpoint: GET /api/members/card/{cardNumber}
 * @param {string} cardNumber - Member card number (format: WAAD|MEMBER|XXXXXX)
 * @returns {Promise<Object>} MemberViewDto
 */
export const getMemberByCard = async (cardNumber) => {
  const response = await axiosClient.get(`${BASE_URL}/card/${encodeURIComponent(cardNumber)}`);
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Get member by barcode/QR code
 * Endpoint: GET /api/members/barcode/{barcode}
 * @param {string} barcode - Barcode or QR code value
 * @returns {Promise<Object>} MemberViewDto
 */
export const getMemberByBarcode = async (barcode) => {
  const response = await axiosClient.get(`${BASE_URL}/barcode/${encodeURIComponent(barcode)}`);
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Get member by civil ID
 * Endpoint: GET /api/members/civil-id/{civilId}
 * Note: Civil ID is optional and NOT required for eligibility
 * @param {string} civilId - Civil ID
 * @returns {Promise<Object>} MemberViewDto
 */
export const getMemberByCivilId = async (civilId) => {
  const response = await axiosClient.get(`${BASE_URL}/civil-id/${encodeURIComponent(civilId)}`);
  return normalizeMemberResponse(unwrap(response));
};

// ============================================================================
// BULK IMPORT OPERATIONS
// ============================================================================

/**
 * Detect columns and suggest mappings from Excel file
 * Endpoint: POST /api/members/import/detect-columns
 * @param {File} file - Excel file (.xlsx)
 * @returns {Promise<Object>} ExcelColumnDetectionDto
 */
export const detectColumns = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axiosClient.post(`${BASE_URL}/import/detect-columns`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return unwrap(response);
};

/**
 * Preview import from Excel file (dry-run)
 * Endpoint: POST /api/members/import/preview
 * @param {File} file - Excel file (.xlsx)
 * @param {Object} customMappings - Optional: User-customized column mappings (excelColumn → systemField)
 * @returns {Promise<Object>} MemberImportPreviewDto
 */
export const previewImport = async (file, customMappings = null) => {
  const formData = new FormData();
  formData.append('file', file);

  if (customMappings) {
    formData.append('customMappings', JSON.stringify(customMappings));
  }

  const response = await axiosClient.post(`${BASE_URL}/import/preview`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

/**
 * Execute import from Excel file
 * Endpoint: POST /api/members/import/execute
 * @param {File} file - Excel file (.xlsx)
 * @param {string} batchId - Import batch ID from preview
 * @param {number} employerId - Selected Employer Organization ID (REQUIRED)
 * @param {number} benefitPolicyId - Selected Benefit Policy ID (OPTIONAL)
 * @returns {Promise<Object>} MemberImportResultDto
 */
export const executeImport = async (file, batchId, employerId, benefitPolicyId = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('batchId', batchId);
  formData.append('employerId', employerId);

  if (benefitPolicyId) {
    formData.append('benefitPolicyId', benefitPolicyId);
  }

  const response = await axiosClient.post(`${BASE_URL}/import/execute`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

/**
 * Get import logs list
 * Endpoint: GET /api/members/import/logs
 * @param {Object} params - Pagination params
 * @returns {Promise<Object>} Paginated import logs
 */
export const getImportLogs = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/import/logs`, { params });
  return unwrap(response);
};

/**
 * Get import log details
 * Endpoint: GET /api/members/import/logs/{batchId}
 * @param {string} batchId - Import batch ID
 * @returns {Promise<Object>} Import log details
 */
export const getImportLogDetails = async (batchId) => {
  const response = await axiosClient.get(`${BASE_URL}/import/logs/${batchId}`);
  return unwrap(response);
};

/**
 * Get import errors for a batch
 * Endpoint: GET /api/members/import/logs/{batchId}/errors
 * @param {string} batchId - Import batch ID
 * @returns {Promise<Array>} List of import errors
 */
export const getImportErrors = async (batchId) => {
  const response = await axiosClient.get(`${BASE_URL}/import/logs/${batchId}/errors`);
  return unwrap(response);
};

/**
 * Get import template info - OLD (Deprecated)
 * Endpoint: GET /api/members/import/template
 * @returns {Promise<Object>} Template column info
 */
export const getImportTemplate = async () => {
  const response = await axiosClient.get(`${BASE_URL}/import/template`);
  return unwrap(response);
};

/**
 * Download members import template (Excel file)
 * Endpoint: GET /api/members/import/template
 * @returns {Promise<Blob>} Excel file blob
 */
export const downloadImportTemplate = async () => {
  const response = await axiosClient.get(`${BASE_URL}/import/template`, {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Upload members excel file (System Template)
 * Endpoint: POST /api/members/import
 * @param {File} file - Excel file
 * @returns {Promise<Object>} Import result
 */
export const uploadMembersExcel = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axiosClient.post(`${BASE_URL}/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return unwrap(response);
};

// ============================================================================
// BENEFIT POLICY ASSIGNMENT
// ============================================================================

/**
 * Assign benefit policy to all members of an employer
 * Endpoint: POST /api/members/employer/{employerId}/assign-benefit-policy
 * @param {number} employerId - Employer ID
 * @param {number} benefitPolicyId - Benefit Policy ID
 * @returns {Promise<Object>} Assignment result with count
 */
export const assignBenefitPolicyToEmployer = async (employerId, benefitPolicyId) => {
  const response = await axiosClient.post(`${BASE_URL}/employer/${employerId}/assign-benefit-policy`, null, {
    params: { benefitPolicyId }
  });
  return unwrap(response);
};

/**
 * Refresh benefit policies for all members of an employer
 * Endpoint: POST /api/members/employer/{employerOrgId}/refresh-policies
 * @param {number} employerOrgId - Employer Organization ID
 * @returns {Promise<number>} Number of members updated
 */
export const refreshBenefitPoliciesForEmployer = async (employerOrgId) => {
  const response = await axiosClient.post(`${BASE_URL}/employer/${employerOrgId}/refresh-policies`);
  return unwrap(response);
};

// ============================================================================
// PHASE 2: STATUS MANAGEMENT
// ============================================================================

/**
 * Suspend member (ACTIVE → SUSPENDED)
 * Endpoint: POST /api/members/{id}/suspend
 * Effects:
 * - Member status → SUSPENDED
 * - Card status → BLOCKED
 * - Eligibility → false
 *
 * @param {number} id - Member ID
 * @param {string} reason - Suspension reason (required)
 * @returns {Promise<Object>} Updated MemberViewDto (normalized)
 */
export const suspendMember = async (id, reason) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/suspend`, { reason });
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Activate member (PENDING/SUSPENDED → ACTIVE)
 * Endpoint: POST /api/members/{id}/activate
 * Effects:
 * - Member status → ACTIVE
 * - Card status → ACTIVE
 * - Eligibility → recalculated
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Updated MemberViewDto (normalized)
 */
export const activateMember = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/activate`);
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Terminate member (ACTIVE/SUSPENDED → TERMINATED)
 * Endpoint: POST /api/members/{id}/terminate
 * Effects:
 * - Member status → TERMINATED
 * - Card status → EXPIRED
 * - active → false
 * - endDate → today
 * - Eligibility → false (permanent)
 *
 * WARNING: This action is IRREVERSIBLE. Terminated members cannot be reactivated.
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Updated MemberViewDto (normalized)
 */
export const terminateMember = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/terminate`);
  return normalizeMemberResponse(unwrap(response));
};

// ============================================================================
// PHASE 2: CARD MANAGEMENT
// ============================================================================

/**
 * Block member card (e.g., lost card, security issue)
 * Endpoint: POST /api/members/{id}/card/block
 * Effects:
 * - Card status → BLOCKED
 * - Eligibility → false
 *
 * @param {number} id - Member ID
 * @param {string} reason - Block reason (required)
 * @returns {Promise<Object>} Updated MemberViewDto (normalized)
 */
export const blockCard = async (id, reason) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/card/block`, { reason });
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Activate member card
 * Endpoint: POST /api/members/{id}/card/activate
 * Effects:
 * - Card status → ACTIVE
 * - Eligibility → recalculated (if member status is ACTIVE)
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Updated MemberViewDto (normalized)
 */
export const activateCard = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/card/activate`);
  return normalizeMemberResponse(unwrap(response));
};

// ============================================================================
// PHASE 2: ELIGIBILITY CHECK
// ============================================================================

/**
 * Get member by card number
 * Endpoint: GET /api/members/card/{cardNumber}
 * @param {string} cardNumber - Member card number
 * @returns {Promise<Object>} Member details (normalized)
 */
export const getMemberByCardNumber = async (cardNumber) => {
  const response = await axiosClient.get(`${BASE_URL}/card/${cardNumber}`);
  return normalizeMemberResponse(unwrap(response));
};

/**
 * Check member eligibility for medical services
 * Endpoint: GET /api/members/{id}/eligibility
 *
 * Eligibility Conditions (ALL must be true):
 * 1. member.active = true
 * 2. member.status = ACTIVE
 * 3. member.cardStatus = ACTIVE
 * 4. member.benefitPolicy != null
 * 5. policy.status = ACTIVE
 * 6. policy is effective on serviceDate
 * 7. employer.active = true
 *
 * NOTE: Civil ID is NOT required for eligibility
 *
 * @param {number} id - Member ID
 * @param {string} serviceDate - Service date (yyyy-MM-dd, optional - defaults to today)
 * @returns {Promise<Object>} EligibilityResponseDto
 * @returns {boolean} eligible - Whether member is eligible
 * @returns {Array<Object>} ineligibilityReasons - Reasons if not eligible (code, messageAr, messageEn)
 * @returns {Object} policyInfo - Benefit policy details
 * @returns {Object} employerInfo - Employer details
 */
export const checkEligibility = async (id, serviceDate = null) => {
  const params = serviceDate ? { serviceDate } : {};
  const response = await axiosClient.get(`${BASE_URL}/${id}/eligibility`, { params });
  return unwrap(response);
};

/**
 * Check member eligibility by card number (Phase 1: Unified Smart Search)
 * Endpoint: GET /api/members/check-eligibility?cardNumber=...
 * Fast indexed lookup for eligibility verification
 *
 * @param {string} cardNumber - Card number to search
 * @returns {Promise<Object>} EligibilityCheckDto
 * @returns {string} fullName - Member full name
 * @returns {string} status - Member status (ACTIVE, SUSPENDED, etc.)
 * @returns {number} copayAmount - Copayment amount
 * @returns {string} cardNumber - Card number searched
 * @returns {boolean} eligible - Whether member is eligible
 * @returns {string} message - Additional message (e.g., warnings)
 */
export const checkEligibilityByCardNumber = async (cardNumber) => {
  const response = await axiosClient.get(`${BASE_URL}/check-eligibility`, {
    params: { cardNumber }
  });
  return unwrap(response);
};

/**
 * Search members by name with autocomplete (Phase 2: Fuzzy Name Search)
 * Endpoint: GET /api/members/search?query=...
 * Fuzzy Arabic name search with typo tolerance
 * Minimum 3 characters required
 *
 * @param {string} query - Search query (Arabic or English name)
 * @returns {Promise<Array>} Array of MemberAutocompleteDto
 * @returns {number} memberId - Member ID
 * @returns {string} fullName - Member full name
 * @returns {string} cardNumber - Card number
 * @returns {number} similarity - Similarity score (0.0 to 1.0)
 */
export const searchMembersByName = async (query) => {
  if (!query || query.trim().length < 3) {
    return [];
  }
  const response = await axiosClient.get(`${BASE_URL}/search`, {
    params: { query: query.trim() }
  });
  // Response is direct array, not wrapped in ApiResponse
  return response.data || [];
};

/**
 * Unified Member Search (Phase 3: Barcode/QR Support)
 * Auto-detects search type based on query pattern:
 * - UUID (8-4-4-4-12) → Barcode/QR search
 * - Numeric → Card number search
 * - Text → Fuzzy name search
 *
 * Endpoint: GET /api/members/search?query={query}
 * Backend: UnifiedSearchController
 *
 * @param {string} query - Search query (card number, name, or barcode UUID)
 * @returns {Promise<Object>} ApiResponse<List<MemberSearchDto>>
 * @returns {string} status - "success" or "error"
 * @returns {string} message - Response message
 * @returns {Array} data - List of MemberSearchDto
 * @returns {string} data[].id - Member ID
 * @returns {string} data[].fullName - Member full name
 * @returns {string} data[].cardNumber - Card number
 * @returns {string} data[].barcode - Barcode/QR UUID
 * @returns {string} data[].status - Member status
 * @returns {string} data[].cardStatus - Card status
 * @returns {boolean} data[].eligible - Eligibility status
 * @returns {string} data[].employerName - Employer name
 * @returns {string} data[].policyName - Benefit policy name
 * @returns {number} data[].copayAmount - Copayment percentage
 * @returns {number} data[].coverageLimit - Coverage limit
 * @returns {string} data[].message - Status message
 * @returns {string} data[].searchType - "BARCODE", "CARD_NUMBER", or "NAME_FUZZY"
 * @returns {number} data[].similarityScore - Similarity score (for fuzzy search)
 */
export const unifiedMemberSearch = async (query) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, {
    params: { query: query.trim() }
  });
  return response.data; // Return full ApiResponse structure
};



// Default export for convenient imports
const membersService = {
  // CRUD operations
  getMembers,
  getMemberById,
  createMember,
  updateMember,
  deleteMember,
  getMembersSelector,
  getMembersCount,
  searchMembers,
  // Unified Search for Eligibility Check (PRIMARY - PRODUCTION-READY)
  searchForEligibility,
  findOneForEligibility, // @deprecated - use searchForEligibility
  unifiedSearchMembers, // @deprecated - use searchForEligibility
  // Advanced Search operations (for Eligibility Check page)
  advancedSearchMembers,
  getMemberByCard,
  getMemberByBarcode,
  getMemberByCivilId,
  // Import operations
  previewImport,
  executeImport,
  getImportLogs,
  getImportLogDetails,
  getImportErrors,
  getImportTemplate,
  downloadImportTemplate,
  uploadMembersExcel,
  // Benefit Policy operations
  refreshBenefitPoliciesForEmployer,
  assignBenefitPolicyToEmployer,
  // Phase 2: Field Normalizers
  normalizeMemberRequest,
  normalizeMemberResponse,
  // Phase 2: Status Management
  suspendMember,
  activateMember,
  terminateMember,
  // Phase 2: Card Management
  blockCard,
  activateCard,
  // Phase 2: Eligibility Check
  checkEligibility,
  checkEligibilityByCardNumber,
  // Phase 2: Fuzzy Name Search + Autocomplete
  searchMembersByName,
  // Phase 3: Unified Search (Card Number + Name + Barcode/QR)
  unifiedMemberSearch,
  // Bulk Operations
  deleteAllMembersByEmployer
};

export default membersService;
