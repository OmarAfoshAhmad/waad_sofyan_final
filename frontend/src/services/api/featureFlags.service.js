/**
 * Feature Flags API Service
 *
 * Provides access to feature flags stored in the DB.
 * - getPublicFlags(): No auth required — called on app load
 * - getAllFlags(): SUPER_ADMIN only
 * - toggleFlag(): SUPER_ADMIN only
 *
 * API Base: /api/v1/admin/features
 */

import axiosClient from 'utils/axios';

const BASE_URL = '/admin/features';

const featureFlagsService = {
  /**
   * Get public feature flags (no auth required).
   * Used on app load to determine visible features.
   * @returns {Promise<Array>} List of public feature flag DTOs
   */
  getPublicFlags: async () => {
    const response = await axiosClient.get(`${BASE_URL}/public`, {
      suppress401Handling: true
    });
    return response.data?.data ?? [];
  },

  /**
   * Get all feature flags (SUPER_ADMIN only).
   * @returns {Promise<Array>} Full list of feature flags
   */
  getAllFlags: async () => {
    const response = await axiosClient.get(BASE_URL);
    return response.data?.data ?? [];
  },

  /**
   * Toggle a feature flag on or off (SUPER_ADMIN only).
   * @param {string} flagKey - Flag identifier (e.g. 'PROVIDER_PORTAL_ENABLED')
   * @param {boolean} enabled - New state
   * @returns {Promise<Object>} Updated flag DTO
   */
  toggleFlag: async (flagKey, enabled) => {
    const response = await axiosClient.put(`${BASE_URL}/${flagKey}/toggle`, null, {
      params: { enabled }
    });
    return response.data?.data;
  },

  /**
   * Get UI configuration (public, no auth).
   * Returns: { logoUrl, fontFamily, fontSizeBase, systemNameAr, systemNameEn }
   * @returns {Promise<Object>}
   */
  getUiConfig: async () => {
    const response = await axiosClient.get('/admin/system-settings/ui-config', {
      suppress401Handling: true
    });
    return response.data;
  }
};

export default featureFlagsService;
