/**
 * API Error Handler - Unified Error Handling Utility
 * Provides consistent error handling across all API services
 */

/**
 * Generic API error handler
 * Maps backend errors to user-friendly Arabic messages
 *
 * @param {Error} error - Axios error object
 * @param {Object} customMessages - Custom error messages per status code
 * @param {string} entityName - Entity name in Arabic (e.g., 'المطالبة', 'السياسة')
 * @returns {Object} Structured error object
 */
export const handleApiError = (error, customMessages = {}, entityName = 'العنصر') => {
  const status = error?.response?.status;
  const data = error?.response?.data;
  const message = data?.messageAr || data?.message || data?.error || error?.response?.statusText || error?.message || 'تعذر الاتصال بالخادم أو حدث خطأ داخلي';
  const backendErrorCode = data?.errorCode || data?.code || null;

  console.error(`[API Error] ${entityName}:`, {
    status,
    message,
    data
  });

  // Structure error response
  const errorResponse = {
    status: status || 500,
    message,
    errorCode: backendErrorCode,
    fieldErrors: null,
    originalError: error
  };

  // Default messages for common status codes
  const defaultMessages = {
    400: 'يرجى تصحيح الأخطاء في النموذج',
    401: 'يجب تسجيل الدخول للمتابعة',
    403: 'ليس لديك صلاحية لتنفيذ هذه العملية',
    404: `${entityName} غير موجود`,
    409: `${entityName} مكرر أو يوجد تعارض في البيانات`,
    422: 'البيانات المُدخلة غير صحيحة، يرجى مراجعتها وتصحيحها',
    500: 'حدث خطأ داخلي في الخادم، يرجى إعادة المحاولة لاحقاً',
    503: 'الخدمة غير متاحة حالياً'
  };

  // Merge custom messages with defaults
  const messages = { ...defaultMessages, ...customMessages };

  // Handle specific error types
  switch (status) {
    case 400: // Validation Error
      errorResponse.message = messages[400];
      errorResponse.fieldErrors = data?.errors || {};
      break;

    case 401: // Unauthorized
      errorResponse.message = messages[401];
      break;

    case 403: // Forbidden
      errorResponse.message = messages[403];
      break;

    case 404: // Not Found
      errorResponse.message = messages[404];
      break;

    case 409: // Conflict
      if (backendErrorCode === 'INVALID_CLAIM_TRANSITION') {
        const fromStatus = data?.details?.fromStatus;
        const toStatus = data?.details?.toStatus;
        const requiredRole = data?.details?.requiredRole;

        errorResponse.message =
          fromStatus && toStatus
            ? `لا يمكن تنفيذ الإجراء لأن حالة المطالبة تغيّرت (${fromStatus} ← ${toStatus}). قم بتحديث الصفحة ثم أعد المحاولة.`
            : 'لا يمكن تنفيذ الإجراء بسبب تعارض في حالة المطالبة. قم بتحديث الصفحة ثم أعد المحاولة.';

        if (requiredRole) {
          errorResponse.message += ` (الدور المطلوب: ${requiredRole})`;
        }
      } else {
        errorResponse.message = messages[409];
      }
      break;

    case 422: // Unprocessable Entity
      errorResponse.message = data?.messageAr || data?.message || messages[422];
      errorResponse.fieldErrors = data?.errors || {};
      break;

    case 500: // Server Error
      errorResponse.message = messages[500];
      break;

    case 503: // Service Unavailable
      errorResponse.message = messages[503];
      break;

    default:
      if (message.includes('Network Error')) {
        errorResponse.message = 'خطأ في الشبكة، يرجى التحقق من اتصالك بالإنترنت';
      } else {
        errorResponse.message = message || 'عذراً، تعذر معالجة الطلب لسبب غير معروف. يرجى إعادة المحاولة';
      }
  }

  return errorResponse;
};

/**
 * Create entity-specific error handler
 * Returns a configured error handler for a specific entity
 *
 * @param {string} entityName - Entity name in Arabic
 * @param {Object} customMessages - Custom messages for this entity
 * @returns {Function} Error handler function
 */
export const createErrorHandler = (entityName, customMessages = {}) => {
  return (error) => handleApiError(error, customMessages, entityName);
};

/**
 * Map backend field names to frontend field names
 * Useful for validation error mapping
 *
 * @param {Object} fieldErrors - Backend field errors
 * @param {Object} fieldMapping - Field name mapping (backend → frontend)
 * @returns {Object} Mapped field errors
 */
export const mapFieldErrors = (fieldErrors, fieldMapping = {}) => {
  if (!fieldErrors || typeof fieldErrors !== 'object') {
    return {};
  }

  const mappedErrors = {};
  Object.keys(fieldErrors).forEach((backendField) => {
    const frontendField = fieldMapping[backendField] || backendField;
    mappedErrors[frontendField] = fieldErrors[backendField];
  });

  return mappedErrors;
};

/**
 * Extract validation errors from error response
 *
 * @param {Object} errorResponse - Error response object
 * @returns {Object} Field errors object
 */
export const extractFieldErrors = (errorResponse) => {
  return errorResponse?.fieldErrors || {};
};

/**
 * Check if error is a validation error (400 or 422)
 *
 * @param {Object} errorResponse - Error response object
 * @returns {boolean} True if validation error
 */
export const isValidationError = (errorResponse) => {
  const status = errorResponse?.status;
  return status === 400 || status === 422;
};

/**
 * Check if error is an authorization error (401 or 403)
 *
 * @param {Object} errorResponse - Error response object
 * @returns {boolean} True if authorization error
 */
export const isAuthError = (errorResponse) => {
  const status = errorResponse?.status;
  return status === 401 || status === 403;
};

/**
 * Check if error is a not found error (404)
 *
 * @param {Object} errorResponse - Error response object
 * @returns {boolean} True if not found error
 */
export const isNotFoundError = (errorResponse) => {
  return errorResponse?.status === 404;
};

/**
 * Check if error is a conflict error (409)
 *
 * @param {Object} errorResponse - Error response object
 * @returns {boolean} True if conflict error
 */
export const isConflictError = (errorResponse) => {
  return errorResponse?.status === 409;
};

/**
 * Check if error is a server error (500+)
 *
 * @param {Object} errorResponse - Error response object
 * @returns {boolean} True if server error
 */
export const isServerError = (errorResponse) => {
  const status = errorResponse?.status;
  return status >= 500;
};

export default {
  handleApiError,
  createErrorHandler,
  mapFieldErrors,
  extractFieldErrors,
  isValidationError,
  isAuthError,
  isNotFoundError,
  isConflictError,
  isServerError
};
