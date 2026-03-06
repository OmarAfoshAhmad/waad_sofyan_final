import axiosClient from 'utils/axios';

/**
 * Provider Contracts API Service
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Provides CRUD operations and lifecycle management for Provider Contracts module.
 * Backend: ProviderContractController.java
 *
 * @version 1.0.0
 * @lastUpdated 2024-12-24
 */

const BASE_URL = '/provider-contracts';

/**
 * Helper function to unwrap ApiResponse
 * Backend returns: { status: "success", data: {...}, message: "...", timestamp: "..." }
 */
const unwrap = (response) => response.data?.data || response.data;

// ═══════════════════════════════════════════════════════════════════════════
// CONTRACT STATUS & PRICING MODEL ENUMS
// ═══════════════════════════════════════════════════════════════════════════

export const CONTRACT_STATUS = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  EXPIRED: 'EXPIRED',
  SUSPENDED: 'SUSPENDED',
  TERMINATED: 'TERMINATED'
};

export const CONTRACT_STATUS_CONFIG = {
  DRAFT: {
    label: 'مسودة',
    labelEn: 'Draft',
    color: 'default',
    description: 'العقد قيد الإعداد'
  },
  ACTIVE: {
    label: 'نشط',
    labelEn: 'Active',
    color: 'success',
    description: 'العقد ساري المفعول'
  },
  EXPIRED: {
    label: 'منتهي',
    labelEn: 'Expired',
    color: 'error',
    description: 'انتهت صلاحية العقد'
  },
  SUSPENDED: {
    label: 'موقوف',
    labelEn: 'Suspended',
    color: 'warning',
    description: 'العقد موقوف مؤقتاً'
  },
  TERMINATED: {
    label: 'ملغي',
    labelEn: 'Terminated',
    color: 'error',
    description: 'تم إنهاء العقد'
  }
};

export const PRICING_MODEL = {
  FIXED: 'FIXED',
  DISCOUNT: 'DISCOUNT',
  TIERED: 'TIERED',
  NEGOTIATED: 'NEGOTIATED'
};

export const PRICING_MODEL_CONFIG = {
  FIXED: { label: 'سعر ثابت', labelEn: 'Fixed Price' },
  DISCOUNT: { label: 'نسبة خصم', labelEn: 'Discount Rate' },
  TIERED: { label: 'تسعير متدرج', labelEn: 'Tiered Pricing' },
  NEGOTIATED: { label: 'سعر تفاوضي', labelEn: 'Negotiated' }
};

// ═══════════════════════════════════════════════════════════════════════════
// READ OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get paginated provider contracts list
 * Endpoint: GET /api/provider-contracts
 * @param {Object} params - Query parameters
 * @param {number} params.page - Page number (0-based, default: 0)
 * @param {number} params.size - Page size (default: 20)
 * @returns {Promise<Object>} Paginated response with content, totalElements, totalPages
 */
export const getProviderContracts = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  const data = unwrap(response);

  // Normalize backend response (items/total) to frontend format (content/totalElements)
  if (data && typeof data === 'object') {
    if (Array.isArray(data.items)) {
      return {
        content: data.items,
        totalElements: data.total || data.items.length,
        page: data.page,
        size: data.size
      };
    }
    if (Array.isArray(data.content)) {
      return data;
    }
    if (Array.isArray(data)) {
      return {
        content: data,
        totalElements: data.length
      };
    }
  }

  return data;
};

/**
 * Search provider contracts
 * Endpoint: GET /api/provider-contracts/search
 * @param {Object} params - Query parameters
 * @param {string} params.q - Search query
 * @param {string} params.status - Filter by status
 * @returns {Promise<Object>} Paginated search results
 */
export const searchProviderContracts = async (params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/search`, { params });
  return unwrap(response);
};

/**
 * Get contract statistics
 * Endpoint: GET /api/provider-contracts/stats
 * @returns {Promise<Object>} Statistics summary
 */
export const getProviderContractStats = async () => {
  const response = await axiosClient.get(`${BASE_URL}/stats`);
  return unwrap(response);
};

/**
 * Get contracts expiring within N days
 * Endpoint: GET /api/provider-contracts/expiring
 * @param {number} days - Days until expiration (default: 30)
 * @returns {Promise<Array>} List of expiring contracts
 */
export const getExpiringContracts = async (days = 30) => {
  const response = await axiosClient.get(`${BASE_URL}/expiring`, { params: { days } });
  return unwrap(response);
};

/**
 * Get contracts by status
 * Endpoint: GET /api/provider-contracts/status/{status}
 * @param {string} status - Contract status
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated contracts
 */
export const getContractsByStatus = async (status, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/status/${status}`, { params });
  return unwrap(response);
};

/**
 * Get provider contract by ID
 * Endpoint: GET /api/provider-contracts/{id}
 * @param {number} id - Contract ID
 * @returns {Promise<Object>} Contract details
 */
export const getProviderContractById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Get contract by code
 * Endpoint: GET /api/provider-contracts/code/{code}
 * @param {string} code - Contract code
 * @returns {Promise<Object>} Contract details
 */
export const getProviderContractByCode = async (code) => {
  const response = await axiosClient.get(`${BASE_URL}/code/${code}`);
  return unwrap(response);
};

/**
 * Get contracts for a provider
 * Endpoint: GET /api/provider-contracts/provider/{providerId}
 * @param {number} providerId - Provider ID
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated contracts
 */
export const getContractsByProvider = async (providerId, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/provider/${providerId}`, { params });
  return unwrap(response);
};

/**
 * Get active contract for a provider
 * Endpoint: GET /api/provider-contracts/provider/{providerId}/active
 * @param {number} providerId - Provider ID
 * @returns {Promise<Object>} Active contract or null
 */
export const getActiveContractByProvider = async (providerId) => {
  const response = await axiosClient.get(`${BASE_URL}/provider/${providerId}/active`);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// CREATE/UPDATE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create new provider contract
 * Endpoint: POST /api/provider-contracts
 * @param {Object} data - Contract data
 * @returns {Promise<Object>} Created contract
 */
export const createProviderContract = async (data) => {
  const response = await axiosClient.post(BASE_URL, data);
  return unwrap(response);
};

/**
 * Update provider contract
 * Endpoint: PUT /api/provider-contracts/{id}
 * @param {number} id - Contract ID
 * @param {Object} data - Updated contract data
 * @returns {Promise<Object>} Updated contract
 */
export const updateProviderContract = async (id, data) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, data);
  return unwrap(response);
};

/**
 * Delete provider contract (soft delete)
 * Endpoint: DELETE /api/provider-contracts/{id}
 * @param {number} id - Contract ID
 * @returns {Promise<void>}
 */
export const deleteProviderContract = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// LIFECYCLE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Activate a contract
 * Endpoint: POST /api/provider-contracts/{id}/activate
 * @param {number} id - Contract ID
 * @returns {Promise<Object>} Updated contract
 */
export const activateContract = async (id) => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/activate`);
  return unwrap(response);
};

/**
 * Suspend a contract
 * Endpoint: POST /api/provider-contracts/{id}/suspend
 * @param {number} id - Contract ID
 * @param {string} reason - Suspension reason (optional)
 * @returns {Promise<Object>} Updated contract
 */
export const suspendContract = async (id, reason = '') => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/suspend`, null, {
    params: { reason }
  });
  return unwrap(response);
};

/**
 * Terminate a contract
 * Endpoint: POST /api/provider-contracts/{id}/terminate
 * @param {number} id - Contract ID
 * @param {string} reason - Termination reason (optional)
 * @returns {Promise<Object>} Updated contract
 */
export const terminateContract = async (id, reason = '') => {
  const response = await axiosClient.post(`${BASE_URL}/${id}/terminate`, null, {
    params: { reason }
  });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// PRICING ITEMS OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get pricing items for a contract
 * Endpoint: GET /api/provider-contracts/{contractId}/pricing
 * @param {number} contractId - Contract ID
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Paginated pricing items
 */
export const getContractPricingItems = async (contractId, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/${contractId}/pricing`, { params });
  return unwrap(response);
};

/**
 * Search pricing items within a contract
 * Endpoint: GET /api/provider-contracts/{contractId}/pricing/search
 * @param {number} contractId - Contract ID
 * @param {string} query - Search query
 * @param {Object} params - Pagination parameters
 * @returns {Promise<Object>} Search results
 */
export const searchContractPricingItems = async (contractId, query, params = {}) => {
  const response = await axiosClient.get(`${BASE_URL}/${contractId}/pricing/search`, {
    params: { q: query, ...params }
  });
  return unwrap(response);
};

/**
 * Get pricing statistics for a contract
 * Endpoint: GET /api/provider-contracts/{contractId}/pricing/stats
 * @param {number} contractId - Contract ID
 * @returns {Promise<Object>} Pricing statistics
 */
export const getContractPricingStats = async (contractId) => {
  const response = await axiosClient.get(`${BASE_URL}/${contractId}/pricing/stats`);
  return unwrap(response);
};

/**
 * Get pricing item by ID
 * Endpoint: GET /api/provider-contracts/pricing/{pricingId}
 * @param {number} pricingId - Pricing item ID
 * @returns {Promise<Object>} Pricing item details
 */
export const getPricingItemById = async (pricingId) => {
  const response = await axiosClient.get(`${BASE_URL}/pricing/${pricingId}`);
  return unwrap(response);
};

/**
 * Add pricing item to contract
 * Endpoint: POST /api/provider-contracts/{contractId}/pricing
 * @param {number} contractId - Contract ID
 * @param {Object} data - Pricing item data
 * @returns {Promise<Object>} Created pricing item
 */
export const addPricingItem = async (contractId, data) => {
  const response = await axiosClient.post(`${BASE_URL}/${contractId}/pricing`, data);
  return unwrap(response);
};

/**
 * Bulk add pricing items to contract
 * Endpoint: POST /api/provider-contracts/{contractId}/pricing/bulk
 * @param {number} contractId - Contract ID
 * @param {Array} items - Array of pricing item data
 * @returns {Promise<Array>} Created pricing items
 */
export const addBulkPricingItems = async (contractId, items) => {
  const response = await axiosClient.post(`${BASE_URL}/${contractId}/pricing/bulk`, items);
  return unwrap(response);
};

/**
 * Update pricing item
 * Endpoint: PUT /api/provider-contracts/pricing/{pricingId}
 * @param {number} pricingId - Pricing item ID
 * @param {Object} data - Updated pricing data
 * @returns {Promise<Object>} Updated pricing item
 */
export const updatePricingItem = async (pricingId, data) => {
  const response = await axiosClient.put(`${BASE_URL}/pricing/${pricingId}`, data);
  return unwrap(response);
};

/**
 * Delete pricing item
 * Endpoint: DELETE /api/provider-contracts/pricing/{pricingId}
 * @param {number} pricingId - Pricing item ID
 * @returns {Promise<void>}
 */
export const deletePricingItem = async (pricingId) => {
  const response = await axiosClient.delete(`${BASE_URL}/pricing/${pricingId}`);
  return unwrap(response);
};

/**
 * Delete all pricing items for a contract (DRAFT only)
 * Endpoint: DELETE /api/provider-contracts/{contractId}/pricing
 * @param {number} contractId - Contract ID
 * @returns {Promise<number>} Number of deleted items
 */
export const deleteAllPricingItems = async (contractId) => {
  const response = await axiosClient.delete(`${BASE_URL}/${contractId}/pricing`);
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// EXCEL IMPORT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Excel template for pricing items import
 * Endpoint: GET /api/provider-contracts/{contractId}/pricing/import/template
 * @param {number} contractId - Contract ID
 * @returns {Promise<Blob>} Excel template file
 */
export const downloadPricingTemplate = async (contractId) => {
  try {
    const response = await axiosClient.get(`${BASE_URL}/${contractId}/pricing/import/template`, {
      responseType: 'blob'
    });

    // Check if response is actually an error (JSON instead of blob)
    const contentType = response.headers?.['content-type'] || '';
    if (contentType.includes('application/json')) {
      // Error response - parse as JSON
      const text = await response.data.text();
      const errorData = JSON.parse(text);
      throw new Error(errorData.message || errorData.messageAr || 'فشل تحميل القالب');
    }

    // Create download link
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `Price_List_Contract_${contractId}.xlsx`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);

    return response.data;
  } catch (error) {
    console.error('[downloadPricingTemplate] Error:', error);
    // Handle blob error response
    if (error.response?.data instanceof Blob) {
      try {
        const text = await error.response.data.text();
        const errorData = JSON.parse(text);
        throw new Error(errorData.message || errorData.messageAr || 'فشل تحميل القالب');
      } catch (parseError) {
        // Couldn't parse error, throw generic message
        throw new Error('فشل تحميل القالب - العقد غير موجود أو غير نشط');
      }
    }
    // Re-throw with better message
    throw new Error(error.response?.data?.message || error.message || 'فشل تحميل القالب');
  }
};

/**
 * Upload Excel file to import pricing items for a contract
 * Endpoint: POST /api/provider-contracts/{contractId}/pricing/import
 * @param {number} contractId - Contract ID
 * @param {File} file - Excel file (.xlsx or .xls)
 * @returns {Promise<Object>} Import result with statistics
 */
export const uploadContractPricingExcel = async (contractId, file) => {
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await axiosClient.post(`${BASE_URL}/${contractId}/pricing/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 300000 // 5 minutes for large Excel files
    });

    return unwrap(response);
  } catch (error) {
    // Check if it's a 400 with actual success data
    if (error.response?.status === 400 && error.response?.data?.status === 'success') {
      // Backend returned 400 but operation was partially successful
      return unwrap(error.response);
    }
    throw error;
  }
};

// ═══════════════════════════════════════════════════════════════════════════
// PROVIDER PORTAL SELF-ACCESS ENDPOINTS (NEW 2026-01-17)
// These endpoints allow PROVIDER role users to access their own contract/pricing
// without requiring VIEW_PROVIDER_CONTRACTS permission
// ═══════════════════════════════════════════════════════════════════════════

const PROVIDER_PORTAL_URL = '/provider';

/**
 * Get the active contract for the current PROVIDER user
 * Endpoint: GET /api/provider/my-contract
 *
 * SECURITY: Provider can only access their own contract
 *
 * @returns {Promise<Object>} Active contract details or null
 */
export const getMyActiveContract = async () => {
  const response = await axiosClient.get(`${PROVIDER_PORTAL_URL}/my-contract`);
  return unwrap(response);
};

/**
 * Get all services with pricing for the current PROVIDER's active contract
 * Endpoint: GET /api/provider/my-contract/services
 *
 * SECURITY: Provider can only access their own contract services
 *
 * @param {Object} params - Pagination parameters
 * @param {number} params.page - Page number (0-based)
 * @param {number} params.size - Page size
 * @returns {Promise<Object>} Paginated services with contract prices
 */
export const getMyContractServices = async (params = {}) => {
  const response = await axiosClient.get(`${PROVIDER_PORTAL_URL}/my-contract/services`, { params });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// UNIFIED HELPERS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Get all contracted services for a specific provider.
 * Logic: Get active contract -> Get its pricing items.
 */
export const getAllContractedServices = async (providerId) => {
  try {
    const activeContract = await getActiveContractByProvider(providerId);
    if (!activeContract) return [];

    // Get all pricing items (setting a large size to get all at once for dropdowns)
    const response = await getContractPricingItems(activeContract.id, { size: 1000 });
    return response?.items || response?.content || (Array.isArray(response) ? response : []);
  } catch (error) {
    console.error('Error fetching contracted services:', error);
    return [];
  }
};

const providerContractsService = {
  getProviderContracts,
  searchProviderContracts,
  getProviderContractStats,
  getExpiringContracts,
  getContractsByStatus,
  getProviderContractById,
  getProviderContractByCode,
  getContractsByProvider,
  getActiveContractByProvider,
  createProviderContract,
  updateProviderContract,
  deleteProviderContract,
  activateContract,
  suspendContract,
  terminateContract,
  getContractPricingItems,
  searchContractPricingItems,
  getContractPricingStats,
  getPricingItemById,
  addPricingItem,
  addBulkPricingItems,
  updatePricingItem,
  deletePricingItem,
  deleteAllPricingItems,
  downloadPricingTemplate,
  uploadContractPricingExcel,
  getMyActiveContract,
  getMyContractServices,
  getAllContractedServices
};

export default providerContractsService;
