/**
 * RBAC Users Service
 * Responsible for: User entity CRUD operations
 * Backend: UserController (/api/admin/users)
 *
 * This service handles ONLY user entity operations.
 * For user account management (status, password, roles), use userManagement.service.js
 */

import axiosServices from '../../utils/axios';

const BASE_URL = '/admin/users';

export const usersService = {
  /**
   * Get all users (list)
   * GET /api/admin/users
   */
  getAllUsers: async () => {
    const response = await axiosServices.get(BASE_URL);
    return response?.data?.data || response?.data || [];
  },

  /**
   * Get user by ID
   * GET /api/admin/users/{id}
   */
  getUserById: (id) => {
    return axiosServices.get(`${BASE_URL}/${id}`);
  },

  /**
   * Create new user
   * POST /api/admin/users
   */
  createUser: (userData) => {
    return axiosServices.post(BASE_URL, userData);
  },

  /**
   * Update user
   * PUT /api/admin/users/{id}
   */
  updateUser: (id, userData) => {
    return axiosServices.put(`${BASE_URL}/${id}`, userData);
  },

  /**
   * Safely update part of a user.
   *
   * Backend PUT /admin/users/{id} currently validates a full UserUpdateDto
   * (username, fullName, email). Provider linking screens often need to update
   * only providerId/employerId, so this helper first loads the current user and
   * sends a complete payload to avoid accidental validation failures or data loss.
   */
  updateUserPatch: async (id, patchData = {}) => {
    const currentResponse = await axiosServices.get(`${BASE_URL}/${id}`);
    const current = currentResponse?.data?.data || currentResponse?.data || {};
    const merged = {
      username: current.username,
      fullName: current.fullName || current.username,
      email: current.email,
      phone: current.phone || null,
      active: current.active !== false,
      userType: current.role || current.userType || 'DATA_ENTRY',
      employerId: current.employerId ?? null,
      providerId: current.providerId ?? null,
      ...patchData
    };

    return axiosServices.put(`${BASE_URL}/${id}`, merged);
  },

  /**
   * Update user
   * PUT /api/admin/users/{id}
   */
  updateUserPermissions: (id, permissions) => {
    return axiosServices.put(`${BASE_URL}/${id}/permissions`, permissions);
  },

  /**
   * Download Excel template for importing provider users
   * GET /api/admin/users/import/providers/template
   */
  downloadProviderUsersTemplate: async () => {
    const response = await axiosServices.get(`${BASE_URL}/import/providers/template`, {
      responseType: 'blob'
    });
    return response;
  },

  /**
   * Import provider users from Excel file
   * POST /api/admin/users/import/providers
   */
  importProviderUsers: async (file) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await axiosServices.post(`${BASE_URL}/import/providers`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
    return response?.data?.data || response?.data;
  },

  /**
   * Delete user
   * DELETE /api/admin/users/{id}
   */
  deleteUser: (id) => {
    return axiosServices.delete(`${BASE_URL}/${id}`);
  },

  /**
   * Toggle user status (activate/deactivate)
   * PATCH /api/admin/users/{id}/toggle-status
   */
  toggleUserStatus: async (id) => {
    const response = await axiosServices.patch(`${BASE_URL}/${id}/toggle-status`);
    return response?.data;
  },

  /**
   * Reset user password (Super Admin Only)
   * PUT /api/admin/users/{id}/reset-password
   */
  resetPassword: async (id, newPassword) => {
    const response = await axiosServices.put(`${BASE_URL}/${id}/reset-password`, { newPassword });
    return response?.data;
  },

  /**
   * Search users
   * GET /api/admin/users/search?query={query}
   */
  searchUsers: (query) => {
    return axiosServices.get(`${BASE_URL}/search`, {
      params: { query }
    });
  },

  /**
   * Get users paginated
   * GET /api/admin/users/paginate?page={page}&size={size}
   */
  getUsersPaginated: (page = 0, size = 10) => {
    return axiosServices.get(`${BASE_URL}/paginate`, {
      params: { page, size }
    });
  },

  /**
   * Get users paginated with sorting - TbaDataTable format
   * GET /api/admin/users/paginate?page={page}&size={size}&sortBy={field}&sortDir={dir}
   *
   * ⚠️ Backend returns Spring Page format: { content: [], totalElements: N }
   * TbaDataTable expects: { items: [], total: N }
   */
  getUsersTable: async (params) => {
    const { page = 1, size = 20, sortBy = 'id', sortDir = 'asc', search = '', role = '', active = '', providerLink = '' } = params || {};
    // Backend paginate uses 0-based page, frontend sends 1-based
    const endpoint = search || role || active !== '' || providerLink ? `${BASE_URL}/search/paginate` : `${BASE_URL}/paginate`;
    const response = await axiosServices.get(endpoint, {
      params: { page: Math.max(0, page - 1), size, sortBy, sortDir, query: search, role, active: active === '' ? undefined : active, providerLink }
    });
    // Unwrap ApiResponse and transform Spring Page to TbaDataTable format
    const pageData = response?.data?.data || response?.data || {};
    return {
      items: pageData?.content || [],
      total: pageData?.totalElements || 0,
      page: (pageData?.number || 0) + 1,
      size: pageData?.size || size
    };
  },

  /**
   * Get unassigned providers (users with PROVIDER role but no providerId)
   * GET /api/admin/users/unassigned-providers
   */
  getUnassignedProviders: async () => {
    const response = await axiosServices.get(`${BASE_URL}/unassigned-providers`);
    return response?.data?.data || response?.data || [];
  },

  /**
   * Get users assigned to a provider
   * GET /api/admin/users/provider/{providerId}
   */
  getUsersByProvider: async (providerId) => {
    const response = await axiosServices.get(`${BASE_URL}/provider/${providerId}`);
    return response?.data?.data || response?.data || [];
  }
};

export default usersService;
