import axiosClient from 'utils/axios';

/**
 * Benefit Policy Rules API Service
 * Provides CRUD operations for Benefit Policy Rules
 * Backend: BenefitPolicyRuleController.java
 *
 * All endpoints are nested under: /api/benefit-policies/{policyId}/rules
 */

/**
 * Helper function to unwrap ApiResponse
 * Backend returns: { status: "success", data: {...}, message: "...", timestamp: "..." }
 */
const unwrap = (response) => response.data?.data || response.data;

// ═══════════════════════════════════════════════════════════════════════════
// READ OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get all rules for a policy
 * Endpoint: GET /api/benefit-policies/{policyId}/rules
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} List of rules
 */
export const getPolicyRules = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules`);
  return unwrap(response);
};

/**
 * Get paginated rules for a policy
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/paged
 * @param {number} policyId - Policy ID
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getPolicyRulesPaged = async (policyId, params = {}) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/paged`, { params });
  return unwrap(response);
};

/**
 * Get only active rules for a policy
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/active
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} List of active rules
 */
export const getActivePolicyRules = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/active`);
  return unwrap(response);
};

/**
 * Get a specific rule by ID
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/{ruleId}
 * @param {number} policyId - Policy ID
 * @param {number} ruleId - Rule ID
 * @returns {Promise<Object>} Rule details
 */
export const getPolicyRuleById = async (policyId, ruleId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/${ruleId}`);
  return unwrap(response);
};

/**
 * Get category-level rules
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/category
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} List of category rules
 */
export const getCategoryRules = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/category`);
  return unwrap(response);
};

/**
 * Get service-level rules
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/service
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} List of service rules
 */
export const getServiceRules = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/service`);
  return unwrap(response);
};

/**
 * Get rules requiring pre-approval
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/pre-approval
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} List of pre-approval rules
 */
export const getPreApprovalRules = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/pre-approval`);
  return unwrap(response);
};

/**
 * Get rule count statistics
 * Endpoint: GET /api/benefit-policies/{policyId}/rules/count
 * @param {number} policyId - Policy ID
 * @returns {Promise<Object>} { total, active, inactive }
 */
export const getPolicyRulesCount = async (policyId) => {
  const response = await axiosClient.get(`/benefit-policies/${policyId}/rules/count`);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// COVERAGE LOOKUP
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get coverage rule for a specific service
 * Endpoint: GET /api/benefit-policies/{policyId}/coverage/service/{serviceId}
 * @param {number} policyId - Policy ID
 * @param {number} serviceId - Service ID
 * @returns {Promise<Object|null>} Coverage rule or null
 */
export const getCoverageForService = async (policyId, serviceId, categoryId = null) => {
  const params = {};
  if (categoryId) params.categoryId = categoryId;
  const response = await axiosClient.get(`/benefit-policies/${policyId}/coverage/service/${serviceId}`, { params });
  return unwrap(response);
};

/**
 * Quick check if service is covered
 * Endpoint: GET /api/benefit-policies/{policyId}/coverage/service/{serviceId}/check
 * @param {number} policyId - Policy ID
 * @param {number} serviceId - Service ID
 * @returns {Promise<Object>} { covered, coveragePercent, requiresPreApproval }
 */
export const checkServiceCoverage = async (policyId, serviceId, categoryId = null) => {
  const params = {};
  if (categoryId) params.categoryId = categoryId;
  const response = await axiosClient.get(`/benefit-policies/${policyId}/coverage/service/${serviceId}/check`, { params });
  return unwrap(response);
};

/**
 * Check service usage against limits
 * Endpoint: GET /api/benefit-policies/{policyId}/coverage/service/{serviceId}/usage
 * @param {number} policyId - Policy ID
 * @param {number} serviceId - Service ID
 * @param {number} memberId - Member ID
 * @param {number} year - Optional year
 * @param {number} categoryId - Optional category ID
 * @param {number} excludeClaimId - Optional claim ID to exclude from usage calculation
 * @returns {Promise<Object>} Usage details
 */
export const checkServiceUsageLimit = async (policyId, serviceId, memberId, categoryId = null, year = null, excludeClaimId = null) => {
  const params = { memberId };
  if (categoryId) params.categoryId = categoryId;
  if (year) params.year = year;
  if (excludeClaimId) params.excludeClaimId = excludeClaimId;
  const response = await axiosClient.get(`/benefit-policies/${policyId}/coverage/service/${serviceId}/usage`, { params });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// CREATE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create a new rule for the policy
 * Endpoint: POST /api/benefit-policies/{policyId}/rules
 * @param {number} policyId - Policy ID
 * @param {Object} payload - Rule data
 * @param {number} payload.medicalCategoryId - Category ID (XOR with serviceId)
 * @param {number} payload.medicalServiceId - Service ID (XOR with categoryId)
 * @param {number} payload.coveragePercent - Coverage % (0-100)
 * @param {number} payload.amountLimit - Amount limit
 * @param {number} payload.timesLimit - Times limit
 * @param {number} payload.waitingPeriodDays - Waiting period in days
 * @param {boolean} payload.requiresPreApproval - Requires pre-approval
 * @param {string} payload.notes - Notes
 * @returns {Promise<Object>} Created rule
 */
export const createPolicyRule = async (policyId, payload) => {
  const response = await axiosClient.post(`/benefit-policies/${policyId}/rules`, payload);
  return unwrap(response);
};

/**
 * Bulk create rules
 * Endpoint: POST /api/benefit-policies/{policyId}/rules/bulk
 * @param {number} policyId - Policy ID
 * @param {Array} rules - Array of rule data
 * @returns {Promise<Array>} Created rules
 */
export const createPolicyRulesBulk = async (policyId, rules) => {
  const response = await axiosClient.post(`/benefit-policies/${policyId}/rules/bulk`, rules);
  return unwrap(response);
};

/**
 * Initialize standard professional rules
 * Endpoint: POST /api/benefit-policies/{policyId}/rules/initialize-standard
 * @param {number} policyId - Policy ID
 * @returns {Promise<Array>} Created rules
 */
export const initializeStandardRules = async (policyId) => {
  const response = await axiosClient.post(`/benefit-policies/${policyId}/rules/initialize-standard`);
  return unwrap(response);
};


// ═══════════════════════════════════════════════════════════════════════════
// UPDATE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Update an existing rule
 * Endpoint: PUT /api/benefit-policies/{policyId}/rules/{ruleId}
 * @param {number} policyId - Policy ID
 * @param {number} ruleId - Rule ID
 * @param {Object} payload - Updated rule data
 * @returns {Promise<Object>} Updated rule
 */
export const updatePolicyRule = async (policyId, ruleId, payload) => {
  const response = await axiosClient.put(`/benefit-policies/${policyId}/rules/${ruleId}`, payload);
  return unwrap(response);
};

/**
 * Toggle rule active status
 * Endpoint: POST /api/benefit-policies/{policyId}/rules/{ruleId}/toggle
 * @param {number} policyId - Policy ID
 * @param {number} ruleId - Rule ID
 * @returns {Promise<Object>} Updated rule
 */
export const togglePolicyRuleActive = async (policyId, ruleId) => {
  const response = await axiosClient.post(`/benefit-policies/${policyId}/rules/${ruleId}/toggle`);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// DELETE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Soft delete a rule (deactivate)
 * Endpoint: DELETE /api/benefit-policies/{policyId}/rules/{ruleId}
 * @param {number} policyId - Policy ID
 * @param {number} ruleId - Rule ID
 * @returns {Promise<void>}
 */
export const deletePolicyRule = async (policyId, ruleId) => {
  const response = await axiosClient.delete(`/benefit-policies/${policyId}/rules/${ruleId}`);
  return unwrap(response);
};

/**
 * Permanently delete a rule
 * Endpoint: DELETE /api/benefit-policies/{policyId}/rules/{ruleId}/hard
 * @param {number} policyId - Policy ID
 * @param {number} ruleId - Rule ID
 * @returns {Promise<void>}
 */
export const hardDeletePolicyRule = async (policyId, ruleId) => {
  const response = await axiosClient.delete(`/benefit-policies/${policyId}/rules/${ruleId}/hard`);
  return unwrap(response);
};

/**
 * Delete all rules for a policy
 * Endpoint: DELETE /api/benefit-policies/{policyId}/rules
 * @param {number} policyId - Policy ID
 * @returns {Promise<void>}
 */
export const deleteAllPolicyRules = async (policyId) => {
  const response = await axiosClient.delete(`/benefit-policies/${policyId}/rules`);
  return unwrap(response);
};

/**
 * Deactivate all rules for a policy
 * Endpoint: POST /api/benefit-policies/{policyId}/rules/deactivate-all
 * @param {number} policyId - Policy ID
 * @returns {Promise<number>} Number of deactivated rules
 */
export const deactivateAllPolicyRules = async (policyId) => {
  const response = await axiosClient.post(`/benefit-policies/${policyId}/rules/deactivate-all`);
  return unwrap(response);
};

// Default export
export default {
  // Read
  getPolicyRules,
  getPolicyRulesPaged,
  getActivePolicyRules,
  getPolicyRuleById,
  getCategoryRules,
  getServiceRules,
  getPreApprovalRules,
  getPolicyRulesCount,
  // Coverage lookup
  getCoverageForService,
  checkServiceCoverage,
  checkServiceUsageLimit,
  // Create
  createPolicyRule,
  createPolicyRulesBulk,
  initializeStandardRules,
  // Update

  updatePolicyRule,
  togglePolicyRuleActive,
  // Delete
  deletePolicyRule,
  hardDeletePolicyRule,
  deleteAllPolicyRules,
  deactivateAllPolicyRules
};
