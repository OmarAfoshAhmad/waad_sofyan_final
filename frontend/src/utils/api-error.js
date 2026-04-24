export const normalizeApiError = (error) => {
  const payload = error?.response?.data || {};

  const code = payload.code || payload.errorCode || 'UNKNOWN_ERROR';
  const category = payload.category || 'SYSTEM';
  const message = payload.messageAr || payload.message || error?.userMessage || 'حدث خطأ غير متوقع';
  const details = payload.details || {
    reason: error?.message || 'Unknown error'
  };

  return { code, category, message, details };
};

export const runWithRetry = async (operation, { maxRetries = 1, shouldRetry } = {}) => {
  let lastError;

  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;

      const retryable = shouldRetry
        ? shouldRetry(error)
        : (!error?.response || error?.response?.status >= 500);

      if (attempt >= maxRetries || !retryable) {
        throw error;
      }
    }
  }

  throw lastError;
};
