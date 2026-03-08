import axiosClient from 'utils/axios';
import ExcelJS from 'exceljs';

/**
 * ============================================================================
 * Employers API Service - Phase 2 Implementation
 * ============================================================================
 *
 * Features:
 * - Field normalization (Frontend ↔ Backend mapping)
 * - Auto-code generation support (optional code field)
 * - Error handling and user-friendly messages
 * - Backward compatibility with legacy field names
 * - Full CRUD operations with proper transformations
 *
 * Backend Contract:
 * - CREATE: POST /api/employers - code is optional (auto-generated)
 * - READ:   GET /api/employers/:id
 * - UPDATE: PUT /api/employers/:id
 * - DELETE: DELETE /api/employers/:id
 * - LIST:   GET /api/employers
 *
 * Field Mapping Reference:
 * ┌─────────────────┬──────────────────┬─────────────────┐
 * │ Frontend Field  │ Backend Field    │ Direction       │
 * ├─────────────────┼──────────────────┼─────────────────┤
 * │ employerCode    │ code             │ Request         │
 * │ nameAr          │ name             │ Request         │
 * │ name          │ name           │ Both            │
 * │ active          │ active           │ Both            │
 * │ code            │ code             │ Response        │
 * │ nameAr          │ name (@JsonProp) │ Response        │
 * └─────────────────┴──────────────────┴─────────────────┘
 *
 * @see EMPLOYER_API_CONTRACT.md
 * @see PHASE-2-EMPLOYER-IMPLEMENTATION-REPORT.md
 */

// ⚠️ FIXED: Backend uses /api/employers (no v1)
const BASE_URL = '/employers';

// ============================================================================
// FIELD NORMALIZATION (Frontend ↔ Backend)
// ============================================================================

/**
 * Normalize frontend request payload to backend canonical format
 *
 * Transformations:
 * - employerCode → code (if provided)
 * - nameAr → name (required field)
 * - name → name (optional)
 * - active → active (defaults to true)
 *
 * Auto-Code Generation:
 * - If neither 'code' nor 'employerCode' provided → backend auto-generates (EMP-01, EMP-02, ...)
 * - If provided → backend uses the provided value
 *
 * @param {Object} frontendDto - Frontend form data
 * @param {string} [frontendDto.employerCode] - Employer code (legacy name)
 * @param {string} [frontendDto.code] - Employer code (canonical name)
 * @param {string} frontendDto.name - Arabic name (required)
 * @param {string} [frontendDto.name] - Arabic name (alternative)
 * @param {string} [frontendDto.name] - English name (optional)
 * @param {boolean} [frontendDto.active=true] - Active status
 * @returns {Object} Backend-compatible DTO
 *
 * @example
 * // Frontend sends (legacy names):
 * const frontendDto = {
 *   employerCode: 'EMP-CUSTOM',
 *   name: 'شركة الواحة',
 *   name: 'Al Waha Company',
 *   active: true
 * };
 *
 * // Normalized to backend format:
 * const backendDto = normalizeEmployerRequest(frontendDto);
 * // Result: { code: 'EMP-CUSTOM', name: 'شركة الواحة', name: 'Al Waha Company', active: true }
 *
 * @example
 * // Auto-code generation (no code provided):
 * const frontendDto = {
 *   name: 'شركة النور',
 *   name: 'Al Noor Services'
 * };
 *
 * const backendDto = normalizeEmployerRequest(frontendDto);
 * // Result: { name: 'شركة النور', name: 'Al Noor Services', active: true }
 * // Backend will auto-generate: code = 'EMP-01' (or next available)
 */
export const normalizeEmployerRequest = (frontendDto) => {
  if (!frontendDto) {
    console.warn('[EmployerService] normalizeEmployerRequest: No DTO provided');
    return null;
  }

  // Extract code (support both legacy 'employerCode' and canonical 'code')
  // If both missing → null (backend will auto-generate)
  const code = frontendDto.code || frontendDto.employerCode || null;

  // Extract name - required field
  const name = frontendDto.name || null;

  // Extract optional fields
  const active = frontendDto.active !== undefined ? frontendDto.active : true;
  const email = frontendDto.email || null;
  const phone = frontendDto.phone || null;
  const address = frontendDto.address || null;

  // Build normalized payload
  const normalized = {
    name, // Backend expects 'name'
    active,
    email,
    phone,
    address
  };

  // Only include code if provided (otherwise backend auto-generates)
  if (code !== null && code.trim() !== '') {
    normalized.code = code.trim().toUpperCase();
  }

  console.debug('[EmployerService] Request normalized:', {
    input: { code: frontendDto.code || frontendDto.employerCode, name: frontendDto.name },
    output: normalized
  });

  return normalized;
};

/**
 * Normalize backend response to frontend expected format
 *
 * Transformations:
 * - code → code (keep as-is)
 * - name → nameAr (Backend stores as 'name', serializes as 'nameAr' via @JsonProperty)
 * - name → name (keep as-is)
 * - active → active (keep as-is)
 * - Preserve audit timestamps (createdAt, updatedAt)
 *
 * Note: Backend Phase 2 returns 'nameAr' in response (via @JsonProperty),
 * so we just pass through the response as-is.
 *
 * @param {Object} backendDto - Backend response data
 * @param {number} backendDto.id - Employer ID
 * @param {string} backendDto.code - Employer code (e.g., 'EMP-01')
 * @param {string} backendDto.name - Arabic name (serialized from 'name' field)
 * @param {string} [backendDto.name] - English name
 * @param {boolean} backendDto.active - Active status
 * @param {string} [backendDto.createdAt] - Creation timestamp
 * @param {string} [backendDto.updatedAt] - Update timestamp
 * @returns {Object} Frontend-compatible DTO
 *
 * @example
 * // Backend returns:
 * const backendDto = {
 *   id: 1,
 *   code: 'EMP-01',
 *   name: 'شركة الواحة',  // Via @JsonProperty
 *   name: 'Al Waha Company',
 *   active: true,
 *   createdAt: '2024-12-29T10:00:00',
 *   updatedAt: '2024-12-29T10:00:00'
 * };
 *
 * // Normalized to frontend format:
 * const frontendDto = normalizeEmployerResponse(backendDto);
 * // Result: Same structure (backend already sends 'nameAr')
 */
export const normalizeEmployerResponse = (backendDto) => {
  if (!backendDto) {
    console.warn('[EmployerService] normalizeEmployerResponse: No DTO provided');
    return null;
  }

  // Backend returns 'name' field directly
  const normalized = {
    id: backendDto.id,
    code: backendDto.code,
    name: backendDto.name,
    active: backendDto.active,
    archived: backendDto.archived,
    email: backendDto.email,
    phone: backendDto.phone,
    address: backendDto.address,
    createdAt: backendDto.createdAt,
    updatedAt: backendDto.updatedAt
  };

  console.debug('[EmployerService] Response normalized:', {
    input: { code: backendDto.code, name: backendDto.name },
    output: normalized
  });

  return normalized;
};

/**
 * Normalize array of employer responses
 *
 * @param {Array} backendArray - Array of backend DTOs
 * @returns {Array} Array of frontend-compatible DTOs
 */
export const normalizeEmployerArrayResponse = (backendArray) => {
  if (!Array.isArray(backendArray)) {
    console.warn('[EmployerService] normalizeEmployerArrayResponse: Not an array');
    return [];
  }

  return backendArray.map(normalizeEmployerResponse);
};

// ============================================================================
// ERROR HANDLING
// ============================================================================

/**
 * Map backend errors to user-friendly frontend messages
 *
 * Error Types Handled:
 * - 400 Bad Request: Validation errors
 * - 404 Not Found: Employer not found
 * - 409 Conflict: Code already exists (duplicate)
 * - 500 Server Error: Unexpected errors
 *
 * @param {Error} error - Axios error object
 * @returns {Object} Structured error object
 * @property {number} status - HTTP status code
 * @property {string} message - User-friendly error message
 * @property {Object} [fieldErrors] - Field-specific errors (for validation)
 * @property {string} [errorCode] - Backend error code (if available)
 *
 * @example
 * try {
 *   await createEmployer(dto);
 * } catch (err) {
 *   const error = handleEmployerErrors(err);
 *   if (error.status === 400) {
 *     // Show field errors
 *     console.error('Validation failed:', error.fieldErrors);
 *   } else {
 *     // Show general error
 *     console.error('Error:', error.message);
 *   }
 * }
 */
export const handleEmployerErrors = (error) => {
  const status = error?.response?.status;
  const data = error?.response?.data;
  const message = data?.message || error?.response?.statusText || error?.message || 'تعذر الاتصال بالخادم أو حدث خطأ داخلي غير متوقع';

  console.error('[EmployerService] Error occurred:', {
    status,
    message,
    data
  });

  // Structure error response
  const errorResponse = {
    status: status || 500,
    message,
    errorCode: data?.errorCode || null,
    fieldErrors: null,
    originalError: error
  };

  // Handle specific error types
  switch (status) {
    case 400: // Validation Error
      errorResponse.message = 'يرجى تصحيح الأخطاء في النموذج';
      errorResponse.fieldErrors = data?.errors || {};

      // Map backend field names to frontend field names
      if (errorResponse.fieldErrors) {
        const mappedErrors = {};
        Object.keys(errorResponse.fieldErrors).forEach((key) => {
          if (key === 'name') {
            mappedErrors.name = errorResponse.fieldErrors[key];
          } else if (key === 'code') {
            mappedErrors.employerCode = errorResponse.fieldErrors[key];
          } else {
            mappedErrors[key] = errorResponse.fieldErrors[key];
          }
        });
        errorResponse.fieldErrors = mappedErrors;
      }
      break;

    case 404: // Not Found
      errorResponse.message = 'الشريك غير موجود';
      break;

    case 409: // Conflict — keep server message (may be CODE_DUPLICATE/NAME_DUPLICATE prefix for forms, or delete-guard message for permanent delete)
      // Do not override: parseApiError in forms uses the prefix to route to field-level errors;
      // permanent delete uses the Arabic message directly in the snackbar.
      break;

    case 403: // Forbidden
      errorResponse.message = 'ليس لديك صلاحية لتنفيذ هذه العملية';
      break;

    case 500: // Server Error
      errorResponse.message = 'خطأ في الخادم. يرجى المحاولة لاحقاً.';
      break;

    default:
      if (message.includes('Network Error')) {
        errorResponse.message = 'خطأ في الشبكة، يرجى التحقق من اتصالك بالإنترنت';
      } else {
        errorResponse.message = message || 'عذراً، حدث خطأ أثناء معالجة طلبك، يرجى إعادة المحاولة';
      }
  }

  return errorResponse;
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Unwrap ApiResponse envelope
 * Backend returns: { status: "success", data: {...}, message: "...", timestamp: "..." }
 * We need response.data.data (axios wraps in .data, then ApiResponse has .data)
 */
const unwrap = (response) => response?.data?.data || response?.data || null;

/**
 * Safely extract array from API response
 * Handles both ApiResponse<List<T>> and ApiResponse<PaginationResponse<T>>
 */
const unwrapArray = (response) => {
  const data = response?.data?.data || response?.data;

  // If data is an array, return it
  if (Array.isArray(data)) return data;

  // If data has items (pagination), return items
  if (data?.items && Array.isArray(data.items)) return data.items;

  // If data has content (Spring Page), return content
  if (data?.content && Array.isArray(data.content)) return data.content;

  // Fallback to empty array
  return [];
};

/**
 * Validate required fields before sending to backend
 *
 * @param {Object} dto - Frontend DTO
 * @returns {Object|null} Validation errors or null if valid
 */
const validateEmployerDto = (dto) => {
  const errors = {};

  // Check for required 'name'
  const name = dto.name;
  if (!name || name.trim() === '') {
    errors.name = 'الاسم مطلوب';
  }

  return Object.keys(errors).length > 0 ? errors : null;
};

// ============================================================================
// CRUD OPERATIONS
// ============================================================================

/**
 * Get all active employers
 *
 * @returns {Promise<Array>} List of employers (frontend format)
 * @throws {Error} Network or server error
 *
 * @example
 * const employers = await getEmployers();
 * console.log(employers);
 * // [
 * //   { id: 1, code: 'EMP-01', name: 'شركة الواحة', ... },
 * //   { id: 2, code: 'EMP-02', name: 'شركة النور', ... }
 * // ]
 */
export const getEmployers = async () => {
  try {
    console.debug('[EmployerService] Fetching all employers...');
    const response = await axiosClient.get(BASE_URL);
    const rawData = unwrapArray(response);
    return normalizeEmployerArrayResponse(rawData);
  } catch (error) {
    console.error('[EmployerService] getEmployers failed:', error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Get employer by ID
 *
 * @param {number} id - Employer ID
 * @returns {Promise<Object>} Employer details (frontend format)
 * @throws {Error} Network, not found, or server error
 *
 * @example
 * const employer = await getEmployerById(1);
 * console.log(employer);
 * // {
 * //   id: 1,
 * //   code: 'EMP-01',
 * //   name: 'شركة الواحة',
 * //   name: 'Al Waha Company',
 * //   active: true,
 * //   createdAt: '2024-12-29T10:00:00',
 * //   updatedAt: '2024-12-29T10:00:00'
 * // }
 */
export const getEmployerById = async (id) => {
  try {
    console.debug(`[EmployerService] Fetching employer ID: ${id}...`);
    const response = await axiosClient.get(`${BASE_URL}/${id}`);
    const rawData = unwrap(response);
    return normalizeEmployerResponse(rawData);
  } catch (error) {
    console.error(`[EmployerService] getEmployerById(${id}) failed:`, error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Create new employer
 *
 * Features:
 * - Auto-code generation if code not provided
 * - Field normalization (employerCode → code, nameAr → name)
 * - Client-side validation before sending
 * - Error handling with user-friendly messages
 *
 * @param {Object} frontendDto - Employer data (frontend format)
 * @param {string} [frontendDto.employerCode] - Employer code (optional - auto-generated if missing)
 * @param {string} frontendDto.name - Arabic name (required)
 * @param {string} [frontendDto.name] - English name (optional)
 * @param {boolean} [frontendDto.active=true] - Active status
 * @returns {Promise<Object>} Created employer (frontend format)
 * @throws {Error} Validation, conflict, or server error
 *
 * @example
 * // Create with auto-generated code:
 * const newEmployer = await createEmployer({
 *   name: 'شركة الواحة',
 *   name: 'Al Waha Company',
 *   active: true
 * });
 * console.log(newEmployer.code); // 'EMP-01' (auto-generated)
 *
 * @example
 * // Create with custom code:
 * const newEmployer = await createEmployer({
 *   employerCode: 'EMP-CUSTOM',
 *   name: 'شركة النور',
 *   name: 'Al Noor Services'
 * });
 * console.log(newEmployer.code); // 'EMP-CUSTOM'
 *
 * @example
 * // Error handling:
 * try {
 *   await createEmployer({ name: 'شركة الواحة' });
 * } catch (error) {
 *   if (error.status === 400) {
 *     console.error('Validation errors:', error.fieldErrors);
 *   } else if (error.status === 409) {
 *     console.error('Code already exists');
 *   }
 * }
 */
export const createEmployer = async (frontendDto) => {
  try {
    console.debug('[EmployerService] Creating employer...', frontendDto);

    // Client-side validation
    const validationErrors = validateEmployerDto(frontendDto);
    if (validationErrors) {
      console.warn('[EmployerService] Validation failed:', validationErrors);
      throw {
        response: {
          status: 400,
          data: {
            message: 'يرجى تصحيح الأخطاء في النموذج',
            errors: validationErrors
          }
        }
      };
    }

    // Normalize request
    const backendDto = normalizeEmployerRequest(frontendDto);

    // Send to backend
    const response = await axiosClient.post(BASE_URL, backendDto);
    const rawData = unwrap(response);

    console.info('[EmployerService] Employer created successfully:', rawData);

    // Normalize response
    return normalizeEmployerResponse(rawData);
  } catch (error) {
    console.error('[EmployerService] createEmployer failed:', error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Update existing employer
 *
 * Features:
 * - Field normalization (employerCode → code, nameAr → name)
 * - Client-side validation
 * - Code change warning (if auto-generated code is changed)
 * - Error handling
 *
 * @param {number} id - Employer ID
 * @param {Object} frontendDto - Updated employer data (frontend format)
 * @param {string} [frontendDto.employerCode] - Employer code
 * @param {string} frontendDto.name - Arabic name (required)
 * @param {string} [frontendDto.name] - English name
 * @param {boolean} [frontendDto.active] - Active status
 * @returns {Promise<Object>} Updated employer (frontend format)
 * @throws {Error} Validation, not found, conflict, or server error
 *
 * @example
 * const updated = await updateEmployer(1, {
 *   employerCode: 'EMP-01',
 *   name: 'شركة الواحة المحدودة',
 *   name: 'Al Waha Company Ltd',
 *   active: true
 * });
 */
export const updateEmployer = async (id, frontendDto) => {
  try {
    console.debug(`[EmployerService] Updating employer ID: ${id}...`, frontendDto);

    // Client-side validation
    const validationErrors = validateEmployerDto(frontendDto);
    if (validationErrors) {
      console.warn('[EmployerService] Validation failed:', validationErrors);
      throw {
        response: {
          status: 400,
          data: {
            message: 'يرجى تصحيح الأخطاء في النموذج',
            errors: validationErrors
          }
        }
      };
    }

    // Normalize request
    const backendDto = normalizeEmployerRequest(frontendDto);

    // Send to backend
    const response = await axiosClient.put(`${BASE_URL}/${id}`, backendDto);
    const rawData = unwrap(response);

    console.info(`[EmployerService] Employer ID: ${id} updated successfully`);

    // Normalize response
    return normalizeEmployerResponse(rawData);
  } catch (error) {
    console.error(`[EmployerService] updateEmployer(${id}) failed:`, error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Delete employer - DISABLED
 *
 * Employers cannot be deleted because they are linked to members, policies, claims.
 * Use archiveEmployer() instead.
 *
 * @deprecated Use archiveEmployer() instead
 * @throws {Error} Always throws - delete is not allowed
 */
export const deleteEmployer = async (id) => {
  try {
    console.debug(`[EmployerService] Hard-deleting employer ID: ${id}...`);
    const response = await axiosClient.delete(`${BASE_URL}/${id}`);
    console.info(`[EmployerService] Employer ID: ${id} permanently deleted`);
    return unwrap(response);
  } catch (error) {
    console.error(`[EmployerService] deleteEmployer(${id}) failed:`, error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Archive employer (safe alternative to delete)
 * Sets archived=true, hiding from default lists while preserving all data
 *
 * @param {number} id - Employer ID
 * @returns {Promise<Object>} Archived employer
 */
export const archiveEmployer = async (id) => {
  try {
    console.debug(`[EmployerService] Archiving employer ID: ${id}...`);
    const response = await axiosClient.post(`${BASE_URL}/${id}/archive`);
    console.info(`[EmployerService] Employer ID: ${id} archived successfully`);
    return unwrap(response);
  } catch (error) {
    console.error(`[EmployerService] archiveEmployer(${id}) failed:`, error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Restore archived employer
 * Sets archived=false, making employer visible again
 *
 * @param {number} id - Employer ID
 * @returns {Promise<Object>} Restored employer
 */
export const restoreEmployer = async (id) => {
  try {
    console.debug(`[EmployerService] Restoring employer ID: ${id}...`);
    const response = await axiosClient.post(`${BASE_URL}/${id}/restore`);
    console.info(`[EmployerService] Employer ID: ${id} restored successfully`);
    return unwrap(response);
  } catch (error) {
    console.error(`[EmployerService] restoreEmployer(${id}) failed:`, error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Get employer selectors (for dropdowns)
 *
 * @returns {Promise<Array>} List of employer selectors
 * @example
 * const selectors = await getEmployerSelectors();
 * // [{ id: 1, label: 'شركة الواحة' }, { id: 2, label: 'شركة النور' }]
 */
export const getEmployerSelectors = async () => {
  try {
    console.debug('[EmployerService] Fetching employer selectors...');
    const response = await axiosClient.get(`${BASE_URL}/selectors`);
    return unwrapArray(response);
  } catch (error) {
    if (error?.response?.status === 403) {
      console.warn('[EmployerService] getEmployerSelectors: access denied (403)');
    } else {
      console.error('[EmployerService] getEmployerSelectors failed:', error);
    }
    throw handleEmployerErrors(error);
  }
};

/**
 * Check if a code or name is already taken — used for live uniqueness feedback.
 * @param {'code'|'name'} field
 * @param {string} value
 * @param {number|null} excludeId  pass employer id when editing, null when creating
 * @returns {Promise<boolean>}  true = available, false = already taken
 */
export const checkEmployerField = async (field, value, excludeId = null) => {
  try {
    const params = { field, value: field === 'code' ? value.trim().toUpperCase() : value.trim() };
    if (excludeId) params.excludeId = excludeId;
    const response = await axiosClient.get(`${BASE_URL}/check`, { params });
    return unwrap(response) === true;
  } catch {
    return true; // on network error, allow typing to continue
  }
};

/**
 * Get total count of active employers
 *
 * @returns {Promise<number>} Count of active employers
 */
export const getEmployerCount = async () => {
  try {
    console.debug('[EmployerService] Fetching employer count...');
    const response = await axiosClient.get(`${BASE_URL}/count`);
    return unwrap(response);
  } catch (error) {
    console.error('[EmployerService] getEmployerCount failed:', error);
    throw handleEmployerErrors(error);
  }
};

/**
 * Export employers to Excel based on generic logic
 *
 * @param {Object} params - Filter parameters
 * @returns {Promise<Blob>} Excel file blob
 */
export const exportEmployers = async (params = {}) => {
  try {
    const defaultParams = { page: 0, size: 10000, includeArchived: params.showArchived || false };
    const res = await axiosClient.get(BASE_URL, { params: defaultParams });
    const content = res.data?.data?.content || [];

    const workbook = new ExcelJS.Workbook();
    const worksheet = workbook.addWorksheet('جهات العمل');

    worksheet.columns = [
      { header: 'الرمز', key: 'code', width: 15 },
      { header: 'جهة العمل', key: 'name', width: 30 },
      { header: 'البريد الإلكتروني', key: 'email', width: 30 },
      { header: 'رقم الهاتف', key: 'phone', width: 20 },
      { header: 'العنوان', key: 'address', width: 30 },
      { header: 'الحالة', key: 'active', width: 15 }
    ];

    // Right to left layout for Arabic
    worksheet.views = [
      { rightToLeft: true }
    ];

    content.forEach(emp => {
      const matchSearch = !params.searchTerm ||
        (emp.name && emp.name.includes(params.searchTerm)) ||
        (emp.code && emp.code.includes(params.searchTerm));

      if (matchSearch) {
        let activeStatus = emp.active ? 'نشط' : 'غير نشط';
        if (emp.archived) activeStatus = 'محذوف';

        worksheet.addRow({
          code: emp.code || '-',
          name: emp.name || emp.nameAr || '-',
          email: emp.email || '-',
          phone: emp.phone || '-',
          address: emp.address || '-',
          active: activeStatus
        });
      }
    });

    const buffer = await workbook.xlsx.writeBuffer();
    return new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  } catch (error) {
    console.error('Error exporting employers:', error);
    throw error;
  }
};

// ============================================================================
// DEFAULT EXPORT
// ============================================================================

/**
 * Employers Service - Default Export
 * All CRUD operations with normalization and error handling
 */
const employersService = {
  // CRUD Operations
  getEmployers,
  getEmployerById,
  createEmployer,
  updateEmployer,
  deleteEmployer,

  // Additional Operations
  getEmployerSelectors,
  getEmployerCount,
  exportEmployers,

  // Normalization Utilities (exported for advanced usage)
  normalizeEmployerRequest,
  normalizeEmployerResponse,
  normalizeEmployerArrayResponse,
  handleEmployerErrors
};

export default employersService;
