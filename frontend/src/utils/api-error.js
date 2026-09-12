const FALLBACK_MESSAGE = 'حدث خطأ غير متوقع';

const firstText = (...values) => {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return null;
};

const detailsMessage = (details) => {
  if (!details) return null;
  if (typeof details === 'string') return details;
  if (Array.isArray(details)) return details.map(detailsMessage).filter(Boolean).join('، ') || null;
  if (typeof details !== 'object') return null;

  const direct = firstText(details.messageAr, details.message, details.error, details.reason, details.detail);
  if (direct) return direct;

  const errors = details.errors || details.fieldErrors || details.validationErrors;
  if (errors && typeof errors === 'object') {
    if (Array.isArray(errors)) return errors.map(detailsMessage).filter(Boolean).join('، ') || null;
    const values = Object.entries(errors)
      .map(([field, value]) => {
        const msg = detailsMessage(value) || (typeof value === 'string' ? value : null);
        return msg ? `${field}: ${msg}` : null;
      })
      .filter(Boolean);
    if (values.length) return values.join('، ');
  }

  return null;
};

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
  const isTextPayload = typeof payload === 'string';
  const body = isTextPayload ? {} : payload;

  const details = body.details || {
    reason: error?.message || 'Unknown error'
  };

  const code = body.code || body.errorCode || body.error || error?.code || 'UNKNOWN_ERROR';
  const category = body.category || 'SYSTEM';
  const message =
    firstText(
      body.messageAr,
      body.message,
      body.errorDescription,
      body.title,
      isTextPayload ? payload : null,
      error?.userMessage,
      detailsMessage(details)
    ) || FALLBACK_MESSAGE;
  const trackingId = body.trackingId || body.traceId || body.reference || null;

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
