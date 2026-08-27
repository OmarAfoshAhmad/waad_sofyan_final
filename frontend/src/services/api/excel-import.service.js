/**
 * Excel Import Service - Unified API for all modules
 *
 * System-Generated Templates Architecture:
 * 1. Download template from system
 * 2. Fill template in Excel
 * 3. Upload filled template
 * 4. Review detailed results
 */

import axios from 'utils/axios';

const unwrap = (response) => response.data?.data || response.data;

// ═══════════════════════════════════════════════════════════════════════════
// MEMBERS MODULE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Members import template
 * GET /members/import/template
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const downloadMemberTemplate = async () => {
  const response = await axios.get('/members/import/template', {
    responseType: 'blob'
  });
  return response.data;
};

// ═══════════════════════════════════════════════════════════════════════════
// PROVIDERS MODULE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Providers import template
 * GET /providers/import/template
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const downloadProviderTemplate = async () => {
  const response = await axios.get('/providers/import/template', {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Import providers from Excel
 * POST /providers/import
 * @param {File} file - Excel file
 * @returns {Promise<ExcelImportResult>}
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const importProviders = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axios.post('/providers/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// MEDICAL SERVICES MODULE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Medical Services import template
 * GET /medical-services/import/template
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const downloadMedicalServiceTemplate = async () => {
  const response = await axios.get('/medical-services/import/template', {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Import medical services from Excel
 * POST /medical-services/import
 * @param {File} file - Excel file
 * @returns {Promise<ExcelImportResult>}
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const importMedicalServices = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axios.post('/medical-services/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// MEDICAL CATEGORIES MODULE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Medical Categories import template
 * GET /medical-categories/import/template
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const downloadMedicalCategoryTemplate = async () => {
  const response = await axios.get('/medical-categories/import/template', {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Import medical categories from Excel
 * POST /medical-categories/import
 * @param {File} file - Excel file
 * @returns {Promise<ExcelImportResult>}
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const importMedicalCategories = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axios.post('/medical-categories/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// MEDICAL PACKAGES MODULE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download Medical Packages import template
 * GET /medical-packages/import/template
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const downloadMedicalPackageTemplate = async () => {
  const response = await axios.get('/medical-packages/import/template', {
    responseType: 'blob'
  });
  return response.data;
};

/**
 * Import medical packages from Excel
 * POST /medical-packages/import
 * @param {File} file - Excel file
 * @returns {Promise<ExcelImportResult>}
 *
 * NOTE: No /api prefix - axios baseURL already includes /api
 */
export const importMedicalPackages = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axios.post('/medical-packages/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5 minutes for large Excel files
  });
  return unwrap(response);
};

// ═══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Download blob as file
 * @param {Blob} blob - File data
 * @param {string} filename - Filename
 */
export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.style.display = 'none';
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
};

/**
 * Format import result for display
 * @param {ExcelImportResult} result
 * @returns {string}
 */
export const formatImportSummary = (result) => {
  const { summary } = result;
  return `تم إنشاء ${summary.created} سجل، تم رفض ${summary.rejected} سجل، فشل ${summary.failed} سجل`;
};

/**
 * Get error type label (Arabic)
 * @param {string} errorType
 * @returns {string}
 */
export const getErrorTypeLabel = (errorType) => {
  const labels = {
    MISSING_REQUIRED: 'حقل مطلوب مفقود',
    LOOKUP_FAILED: 'فشل البحث',
    INVALID_FORMAT: 'صيغة غير صحيحة',
    INVALID_ENUM: 'قيمة غير صالحة',
    MAX_LENGTH_EXCEEDED: 'تجاوز الحد الأقصى',
    DUPLICATE: 'قيمة مكررة',
    SYSTEM_GENERATED_IGNORED: 'حقل مُنشأ تلقائياً',
    BUSINESS_RULE_VIOLATION: 'انتهاك قاعدة عمل',
    PROCESSING_ERROR: 'خطأ في المعالجة'
  };
  return labels[errorType] || errorType;
};

/**
 * Check if import has errors
 * @param {ExcelImportResult} result
 * @returns {boolean}
 */
export const hasImportErrors = (result) => {
  return result.errors && result.errors.length > 0;
};

/**
 * Check if import was successful
 * @param {ExcelImportResult} result
 * @returns {boolean}
 */
export const isImportSuccessful = (result) => {
  return result.success && result.summary.created > 0;
};

export default {
  // Members
  downloadMemberTemplate,
  // Providers
  downloadProviderTemplate,
  importProviders,

  // Medical Services
  downloadMedicalServiceTemplate,
  importMedicalServices,

  // Medical Categories
  downloadMedicalCategoryTemplate,
  importMedicalCategories,

  // Medical Packages
  downloadMedicalPackageTemplate,
  importMedicalPackages,

  // Helpers
  downloadBlob,
  formatImportSummary,
  getErrorTypeLabel,
  hasImportErrors,
  isImportSuccessful
};
