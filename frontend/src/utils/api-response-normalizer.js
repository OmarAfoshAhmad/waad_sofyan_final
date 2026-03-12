/**
 * API Response Normalizer
 *
 * Handles multiple backend response formats and normalizes them
 * into a consistent format for frontend consumption.
 *
 * Backend Response Variations:
 * 1. ApiResponse wrapper: { status: "success", data: {...}, timestamp: "..." }
 * 2. Spring Page: { content: [...], totalElements: 10, number: 0, size: 10 }
 * 3. Plain array: [{...}, {...}]
 * 4. Paginated response: { items: [...], total: 10, page: 1, size: 10 }
 */

/**
 * Extract items array from various response formats
 * @param {*} data - Response data (can be wrapped or raw)
 * @returns {Array} - Normalized items array
 */
export const extractItems = (data) => {
  if (!data) return [];

  // Handle ApiResponse wrapper with items
  if (Array.isArray(data?.data?.items)) return data.data.items;

  // Handle settlement batches response shape
  if (Array.isArray(data?.data?.batches)) return data.data.batches;
  if (Array.isArray(data?.batches)) return data.batches;

  // Handle direct items property
  if (Array.isArray(data?.items)) return data.items;

  // Handle Spring Page format (content array)
  if (Array.isArray(data?.content)) return data.content;
  if (Array.isArray(data?.data?.content)) return data.data.content;

  // Handle plain array response
  if (Array.isArray(data)) return data;

  // Handle ApiResponse wrapper with direct array
  if (Array.isArray(data?.data)) return data.data;

  return [];
};

/**
 * Extract total count from various response formats
 * @param {*} data - Response data
 * @returns {number} - Total count
 */
export const extractTotal = (data) => {
  if (!data) return 0;

  // Handle ApiResponse wrapper with total
  if (typeof data?.data?.total === 'number') return data.data.total;

  // Handle direct total property
  if (typeof data?.total === 'number') return data.total;

  // Handle Spring Page format (totalElements)
  if (typeof data?.totalElements === 'number') return data.totalElements;
  if (typeof data?.data?.totalElements === 'number') return data.data.totalElements;

  // Fallback: count items
  return extractItems(data).length;
};

/**
 * Extract page number from various response formats
 * @param {*} data - Response data
 * @param {number} defaultPage - Default page if not found (default: 1)
 * @returns {number} - Current page number
 */
export const extractPage = (data, defaultPage = 1) => {
  if (!data) return defaultPage;

  // Handle ApiResponse wrapper with page
  if (typeof data?.data?.page === 'number') return data.data.page;

  // Handle direct page property
  if (typeof data?.page === 'number') return data.page;

  // Handle settlement batches response shape (0-based)
  if (typeof data?.currentPage === 'number') return data.currentPage + 1;
  if (typeof data?.data?.currentPage === 'number') return data.data.currentPage + 1;

  // Handle Spring Page format (number is 0-based, convert to 1-based)
  if (typeof data?.number === 'number') return data.number + 1;
  if (typeof data?.data?.number === 'number') return data.data.number + 1;

  return defaultPage;
};

/**
 * Extract page size from various response formats
 * @param {*} data - Response data
 * @param {number} defaultSize - Default size if not found (default: 10)
 * @returns {number} - Page size
 */
export const extractSize = (data, defaultSize = 10) => {
  if (!data) return defaultSize;

  // Handle ApiResponse wrapper with size
  if (typeof data?.data?.size === 'number') return data.data.size;

  // Handle direct size property
  if (typeof data?.size === 'number') return data.size;

  // Handle settlement batches response shape
  if (typeof data?.pageSize === 'number') return data.pageSize;
  if (typeof data?.data?.pageSize === 'number') return data.data.pageSize;

  // Fallback: count items
  const items = extractItems(data);
  return items.length > 0 ? items.length : defaultSize;
};

/**
 * Normalize any backend response into consistent pagination format
 * @param {*} response - Raw backend response
 * @returns {Object} - Normalized pagination response { items, total, page, size }
 */
export const normalizePaginatedResponse = (response) => {
  // Unwrap ApiResponse if present
  const data = response?.data?.data || response?.data || response;

  return {
    items: extractItems(data),
    total: extractTotal(data),
    page: extractPage(data),
    size: extractSize(data)
  };
};

/**
 * Unwrap ApiResponse wrapper and return data
 * @param {*} response - Raw backend response
 * @returns {*} - Unwrapped data
 */
export const unwrapApiResponse = (response) => {
  return response?.data?.data || response?.data || response;
};

/**
 * Safely extract error message from various error formats
 * @param {*} error - Error object
 * @returns {string} - User-friendly error message
 */
export const extractErrorMessage = (error) => {
  // Axios error with response — prefer Arabic message (messageAr) first
  if (error?.response?.data?.messageAr) return error.response.data.messageAr;
  if (error?.response?.data?.message) return error.response.data.message;

  // Direct error message
  if (error?.message) return error.message;

  // Fallback
  return error?.response?.statusText || 'تعذر الاتصال بالخادم، يرجى المحاولة لاحقاً';
};

/**
 * Check if response indicates success
 * @param {*} response - Backend response
 * @returns {boolean} - True if successful
 */
export const isSuccessResponse = (response) => {
  const data = response?.data || response;

  // Check ApiResponse status field
  if (data?.status === 'success') return true;

  // Check HTTP status code
  if (response?.status >= 200 && response?.status < 300) return true;

  return false;
};

/**
 * Defensive array check with normalization
 * @param {*} value - Value to check
 * @returns {Array} - Normalized array (empty if invalid)
 */
export const normalizeArray = (value) => {
  if (Array.isArray(value)) return value;
  if (!value) return [];

  // Try to extract array from object
  const items = extractItems(value);
  return Array.isArray(items) ? items : [];
};

export default {
  extractItems,
  extractTotal,
  extractPage,
  extractSize,
  normalizePaginatedResponse,
  unwrapApiResponse,
  extractErrorMessage,
  isSuccessResponse,
  normalizeArray
};
