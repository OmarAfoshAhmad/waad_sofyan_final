/**
 * Currency Formatter Utility
 * تنسيق العملات بالدينار الليبي
 *
 * @version 2026.1
 */

/**
 * Format number as LYD currency (Libyan Dinar)
 * @param {number} amount - Amount to format
 * @param {string} locale - Locale (default: en-US)
 * @returns {string} Formatted currency string
 *
 * @example
 * formatCurrency(1500.50) // "1,500.50 د.ل"
 * formatCurrency(1500) // "1,500.00 د.ل"
 */
export const formatCurrency = (amount, locale = 'en-US') => {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '0.00 د.ل';
  }

  const formatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  return `${formatter.format(amount)} د.ل`;
};

/**
 * Format number as currency without symbol
 * @param {number} amount - Amount to format
 * @param {string} locale - Locale (default: en-US)
 * @returns {string} Formatted number string
 *
 * @example
 * formatNumber(1500.50) // "1,500.500"
 */
export const formatNumber = (amount, locale = 'en-US') => {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '0.00';
  }

  const formatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  return formatter.format(amount);
};

/**
 * Parse formatted currency string to number
 * @param {string} currencyString - Formatted currency string
 * @returns {number} Parsed number
 *
 * @example
 * parseCurrency("1,500.500 د.ل") // 1500.500
 */
export const parseCurrency = (currencyString) => {
  if (!currencyString) return 0;

  // Remove currency symbol and spaces
  const cleaned = currencyString.replace(/[^\d.-]/g, '');
  const parsed = parseFloat(cleaned);

  return isNaN(parsed) ? 0 : parsed;
};

export default formatCurrency;
