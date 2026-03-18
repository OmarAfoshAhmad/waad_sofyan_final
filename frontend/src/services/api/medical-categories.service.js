import axiosClient from 'utils/axios';
import { normalizePaginatedResponse } from 'utils/api-response-normalizer';

/**
 * Medical Categories API Service
 * Provides CRUD operations for Medical Categories module
 * Backend: MedicalCategoryController.java
 */
const BASE_URL = '/medical-categories';

/**
 * Helper function to unwrap ApiResponse
 */
const unwrap = (response) => response.data?.data || response.data;

/**
 * Get paginated medical categories list
 * @param {Object} params - Query parameters
 * @returns {Promise<Object>} Paginated response
 */
export const getMedicalCategories = async (params = {}) => {
  const response = await axiosClient.get(BASE_URL, { params });
  return normalizePaginatedResponse(response);
};

/**
 * Get medical category by ID
 * @param {number} id - Category ID
 * @returns {Promise<Object>} Category details
 */
export const getMedicalCategoryById = async (id) => {
  const response = await axiosClient.get(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Create new medical category
 * @param {Object} payload - Category data
 * @returns {Promise<Object>} Created category
 */
export const createMedicalCategory = async (payload) => {
  const response = await axiosClient.post(BASE_URL, payload);
  return unwrap(response);
};

/**
 * Update medical category
 * @param {number} id - Category ID
 * @param {Object} payload - Updated category data
 * @returns {Promise<Object>} Updated category
 */
export const updateMedicalCategory = async (id, payload) => {
  const response = await axiosClient.put(`${BASE_URL}/${id}`, payload);
  return unwrap(response);
};

/**
 * Delete medical category
 * @param {number} id - Category ID
 * @returns {Promise<void>}
 */
export const deleteMedicalCategory = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}`);
  return unwrap(response);
};

/**
 * Toggle category active state (SUPER_ADMIN only)
 * @param {number} id - Category ID
 * @returns {Promise<Object>} Updated category
 */
export const toggleMedicalCategory = async (id) => {
  const response = await axiosClient.patch(`${BASE_URL}/${id}/toggle`);
  return unwrap(response);
};

/**
 * Restore a soft-deleted category (sets active=true, deleted=false)
 * @param {number} id - Category ID
 * @returns {Promise<Object>} Restored category
 */
export const restoreMedicalCategory = async (id) => {
  const response = await axiosClient.patch(`${BASE_URL}/${id}/restore`);
  return unwrap(response);
};

/**
 * Permanently delete a medical category (irreversible)
 * @param {number} id - Category ID
 * @returns {Promise<void>}
 */
export const hardDeleteMedicalCategory = async (id) => {
  const response = await axiosClient.delete(`${BASE_URL}/${id}/hard`);
  return unwrap(response);
};

/**
 * Get all categories (for dropdowns)
 * @returns {Promise<Array>} All categories
 */
export const getAllMedicalCategories = async () => {
  const response = await axiosClient.get(`${BASE_URL}/all`);
  return unwrap(response);
};

/**
 * Upload Excel file to import medical categories
 * @param {File} file - Excel file
 * @returns {Promise<Object>} Import result
 */
export const uploadMedicalCategoriesExcel = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axiosClient.post(`${BASE_URL}/import/excel`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 300000 // 5 minutes for large Excel files
  });

  return unwrap(response);
};
