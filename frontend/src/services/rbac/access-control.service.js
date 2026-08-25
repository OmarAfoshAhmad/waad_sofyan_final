import axios from 'utils/axios';

const BASE_URL = '/admin/access-control';

const unwrap = (response) => response?.data?.data ?? response?.data;

const accessControlService = {
  async getPermissionCatalogue() {
    return unwrap(await axios.get(`${BASE_URL}/permissions`));
  },

  async getRoleTemplates() {
    return unwrap(await axios.get(`${BASE_URL}/roles`));
  },

  async getEffectivePermissions(userId) {
    return unwrap(await axios.get(`${BASE_URL}/users/${userId}/effective-permissions`));
  },

  async updatePermissionOverrides(userId, commands) {
    return unwrap(await axios.put(`${BASE_URL}/users/${userId}/permission-overrides`, commands));
  },

  async createManagedUser(user, permissionOverrides = []) {
    return unwrap(await axios.post(`${BASE_URL}/users`, { user, permissionOverrides }));
  }
};

export default accessControlService;
