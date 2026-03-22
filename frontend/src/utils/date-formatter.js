/**
 * Date Formatter Utility
 * تنسيق التواريخ بالإنجليزية
 *
 * @version 2026.1
 */

/**
 * Format date to English locale
 * @param {string|Date} date - Date to format
 * @param {string} format - Format type (default: 'medium')
 * @returns {string} Formatted date string
 *
 * @example
 * formatDate('2026-01-04') // "January 4, 2026"
 * formatDate('2026-01-04', 'short') // "01/04/2026"
 * formatDate('2026-01-04', 'medium') // "Jan 4, 2026"
 */
export const formatDate = (date, format = 'medium') => {
  if (!date) return '-';

  const dateObj = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(dateObj.getTime())) return '-';

  const day = String(dateObj.getDate()).padStart(2, '0');
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const year = dateObj.getFullYear();

  if (format === 'full' || format === 'long') {
    return dateObj.toLocaleDateString('en-GB', {
      weekday: format === 'full' ? 'long' : undefined,
      year: 'numeric',
      month: 'long',
      day: '2-digit'
    });
  }

  return `${day}/${month}/${year}`;
};

/**
 * Format date to ISO string (YYYY-MM-DD)
 * @param {string|Date} date - Date to format
 * @returns {string} ISO date string
 *
 * @example
 * formatDateISO(new Date()) // "2026-01-04"
 */
export const formatDateISO = (date) => {
  if (!date) return '';

  const dateObj = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(dateObj.getTime())) return '';

  return dateObj.toISOString().split('T')[0];
};

/**
 * Format datetime with time
 * @param {string|Date} datetime - Datetime to format
 * @param {boolean} includeSeconds - Include seconds (default: false)
 * @returns {string} Formatted datetime string
 *
 * @example
 * formatDateTime('2026-01-04T15:30:00') // "Jan 4, 2026 3:30 PM"
 */
export const formatDateTime = (datetime, includeSeconds = false) => {
  if (!datetime) return '-';

  const dateObj = typeof datetime === 'string' ? new Date(datetime) : datetime;

  if (isNaN(dateObj.getTime())) return '-';

  const day = String(dateObj.getDate()).padStart(2, '0');
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const year = dateObj.getFullYear();
  const hours = String(dateObj.getHours()).padStart(2, '0');
  const minutes = String(dateObj.getMinutes()).padStart(2, '0');
  const seconds = String(dateObj.getSeconds()).padStart(2, '0');

  const datePart = `${day}/${month}/${year}`;
  const timePart = includeSeconds ? `${hours}:${minutes}:${seconds}` : `${hours}:${minutes}`;

  return `${datePart} ${timePart}`;
};

/**
 * Format time only
 * @param {string|Date} datetime - Datetime to format
 * @param {boolean} includeSeconds - Include seconds (default: false)
 * @returns {string} Formatted time string
 *
 * @example
 * formatTime('2026-01-04T15:30:00') // "3:30 PM"
 */
export const formatTime = (datetime, includeSeconds = false) => {
  if (!datetime) return '-';

  const dateObj = typeof datetime === 'string' ? new Date(datetime) : datetime;

  if (isNaN(dateObj.getTime())) return '-';

  const timeOptions = includeSeconds
    ? { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true }
    : { hour: '2-digit', minute: '2-digit', hour12: true };

  const locale = 'en-US';

  return dateObj.toLocaleTimeString(locale, timeOptions);
};

/**
 * Calculate days difference between two dates
 * @param {string|Date} date1 - First date
 * @param {string|Date} date2 - Second date (default: today)
 * @returns {number} Days difference
 *
 * @example
 * daysDifference('2026-01-01', '2026-01-04') // 3
 */
export const daysDifference = (date1, date2 = new Date()) => {
  const d1 = typeof date1 === 'string' ? new Date(date1) : date1;
  const d2 = typeof date2 === 'string' ? new Date(date2) : date2;

  if (isNaN(d1.getTime()) || isNaN(d2.getTime())) return 0;

  const diffTime = Math.abs(d2.getTime() - d1.getTime());
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  return diffDays;
};

/**
 * Check if date is in the past
 * @param {string|Date} date - Date to check
 * @returns {boolean} True if date is in the past
 */
export const isPast = (date) => {
  if (!date) return false;

  const dateObj = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(dateObj.getTime())) return false;

  return dateObj < new Date();
};

/**
 * Check if date is in the future
 * @param {string|Date} date - Date to check
 * @returns {boolean} True if date is in the future
 */
export const isFuture = (date) => {
  if (!date) return false;

  const dateObj = typeof date === 'string' ? new Date(date) : date;

  if (isNaN(dateObj.getTime())) return false;

  return dateObj > new Date();
};

export default formatDate;
