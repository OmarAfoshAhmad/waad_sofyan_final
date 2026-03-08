import axiosClient from 'utils/axios';

/**
 * Benefit Policies API Service
 * Provides CRUD operations and lifecycle management for Benefit Policies module
 * Backend: BenefitPolicyController.java
 */

const BASE_URL = '/benefit-policies';

/**
 * Helper function to unwrap ApiResponse
 * Backend returns: { status: "success", data: {...}, message: "...", timestamp: "..." }
 */
const unwrap = (response) => response.data?.data || response.data;

// ═══════════════════════════════════════════════════════════════════════════
// READ OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get paginated benefit policies list
 * Endpoint: GET /api/benefit-policies
 * @param {Object} params - Query parameters
 * @param {number} params.page - Page number (0-based, default: 0)
 * @param {number} params.size - Page size (default: 20)
 * @param {string} params.sortBy - Sort field (default: 'createdAt')
 * @param {string} params.sortDir - Sort direction: 'asc' or 'desc' (default: 'desc')
 * @returns {Promise<Object>} Paginated response with content, totalElements, totalPages
 */
export const getBenefitPolicies = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return unwrap(response);
};

/**
 * Get benefit policy by ID
 * Endpoint: GET /api/benefit-policies/{id}
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Policy details
 */
export const getBenefitPolicyById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get benefit policy by code
 * Endpoint: GET /api/benefit-policies/code/{code}
 * @param {string} code - Policy code
 * @returns {Promise<Object>} Policy details
 */
export const getBenefitPolicyByCode = async (code) => {
  const response = await axiosClient.get(`${BASE_URL}/code/${code}`);
  return unwrap(response);
};

/**
 * Get benefit policies for an employer
 * Endpoint: GET /api/benefit-policies/employer/{employerOrgId}
 * @param {number} employerOrgId - Employer organization ID
 * @returns {Promise<Array>} List of policies
 */
export const getBenefitPoliciesByEmployer = async (employerOrgId) => {
  const response = await axiosClient.get(`${BASE_URL}/employer/${employerOrgId}`);
  return unwrap(response);
};

/**
 * Get benefit policies for an employer (paginated)
 * Endpoint: GET /api/benefit-policies/employer/{employerOrgId}/paged
 * @param {number} employerOrgId - Employer organization ID
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getBenefitPoliciesByEmployerPaged = async (employerOrgId, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/employer/${employerOrgId}/paged`, { params });
  return unwrap(response);
};

/**
 * Get benefit policies by status
 * Endpoint: GET /api/benefit-policies/status/{status}
 * @param {string} status - Policy status (DRAFT, ACTIVE, SUSPENDED, EXPIRED, CANCELLED)
 * @returns {Promise<Array>} List of policies
 */
export const getBenefitPoliciesByStatus = async (status) => {
  const response = await axiosClient.get(`${BASE_URL}/status/${status}`);
  return unwrap(response);
};

/**
 * Get effective policy for employer on a date
 * Endpoint: GET /api/benefit-policies/effective
 * @param {number} employerOrgId - Employer organization ID
 * @param {string} date - Date in ISO format (optional, defaults to today)
 * @returns {Promise<Object>} Effective policy or null
 */
export const getEffectiveBenefitPolicy = async (employerOrgId, date = null) => {
  const params = { employerOrgId };
  if (date) params.date = date;
  const response = await axiosClient.get(`${BASE_URL}/effective`, { params });
  return unwrap(response);
};

/**
 * Search benefit policies
 * Endpoint: GET /api/benefit-policies/search
 * @param {string} query - Search query
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated search results
 */
export const searchBenefitPolicies = async (query, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, { params: { q: query, ...params } });
  return unwrap(response);
};

/**
 * Get selector list for dropdowns
 * Endpoint: GET /api/benefit-policies/selector
 * @returns {Promise<Array>} Selector options
 */
export const getBenefitPoliciesSelector = async () => {
  const response = await axiosClient.get(`${BASE_URL}/selector`);
  return unwrap(response);
};

/**
 * Get selector list for an employer
 * Endpoint: GET /api/benefit-policies/selector/employer/{employerOrgId}
 * @param {number} employerOrgId - Employer organization ID
 * @returns {Promise<Array>} Selector options
 */
export const getBenefitPoliciesSelectorByEmployer = async (employerOrgId) => {
  const response = await axiosClient.get(`${BASE_URL}/selector/employer/${employerOrgId}`);
  return unwrap(response);
};

/**
 * Get policies expiring soon
 * Endpoint: GET /api/benefit-policies/expiring
 * @param {number} days - Number of days to check (default: 30)
 * @returns {Promise<Array>} List of expiring policies
 */
export const getExpiringBenefitPolicies = async (days = 30) => {
  const response = await axiosClient.get(`${BASE_URL}/expiring`, { params: { days } });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// CREATE & UPDATE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create new benefit policy
 * Endpoint: POST /api/benefit-policies
 * @param {Object} payload - Policy data
 * @returns {Promise<Object>} Created policy
 */
export const createBenefitPolicy = async (payload) => {
  const response = await axiosClient.post(BASE_URL, payload);
  return unwrap(response);
};

/**
 * Update existing benefit policy
 * Endpoint: PUT /api/benefit-policies/{id}
 * @param {number} id - Policy ID
 * @param {Object} payload - Updated policy data
 * @returns {Promise<Object>} Updated policy
 */
export const updateBenefitPolicy = async (id, payload) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// LIFECYCLE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Activate a benefit policy
 * Endpoint: POST /api/benefit-policies/{id}/activate
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Activated policy
 */
export const activateBenefitPolicy = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/activate`);
  return unwrap(response);
};

/**
 * Deactivate (expire) a benefit policy
 * Endpoint: POST /api/benefit-policies/{id}/deactivate
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Deactivated policy
 */
export const deactivateBenefitPolicy = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/deactivate`);
  return unwrap(response);
};

/**
 * Suspend a benefit policy temporarily
 * Endpoint: POST /api/benefit-policies/{id}/suspend
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Suspended policy
 */
export const suspendBenefitPolicy = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/suspend`);
  return unwrap(response);
};

/**
 * Cancel a benefit policy
 * Endpoint: POST /api/benefit-policies/{id}/cancel
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Cancelled policy
 */
export const cancelBenefitPolicy = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/cancel`);
  return unwrap(response);
};

/**
 * Soft delete a benefit policy
 * Endpoint: DELETE /api/benefit-policies/{id}
 * @param {number} id - Policy ID
 * @returns {Promise<void>}
 */
export const deleteBenefitPolicy = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get soft-deleted (archived) benefit policies
 * Endpoint: GET /api/benefit-policies/deleted
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getDeletedBenefitPolicies = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/deleted`, { params });
  return unwrap(response);
};

/**
 * Restore a soft-deleted benefit policy
 * Endpoint: POST /api/benefit-policies/{id}/restore
 * @param {number} id - Policy ID
 * @returns {Promise<Object>} Restored policy
 */
export const restoreBenefitPolicy = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/restore`);
  return unwrap(response);
};

/**
 * Permanently delete a soft-deleted benefit policy (hard delete)
 * Endpoint: DELETE /api/benefit-policies/{id}/permanent
 * @param {number} id - Policy ID
 */
export const permanentDeleteBenefitPolicy = async (id) => {
  await axiosClient.delete(`${BASE_URL}/${id}/permanent`);
};

// ═══════════════════════════════════════════════════════════════════════════
// ADMIN OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Auto-expire policies past their end date
 * Endpoint: POST /api/benefit-policies/maintenance/expire-old
 * @returns {Promise<number>} Number of expired policies
 */
export const expireOldPolicies = async () => {
  const response = await axiosClient.post(`${BASE_URL}/maintenance/expire-old`);
  return unwrap(response);
};

// Default export for convenience
export default {
  // Read operations
  getBenefitPolicies,
  getBenefitPolicyById,
  getBenefitPolicyByCode,
  getBenefitPoliciesByEmployer,
  getBenefitPoliciesByEmployerPaged,
  getBenefitPoliciesByStatus,
  getEffectiveBenefitPolicy,
  searchBenefitPolicies,
  getBenefitPoliciesSelector,
  getBenefitPoliciesSelectorByEmployer,
  getExpiringBenefitPolicies,
  // Create & Update
  createBenefitPolicy,
  updateBenefitPolicy,
  // Lifecycle
  activateBenefitPolicy,
  deactivateBenefitPolicy,
  suspendBenefitPolicy,
  cancelBenefitPolicy,
  deleteBenefitPolicy,
  permanentDeleteBenefitPolicy,
  // Admin
  expireOldPolicies
};
