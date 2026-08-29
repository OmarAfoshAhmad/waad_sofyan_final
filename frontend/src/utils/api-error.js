/**
 * @returns {{code: string, category: string, message: string, details: object,
 *            trackingId: (string|null)}}
 *
 * trackingId is the last link in a chain that was already complete on the
 * server and stopped here. LogMdcFilter stamps a traceId onto every request,
 * logback prints it on every line as [%X{traceId}], and GlobalExceptionHandler
 * returns it as trackingId on the error body -- but nothing showed it, so a
 * user reporting a failure had nothing to quote and support had no way to find
 * the line. It is null when the failure never reached the server at all, which
 * is itself worth being able to tell apart.
 */
export const normalizeApiError = (error) => {
  const payload = error?.response?.data || {};

  const code = payload.code || payload.errorCode || 'UNKNOWN_ERROR';
  const category = payload.category || 'SYSTEM';
  const message = payload.messageAr || payload.message || error?.userMessage || 'حدث خطأ غير متوقع';
  const details = payload.details || {
    reason: error?.message || 'Unknown error'
  };
  const trackingId = payload.trackingId || null;

  return { code, category, message, details, trackingId };
};

export const runWithRetry = async (operation, { maxRetries = 1, shouldRetry } = {}) => {
  let lastError;

  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;

      const retryable = shouldRetry ? shouldRetry(error) : !error?.response || error?.response?.status >= 500;

      if (attempt >= maxRetries || !retryable) {
        throw error;
      }
    }
  }

  throw lastError;
};
