/**
 * Unified Members Service
 *
 * Service for managing members using the new Unified Architecture.
 * Replaces legacy Member + FamilyMember anti-pattern with unified Member entity.
 *
 * Architecture:
 * - Principal: parent_id = NULL, has Barcode (WAHA-YYYY-NNNNNN)
 * - Dependent: parent_id references Principal, NO Barcode
 * - Card Numbers: Principal (NNNNNN), Dependent (NNNNNN-NN)
 * - Single-level hierarchy (depth = 1)
 *
 * @module UnifiedMembersService
 * @since 2026-01-11
 */

import api from '../../utils/axios';

const UNIFIED_MEMBERS_BASE_URL = '/unified-members';

/**
 * Create a new Principal member with optional inline Dependents
 *
 * @param {Object} memberData - Principal member data
 * @param {string} memberData.fullName - Full name (required)
 * @param {string} [memberData.nationalNumber] - National ID (optional)
 * @param {string} memberData.birthDate - Birth date (required)
 * @param {string} memberData.gender - Gender: MALE/FEMALE (required)
 * @param {string} [memberData.maritalStatus] - Marital status
 * @param {string} [memberData.phone] - Phone number
 * @param {string} [memberData.email] - Email address
 * @param {number} memberData.employerId - Employer organization ID (required)
 * @param {number} [memberData.benefitPolicyId] - Benefit policy ID
 * @param {Array} [memberData.dependents] - Array of dependents
 * @returns {Promise<Object>} Created Principal member with dependents
 */
export const createPrincipalMember = async (memberData) => {
  try {
    const response = await api.post(UNIFIED_MEMBERS_BASE_URL, memberData);
    return response.data;
  } catch (error) {
    console.error('Error creating principal member:', error);
    throw error;
  }
};

/**
 * Add a Dependent to an existing Principal
 *
 * @param {number} principalId - Principal member ID
 * @param {Object} dependentData - Dependent member data
 * @param {string} dependentData.relationship - Relationship: SPOUSE, SON, DAUGHTER, etc. (required)
 * @param {string} dependentData.fullName - Full name (required)
 * @param {string} dependentData.birthDate - Birth date (required)
 * @param {string} dependentData.gender - Gender: MALE/FEMALE (required)
 * @param {string} [dependentData.nationalNumber] - National ID (optional)
 * @returns {Promise<Object>} Updated Principal member with new dependent
 */
export const addDependent = async (principalId, dependentData) => {
  try {
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/dependents`, dependentData);
    return response.data;
  } catch (error) {
    console.error('Error adding dependent:', error);
    throw error;
  }
};

/**
 * Get a member by ID with their dependents
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Member details with dependents array
 */
export const getMember = async (id) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${id}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching member:', error);
    throw error;
  }
};

/**
 * Get all members with pagination and filtering
 *
 * @param {Object} params - Query parameters
 * @param {number} [params.page=0] - Page number
 * @param {number} [params.size=20] - Page size
 * @param {number} [params.organizationId] - Filter by organization
 * @param {string} [params.status] - Filter by status: ACTIVE, SUSPENDED, TERMINATED
 * @param {string} [params.type] - Filter by type: PRINCIPAL, DEPENDENT
 * @param {boolean} [params.deleted] - Show deleted members
 * @returns {Promise<Object>} Paginated list of members
 */
export const getAllMembers = async (params = {}) => {
  try {
    const normalizedParams = { ...params };
    if (normalizedParams.organizationId && !normalizedParams.employerId) {
      normalizedParams.employerId = normalizedParams.organizationId;
      delete normalizedParams.organizationId;
    }

    const response = await api.get(UNIFIED_MEMBERS_BASE_URL, { params: normalizedParams });
    return response.data;
  } catch (error) {
    console.error('Error fetching members:', error);
    throw error;
  }
};

/**
 * Count members based on filters
 * @param {Object} filters - Search filters (employerId, status, type)
 * @returns {Promise<number>} Count of members
 */
export const countMembers = async (filters = {}) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/count`, { params: filters });
  return response.data?.data || response.data || 0;
};

/**
 * Advanced search for members
 *
 * @param {Object} criteria - Search criteria
 * @param {string} [criteria.fullName] - Full name search
 * @param {string} [criteria.civilId] - Civil ID filter
 * @param {string} [criteria.barcode] - Barcode filter
 * @param {string} [criteria.cardNumber] - Card number filter
 * @param {number} [criteria.organizationId] - Organization filter
 * @param {number} [criteria.benefitPolicyId] - Benefit policy filter
 * @param {string} [criteria.status] - Status filter
 * @param {string} [criteria.type] - Member type filter
 * @param {boolean} [criteria.deleted] - Show deleted members
 * @param {number} [criteria.page=0] - Page number
 * @param {number} [criteria.size=20] - Page size
 * @returns {Promise<Object>} Search results
 */
export const searchMembers = async (criteria = {}) => {
  try {
    const normalizedCriteria = { ...criteria };
    if (normalizedCriteria.organizationId && !normalizedCriteria.employerId) {
      normalizedCriteria.employerId = normalizedCriteria.organizationId;
      delete normalizedCriteria.organizationId;
    }

    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/search`, { params: normalizedCriteria });
    return response.data;
  } catch (error) {
    console.error('Error searching members:', error);
    throw error;
  }
};

/**
 * Unified search for members (Auto-detects type)
 *
 * @param {string} query - Search query
 * @returns {Promise<Array>} List of results
 */
export const unifiedSearch = async (query, employerId = null) => {
  // Guard: don't hit the server with empty queries
  if (!query || !query.trim()) return [];

  try {
    const params = { query };
    if (employerId) {
      params.employerId = employerId;
    }
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/unified-search`, { params });
    let results = response.data?.data || [];

    // Fallback: If results are empty and query is not empty, try a broader search.
    // The backend's unifiedSearch can be strict (exact match for numbers, 3-char min for names).
    if (results.length === 0 && query && query.trim().length > 0) {
      const trimmedQuery = query.trim();
      const criteria = {};

      // If it looks like a number, search specifically by card number (partial)
      if (/^\d+$/.test(trimmedQuery)) {
        criteria.cardNumber = trimmedQuery;
      } else {
        // Otherwise search by full name (partial)
        criteria.fullName = trimmedQuery;
      }

      if (employerId) {
        criteria.employerId = employerId;
      }

      // Call searchMembers (GET /unified-members/search) which uses LIKE %...%
      const advancedResponse = await searchMembers({ ...criteria, size: 20 });
      if (advancedResponse?.content?.length > 0) {
        results = advancedResponse.content.map((m) => ({
          id: m.id,
          fullName: m.fullName,
          cardNumber: m.cardNumber,
          barcode: m.barcode,
          status: m.status,
          cardStatus: m.cardStatus,
          eligible: m.eligibilityStatus,
          employerName: m.employerName,
          policyName: m.benefitPolicyName,
          searchType: 'PARTIAL_MATCH'
        }));
      }
    }

    return results;
  } catch (error) {
    console.error('Unified search failed:', error);
    throw error;
  }
};

/**
 * Deterministic beneficiary search (explicit mode, no guessing).
 *
 * Endpoint: GET /api/beneficiaries?type=BY_NAME&value=...
 *
 * @param {Object} params
 * @param {'BY_ID'|'BY_NAME'|'BY_BARCODE'} params.type
 * @param {string} params.value
 * @param {number} [params.employerId]
 * @param {string} [params.status='ACTIVE']
 * @param {number} [params.size=20]
 * @returns {Promise<Array>} List<MemberViewDto>
 */
export const searchBeneficiaries = async (params = {}) => {
  const response = await api.get('/beneficiaries', { params });
  return response?.data?.data || [];
};

/**
 * Check family eligibility by Principal's Barcode
 *
 * @param {string} barcode - Principal's barcode (WAHA-YYYY-NNNNNN)
 * @returns {Promise<Object>} Family eligibility response with all family members
 */
export const checkEligibility = async (barcode) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/eligibility/${barcode}`);
    return response.data;
  } catch (error) {
    console.error('Error checking eligibility:', error);
    throw error;
  }
};

/**
 * Update a member (Principal or Dependent)
 *
 * @param {number} id - Member ID
 * @param {Object} updateData - Updated member data
 * @returns {Promise<Object>} Updated member
 */
export const updateMember = async (id, updateData) => {
  try {
    const response = await api.put(`${UNIFIED_MEMBERS_BASE_URL}/${id}`, updateData);
    return response.data;
  } catch (error) {
    console.error('Error updating member:', error);
    throw error;
  }
};

/**
 * Delete a member
 * - If Principal: CASCADE delete all dependents
 * - If Dependent: Delete only this dependent
 *
 * @param {number} id - Member ID
 * @returns {Promise<void>}
 */
export const deleteMember = async (id) => {
  try {
    await api.delete(`${UNIFIED_MEMBERS_BASE_URL}/${id}`);
  } catch (error) {
    console.error('Error deleting member:', error);
    throw error;
  }
};

/**
 * End a member's membership. Nothing is physically deleted -- status
 * becomes TERMINATED. Prefer this over deleteMember() in new code; that
 * name is kept only for existing callers (it calls the same backend
 * endpoint that this function's route now aliases to internally).
 *
 * @param {number} id - Member ID
 * @param {string} [reason] - Reason recorded on the transition
 * @returns {Promise<void>}
 */
export const terminateMembership = async (id, reason) => {
  try {
    await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${id}/terminate`, null, { params: { reason } });
  } catch (error) {
    console.error('Error terminating membership:', error);
    throw error;
  }
};

/**
 * Bulk delete members by IDs
 *
 * @param {Array<number>} ids - Array of Member IDs
 * @returns {Promise<Object>} Response
 */
export const bulkDeleteMembers = async (ids) => {
  try {
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/bulk-delete`, ids);
    return response.data;
  } catch (error) {
    console.error('Error bulk deleting members:', error);
    throw error;
  }
};

/**
 * Restore a deleted member
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Response
 */
export const restoreMember = async (id) => {
  try {
    const response = await api.put(`${UNIFIED_MEMBERS_BASE_URL}/${id}/restore`);
    return response.data;
  } catch (error) {
    console.error('Error restoring member:', error);
    throw error;
  }
};

/**
 * Toggle active/inactive status for a member.
 *
 * active=true restores from SUSPENDED (rejects a TERMINATED member -- use
 * reinstateTerminatedMember for that). active=false suspends and requires
 * a reason.
 *
 * @param {number} id - Member ID
 * @param {boolean} active - true to activate, false to deactivate
 * @param {string} [reason] - Required when active=false
 * @returns {Promise<Object>} Updated member
 */
export const toggleMemberActive = async (id, active, reason) => {
  try {
    const response = await api.patch(`${UNIFIED_MEMBERS_BASE_URL}/${id}/active`, null, { params: { active, reason } });
    return response.data;
  } catch (error) {
    console.error('Error toggling member active status:', error);
    throw error;
  }
};

/**
 * TERMINATED -> ACTIVE. Exceptional action requiring SUPER_ADMIN and a
 * mandatory reason -- distinct from the ordinary restore/toggle-active
 * path, which explicitly refuses to touch a TERMINATED member.
 *
 * @param {number} id - Member ID
 * @param {string} reason - Mandatory reason
 * @returns {Promise<Object>} Updated member
 */
export const reinstateTerminatedMember = async (id, reason) => {
  try {
    const response = await api.put(`${UNIFIED_MEMBERS_BASE_URL}/${id}/reinstate`, null, { params: { reason } });
    return response.data;
  } catch (error) {
    console.error('Error reinstating terminated member:', error);
    throw error;
  }
};

/**
 * Restores exactly the dependents ONE specific suspend/terminate family
 * cascade affected (identified by transitionId, found on the principal's
 * statusTransitionId after that cascade ran) -- never every dependent
 * currently sharing that status, and never one who changed independently
 * since. Restoring the principal never does this automatically.
 *
 * @param {string} transitionId
 * @returns {Promise<Object>} { restoredMemberIds, skipped }
 */
export const restoreFamily = async (transitionId) => {
  try {
    const response = await api.put(`${UNIFIED_MEMBERS_BASE_URL}/family-restore/${transitionId}`);
    return response.data;
  } catch (error) {
    console.error('Error restoring family cascade:', error);
    throw error;
  }
};

/**
 * Change a member's membership status (ACTIVE / SUSPENDED / PENDING / TERMINATED)
 *
 * @param {number} id - Member ID
 * @param {string} status - Target status: 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'TERMINATED'
 * @param {string} [reason] - Required when status is 'SUSPENDED'
 * @returns {Promise<Object>} Updated member
 */
export const changeMemberStatus = async (id, status, reason) => {
  try {
    const response = await api.patch(`${UNIFIED_MEMBERS_BASE_URL}/${id}/status`, null, { params: { status, reason } });
    return response.data;
  } catch (error) {
    console.error('Error changing member status:', error);
    throw error;
  }
};

/**
 * Physically delete a member from the database. SUPER_ADMIN only, blocked
 * entirely if any financial/medical/audit footprint exists, and requires a
 * reason -- an independent (non-FK'd) audit record is written before the
 * delete.
 *
 * @param {number} id - Member ID
 * @param {string} reason - Mandatory reason
 * @returns {Promise<void>}
 */
export const hardDeleteMember = async (id, reason) => {
  try {
    await api.delete(`${UNIFIED_MEMBERS_BASE_URL}/${id}/hard`, { params: { reason } });
  } catch (error) {
    console.error('Error physically deleting member:', error);
    throw error;
  }
};

/**
 * Get dependents of a Principal member
 *
 * @param {number} principalId - Principal member ID
 * @returns {Promise<Array>} List of dependents
 */
export const getDependents = async (principalId) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/dependents`);
    return response.data;
  } catch (error) {
    console.error('Error fetching dependents:', error);
    throw error;
  }
};

/**
 * Count dependents of a Principal member
 *
 * @param {number} principalId - Principal member ID
 * @returns {Promise<number>} Count of dependents
 */
export const countDependents = async (principalId) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/dependents/count`);
    return response.data;
  } catch (error) {
    console.error('Error counting dependents:', error);
    throw error;
  }
};

/**
 * Import members from Excel file
 *
 * @param {File} file - Excel file
 * @returns {Promise<any>} Import result
 */
export const importMembers = async (file, clearOldMembers = false, employerId = null) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    if (clearOldMembers) {
      formData.append('clearOldMembers', clearOldMembers);
    }
    if (employerId) {
      formData.append('employerId', employerId);
    }
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 300000 // 5 minutes for large Excel files
    });
    return response.data;
  } catch (error) {
    console.error('Error importing members:', error);

    // Preserve server response data for better error display
    if (error.response?.data) {
      error.importResult = error.response.data.data; // The ExcelImportResult
      error.serverMessage = error.response.data.message;
    }

    throw error;
  }
};

/**
 * Detect Excel columns and suggest mappings
 *
 * @param {File} file - Excel file
 * @returns {Promise<any>} Detection result
 */
export const detectColumns = async (file) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/import/detect-columns`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data;
  } catch (error) {
    console.error('Error detecting columns:', error);
    throw error;
  }
};

/**
 * Preview Excel import
 *
 * @param {File} file - Excel file
 * @param {Object} customMappings - Optional mappings
 * @returns {Promise<any>} Preview result
 */
export const previewImport = async (file, customMappings = null, headerRowNumber = null) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    if (customMappings) {
      formData.append('customMappings', JSON.stringify(customMappings));
    }
    if (headerRowNumber !== null && headerRowNumber !== undefined) {
      formData.append('headerRowNumber', headerRowNumber);
    }
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/import/preview`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data;
  } catch (error) {
    console.error('Error previewing import:', error);
    throw error;
  }
};

/**
 * Execute Excel import
 *
 * @param {File} file - Excel file
 * @param {Object} params - Import params (employerId, benefitPolicyId, batchId)
 * @returns {Promise<any>} Import result
 */
export const executeImport = async (file, params) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('employerId', params.employerId);
    if (params.benefitPolicyId) formData.append('benefitPolicyId', params.benefitPolicyId);
    if (params.batchId) formData.append('batchId', params.batchId);
    if (params.headerRowNumber !== null && params.headerRowNumber !== undefined) {
      formData.append('headerRowNumber', params.headerRowNumber);
    }
    if (params.importPolicy) {
      formData.append('importPolicy', params.importPolicy);
    }
    if (params.clearOldMembers !== undefined) {
      formData.append('clearOldMembers', params.clearOldMembers);
    }

    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/import/execute`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000
    });
    return response.data;
  } catch (error) {
    console.error('Error executing import:', error);
    throw error;
  }
};

/**
 * Get import status (for polling)
 *
 * @param {string} batchId - Import batch ID
 * @returns {Promise<any>} Status result
 */
export const getImportStatus = async (batchId) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/import/status/${batchId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching import status:', error);
    throw error;
  }
};

/**
 * Download members import template
 *
 * @returns {Promise<Blob>} Template file blob
 */
export const downloadTemplate = async () => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/import/template`, {
      responseType: 'blob'
    });
    return response.data;
  } catch (error) {
    console.error('Error downloading template:', error);
    throw error;
  }
};

/**
 * Export members to Excel based on filters
 *
 * @param {Object} params - Filter parameters
 * @returns {Promise<Blob>} Excel file blob
 */
export const exportMembers = async (params = {}) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/export/excel`, {
      params,
      responseType: 'blob'
    });
    return response.data;
  } catch (error) {
    console.error('Error exporting members:', error);
    throw error;
  }
};

/**
 * Member relationships enum
 * Values must match Backend: BROTHER, WIFE, SON, MOTHER, SISTER, DAUGHTER, HUSBAND, FATHER
 */
export const RELATIONSHIPS = {
  WIFE: 'WIFE',
  HUSBAND: 'HUSBAND',
  SON: 'SON',
  DAUGHTER: 'DAUGHTER',
  FATHER: 'FATHER',
  MOTHER: 'MOTHER',
  BROTHER: 'BROTHER',
  SISTER: 'SISTER'
};

/**
 * Member genders enum
 */
export const GENDERS = {
  MALE: 'MALE',
  FEMALE: 'FEMALE',
  UNDEFINED: 'UNDEFINED'
};

/**
 * Member statuses enum
 */
export const MEMBER_STATUSES = {
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
  TERMINATED: 'TERMINATED',
  PENDING: 'PENDING'
};

/**
 * Member types enum
 */
export const MEMBER_TYPES = {
  PRINCIPAL: 'PRINCIPAL',
  DEPENDENT: 'DEPENDENT'
};

/**
 * Upload Member Photo
 *
 * @param {number} id - Member ID
 * @param {File} file - Image file
 * @returns {Promise<Object>} Response
 */
export const uploadPhoto = async (id, file) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${id}/photo`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data;
  } catch (error) {
    console.error('Error uploading photo:', error);
    throw error;
  }
};

/**
 * Delete Member Photo
 *
 * @param {number} id - Member ID
 * @returns {Promise<Object>} Response
 */
export const deletePhoto = async (id) => {
  try {
    const response = await api.delete(`${UNIFIED_MEMBERS_BASE_URL}/${id}/photo`);
    return response.data;
  } catch (error) {
    console.error('Error deleting photo:', error);
    throw error;
  }
};

/**
 * Get financial summary for a member
 *
 * @param {number} memberId - Member ID
 * @returns {Promise<Object>} Financial summary
 */
export const getFinancialSummary = async (memberId) => {
  try {
    const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${memberId}/financial-summary`);
    return response.data;
  } catch (error) {
    console.error('Error fetching member financial summary:', error);
    throw error;
  }
};

export default {
  createPrincipalMember,
  addDependent,
  getMember,
  getAllMembers,
  unifiedSearch,
  searchMembers,
  countMembers,
  searchBeneficiaries,
  checkEligibility,
  updateMember,
  deleteMember,
  restoreMember,
  hardDeleteMember,
  getDependents,
  countDependents,
  importMembers,
  detectColumns,
  previewImport,
  executeImport,
  exportMembers,
  downloadTemplate,
  uploadPhoto,
  deletePhoto,
  getFinancialSummary,
  RELATIONSHIPS,
  GENDERS,
  MEMBER_STATUSES,
  MEMBER_TYPES
};
