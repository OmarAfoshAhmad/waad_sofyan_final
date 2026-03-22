/**
 * ============================================================================
 * Formatting Utilities - Centralized Formatters
 * ============================================================================
 *
 * Standardized formatters for the entire application.
 *
 * CRITICAL RULES:
 * - All numbers: English digits (1234567890)
 * - All dates: English format (DD/MM/YYYY)
 * - All currency: Libyan Dinar (د.ل or LYD)
 * - Locale: 'en-US' for all formatters
 *
 * @created 2026-01-03
 */

// ============================================================================
// CONSTANTS
// ============================================================================

export const LOCALE = 'en-US';
export const CURRENCY_CODE = 'LYD';
export const CURRENCY_SYMBOL = 'د.ل';

// ============================================================================
// NUMBER FORMATTERS
// ============================================================================

/**
 * Format number with English digits and thousands separator
 * @param {number} value - Number to format
 * @returns {string} Formatted number (e.g., "1,234,567")
 */
export const formatNumber = (value) => {
  if (value === null || value === undefined || isNaN(value)) return '-';
  return new Intl.NumberFormat(LOCALE).format(value);
};

/**
 * Format currency in Libyan Dinars with English digits
 * @param {number} value - Amount to format
 * @param {boolean} showSymbol - Show currency symbol (default: true)
 * @returns {string} Formatted currency (e.g., "1,234.56 د.ل")
 */
export const formatCurrency = (value, showSymbol = true) => {
  if (value === null || value === undefined || isNaN(value)) return '-';

  const formatted = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);

  return showSymbol ? `${formatted} ${CURRENCY_SYMBOL}` : formatted;
};

/**
 * Format percentage with English digits
 * @param {number} value - Percentage value
 * @param {number} decimals - Number of decimal places (default: 1)
 * @returns {string} Formatted percentage (e.g., "12.5%")
 */
export const formatPercentage = (value, decimals = 1) => {
  if (value === null || value === undefined || isNaN(value)) return '-';
  return `${value.toFixed(decimals)}%`;
};

// ============================================================================
// DATE FORMATTERS
// ============================================================================

/**
 * Format date with English digits (DD/MM/YYYY)
 * @param {string|Date} date - Date to format
 * @returns {string} Formatted date (e.g., "15/01/2024")
 */
export const formatDate = (date) => {
  if (!date) return '-';

  try {
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';

    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch (error) {
    console.error('Date formatting error:', error);
    return '-';
  }
};

/**
 * Format currency in Libyan Dinars (Alias for standard currency)
 * @param {number} value - Amount to format
 */
export const formatCurrencyLYD = (value) => formatCurrency(value);

/**
 * Format date with time (DD/MM/YYYY HH:MM)
 * @param {string|Date} date - Date to format
 * @returns {string} Formatted date and time
 */
export const formatDateTime = (date) => {
  if (!date) return '-';

  try {
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';

    return new Intl.DateTimeFormat(LOCALE, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(d);
  } catch (error) {
    console.error('DateTime formatting error:', error);
    return '-';
  }
};

/**
 * Format date in long format (e.g., "January 15, 2024")
 * @param {string|Date} date - Date to format
 * @returns {string} Formatted date in long format
 */
export const formatDateLong = (date) => {
  if (!date) return '-';

  try {
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';

    return new Intl.DateTimeFormat(LOCALE, {
      dateStyle: 'long'
    }).format(d);
  } catch (error) {
    console.error('Date long formatting error:', error);
    return '-';
  }
};

/**
 * Format time only (HH:MM)
 * @param {string|Date} date - Date to extract time from
 * @returns {string} Formatted time
 */
export const formatTime = (date) => {
  if (!date) return '-';

  try {
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';

    return new Intl.DateTimeFormat(LOCALE, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(d);
  } catch (error) {
    console.error('Time formatting error:', error);
    return '-';
  }
};

// ============================================================================
// SPECIALIZED FORMATTERS
// ============================================================================

/**
 * Format file size in bytes to human-readable format
 * @param {number} bytes - Size in bytes
 * @returns {string} Formatted size (e.g., "1.5 MB")
 */
export const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B';

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

/**
 * Format phone number
 * @param {string} phone - Phone number
 * @returns {string} Formatted phone number
 */
export const formatPhone = (phone) => {
  if (!phone) return '-';
  // Remove all non-digit characters for display
  return phone.replace(/\D/g, '');
};

// ============================================================================
// EXPORTS
// ============================================================================

export default {
  formatNumber,
  formatCurrency,
  formatPercentage,
  formatDate,
  formatDateTime,
  formatDateLong,
  formatTime,
  formatFileSize,
  formatPhone,
  LOCALE,
  CURRENCY_CODE,
  CURRENCY_SYMBOL
};
