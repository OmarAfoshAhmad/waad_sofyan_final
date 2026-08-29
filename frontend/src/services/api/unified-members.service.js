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
import { prepareProfilePhoto } from 'utils/profile-photo';

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
 * Check family eligibility by Principal's Barcode
 *
 * @param {string} barcode - Principal's barcode (WAHA-YYYY-NNNNNN)
 * @returns {Promise<Object>} Family eligibility response with all family members
 */
export const checkEligibility = async (barcode) => {
  try {
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/eligibility/evaluations`, {
      barcode,
      serviceDate: new Date().toISOString().slice(0, 10)
    });
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
    await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${id}/terminate`, { reason });
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
export const bulkDeleteMembers = async (ids, reason) => {
  try {
    const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/bulk-delete`, { ids, reason });
    return response.data;
  } catch (error) {
    console.error('Error bulk deleting members:', error);
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
    const response = await api.patch(`${UNIFIED_MEMBERS_BASE_URL}/${id}/active`, { reason }, { params: { active } });
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
    const response = await api.put(`${UNIFIED_MEMBERS_BASE_URL}/${id}/reinstate`, { reason });
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
 * Move a dependent to a different principal's family. Atomic, dated,
 * requires the dependent's current row version to guard against a
 * concurrent edit.
 */
export const transferDependent = async (dependentId, { newPrincipalId, relationship, effectiveDate, reason, expectedVersion }) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${dependentId}/family-transfer`, {
    newPrincipalId,
    relationship,
    effectiveDate,
    reason,
    expectedVersion
  });
  return response.data;
};

/** Corrects a dependent's kinship/relationship value -- a dedicated, audited operation, not a field edit. */
export const correctRelationship = async (dependentId, { relationship, reason, expectedVersion }) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${dependentId}/relationship-correction`, {
    relationship,
    reason,
    expectedVersion
  });
  return response.data;
};

/**
 * Changes the whole family's benefit policy as of an effective date. All or
 * nothing: expectedVersions must name every affected member's current
 * version or the whole call is rejected.
 */
export const changeFamilyPolicy = async (principalId, { policyId, effectiveDate, reason, expectedVersions }) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/family-policy`, {
    policyId,
    effectiveDate,
    reason,
    expectedVersions
  });
  return response.data;
};

/** Reorders a family's dependents for display only -- never touches card number or barcode. */
export const reorderFamily = async (principalId, { dependentIds, expectedVersions }) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/family-order`, {
    dependentIds,
    expectedVersions
  });
  return response.data;
};

/** Read-only impact preview for transferring a principal and their whole family to another employer. */
export const previewEmployerTransfer = async (principalId, newEmployerId) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/employer-transfer/preview`, {
    params: { newEmployerId }
  });
  return response.data;
};

/**
 * Moves a principal and their whole family to another employer as of an
 * effective date. All-or-nothing: expectedVersions must name every family
 * member's current version. Pass noPolicy:true instead of newPolicyId only
 * to explicitly confirm the family should carry no policy for now.
 */
export const transferEmployerFamily = async (principalId, { newEmployerId, newPolicyId, noPolicy, effectiveDate, reason, expectedVersions }) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${principalId}/employer-transfer`, {
    newEmployerId,
    newPolicyId: noPolicy ? null : newPolicyId,
    noPolicy: Boolean(noPolicy),
    effectiveDate,
    reason,
    expectedVersions
  });
  return response.data;
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
    const response = await api.patch(`${UNIFIED_MEMBERS_BASE_URL}/${id}/status`, { reason }, { params: { status } });
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
    await api.delete(`${UNIFIED_MEMBERS_BASE_URL}/${id}/hard`, { data: { reason } });
  } catch (error) {
    console.error('Error physically deleting member:', error);
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
export const previewImport = async (file, params = {}) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    if (params.employerId) {
      formData.append('employerId', params.employerId);
    }
    if (params.headerRowNumber !== null && params.headerRowNumber !== undefined) {
      formData.append('headerRowNumber', params.headerRowNumber);
    }
    if (params.benefitPolicyId) formData.append('benefitPolicyId', params.benefitPolicyId);
    if (params.clearOldMembers !== undefined) formData.append('clearOldMembers', params.clearOldMembers);
    if (params.customMappings) {
      formData.append('customMappingsJson', JSON.stringify(params.customMappings));
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
    if (params.customMappings) {
      formData.append('customMappingsJson', JSON.stringify(params.customMappings));
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
 * @param {Object} [filters] status ('ALL' or an ImportStatus), search (file
 *   name, batch id or who ran it), from/to as YYYY-MM-DD. Empty values are
 *   dropped rather than sent as blanks, so the server sees "no filter" and
 *   not "match the empty string".
 */
export const getImportLogs = async (page = 1, size = 20, filters = {}) => {
  const params = { page, size };
  if (filters.status && filters.status !== 'ALL') params.status = filters.status;
  if (filters.search?.trim()) params.search = filters.search.trim();
  if (filters.from) params.from = filters.from;
  if (filters.to) params.to = filters.to;

  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/import/logs`, { params });
  return response.data;
};

export const getImportErrors = async (batchId) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/import/errors/${batchId}`);
  return response.data;
};

export const previewImportRollback = async (batchId) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/import/${batchId}/rollback/preview`);
  return response.data;
};

export const executeImportRollback = async (batchId, reason) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/import/${batchId}/rollback`, { reason });
  return response.data;
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
      params: {
        searchQuery: params.searchQuery ?? params.searchTerm,
        employerId: params.employerId ?? params.organizationId,
        benefitPolicyId: params.benefitPolicyId,
        status: params.status,
        type: params.type,
        includeDeleted: params.includeDeleted ?? params.deleted ?? false
      },
      responseType: 'blob'
    });
    return response.data;
  } catch (error) {
    console.error('Error exporting members:', error);
    throw error;
  }
};

/** Canonical workbook that can be sent back through preview -> execute. */
export const exportReimportableMembers = async (params = {}) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/export/reimportable-excel`, {
    params: {
      searchQuery: params.searchQuery ?? params.searchTerm,
      employerId: params.employerId ?? params.organizationId,
      benefitPolicyId: params.benefitPolicyId,
      status: params.status,
      type: params.type,
      includeDeleted: params.includeDeleted ?? params.deleted ?? false
    },
    responseType: 'blob'
  });
  return response.data;
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
/**
 * Not a member status, and sent through the status filter anyway.
 *
 * A member with an exceptional ceiling uplift is also ACTIVE, or SUSPENDED,
 * or anything else -- the two are different questions. It travels through the
 * status control because that is where someone looks for it, and the server
 * recognises it before parsing a status and applies it as its own predicate.
 * See MemberFilter.WITH_UPLIFT.
 */
export const MEMBER_FILTER_WITH_UPLIFT = 'WITH_UPLIFT';

/**
 * Every exception ever granted on this member's ceiling, the ended included.
 * Guarded server-side by MEMBER_LIMIT_UPLIFT_MANAGE.
 */
export const getLimitUplifts = async (memberId) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${memberId}/limit-uplifts`);
  return response.data;
};

export const grantLimitUplift = async (memberId, payload) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/${memberId}/limit-uplifts`, payload);
  return response.data;
};

/** Ends one early. The row is kept; only its window closes. */
export const revokeLimitUplift = async (upliftId, reason) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/limit-uplifts/${upliftId}/revoke`, { reason });
  return response.data;
};

export const MEMBER_STATUSES = {
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
  TERMINATED: 'TERMINATED',
  PENDING: 'PENDING',
  DUPLICATE_MERGED: 'DUPLICATE_MERGED'
};

/** Arabic display labels. TERMINATED reads as "ended membership", not "deleted" -- the record still exists. */
export const MEMBER_STATUS_LABELS = {
  ACTIVE: 'نشط',
  SUSPENDED: 'معلّق',
  PENDING: 'قيد المراجعة',
  TERMINATED: 'منتهية العضوية',
  DUPLICATE_MERGED: 'مدموج'
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
 * The file is shrunk to avatar dimensions and re-encoded as WebP here rather
 * than at each of the five screens that upload one, so no screen can forget
 * and none of them can disagree about the size or the quality. If the
 * browser cannot encode WebP the prepared file comes back as JPEG, which the
 * server accepts too; if it cannot be read as an image at all, the original
 * is sent and the server rejects it with its own message rather than this
 * layer inventing one.
 *
 * @param {number} id - Member ID
 * @param {File} file - Image file
 * @returns {Promise<Object>} Response
 */
export const uploadPhoto = async (id, file) => {
  try {
    let toSend = file;
    try {
      toSend = (await prepareProfilePhoto(file)).file;
    } catch (conversionError) {
      console.warn('Photo could not be re-encoded, sending as chosen:', conversionError?.message);
    }

    const formData = new FormData();
    formData.append('file', toSend);
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
 * Current general ceiling for a whole page of members, in one request.
 *
 * POST because the ids are a body: a page of them is long, and ids in a URL
 * reach access logs, APM and browser history. It is a read.
 *
 * Call this once per page. A call per row puts the request count on the rows,
 * which is the cost the bulk backend path exists to remove.
 *
 * @param {number[]} memberIds - the members on the current page
 * @returns {Promise<Object>} map of member id to ceiling summary
 */
export const getLimitsOverview = async (memberIds) => {
  const response = await api.post(`${UNIFIED_MEMBERS_BASE_URL}/limits/overview`, { memberIds });
  return response.data;
};

/**
 * One member's general ceiling and every bucket under it.
 *
 * Called only when the drawer opens, never as part of rendering a list. The
 * general figures come from the same read the column used, so the two agree by
 * construction; readAt says how far apart the two reads were.
 *
 * @param {number} memberId
 * @returns {Promise<Object>} general summary plus bucket balances
 */
export const getLimitDetail = async (memberId) => {
  const response = await api.get(`${UNIFIED_MEMBERS_BASE_URL}/${memberId}/limits/detail`);
  return response.data;
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
  checkEligibility,
  updateMember,
  deleteMember,
  hardDeleteMember,
  detectColumns,
  previewImport,
  executeImport,
  getImportLogs,
  getImportErrors,
  previewImportRollback,
  executeImportRollback,
  exportMembers,
  exportReimportableMembers,
  downloadTemplate,
  uploadPhoto,
  deletePhoto,
  getFinancialSummary,
  getLimitsOverview,
  getLimitDetail,
  RELATIONSHIPS,
  GENDERS,
  getLimitUplifts,
  grantLimitUplift,
  revokeLimitUplift,
  MEMBER_STATUSES,
  MEMBER_FILTER_WITH_UPLIFT,
  MEMBER_STATUS_LABELS,
  MEMBER_TYPES,
  restoreFamily,
  transferDependent,
  correctRelationship,
  changeFamilyPolicy,
  reorderFamily,
  previewEmployerTransfer,
  transferEmployerFamily
};
