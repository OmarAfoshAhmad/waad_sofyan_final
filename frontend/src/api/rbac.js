import { create } from 'zustand';
import { SystemRole, isProviderRole, getPrivilegeLevel, getAssignableRoles, canModifyRole } from 'constants/rbac';
import { clearToken } from 'utils/token-storage';

/**
 * RBAC Store — Phase 5 (Static Role-Based Auth)
 *
 * SIMPLIFIED: No permissions, no domains, no resource:action.
 * Each user has exactly ONE role (from user.roles[0]).
 * SUPER_ADMIN bypasses all checks.
 *
 * Backend login returns: { roles: ["SUPER_ADMIN"], permissions: [] }
 */

const STORAGE_KEYS = {
  ROLES: 'userRoles',
  USER: 'userData',
  TOKEN: 'serviceToken'
};

export const useRBACStore = create((set, get) => ({
  roles: [],
  user: null,
  isInitialized: false,

  /**
   * Initialize RBAC state from backend user data or localStorage
   */
  initialize: (userData = null) => {
    try {
      let roles = [];
      let user = null;

      if (userData) {
        roles = userData.roles || [];
        user = userData;
        localStorage.setItem(STORAGE_KEYS.ROLES, JSON.stringify(roles));
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
      } else {
        const rolesStr = localStorage.getItem(STORAGE_KEYS.ROLES);
        roles = rolesStr ? JSON.parse(rolesStr) : [];
        const userStr = localStorage.getItem(STORAGE_KEYS.USER);
        user = userStr ? JSON.parse(userStr) : null;
      }

      set({ roles, user, isInitialized: true });
    } catch (error) {
      set({ isInitialized: true });
    }
  },

  clear: () => {
    localStorage.removeItem(STORAGE_KEYS.ROLES);
    localStorage.removeItem(STORAGE_KEYS.USER);
    clearToken();
    localStorage.removeItem('userPermissions');
    localStorage.removeItem('selectedEmployerId');
    localStorage.removeItem('selectedEmployerName');
    set({ roles: [], user: null, isInitialized: false });
  },

  isSuperAdmin: () => {
    const { roles } = get();
    return roles.includes(SystemRole.SUPER_ADMIN);
  },

  getPrimaryRole: () => {
    const { roles } = get();
    return roles.length > 0 ? roles[0] : null;
  },

  hasRole: (allowedRoles) => {
    const { roles } = get();
    const primaryRole = roles[0];
    if (!allowedRoles || allowedRoles.length === 0) return true;
    return allowedRoles.includes(primaryRole);
  },

  isEmployerRole: () => {
    const { roles } = get();
    return roles[0] === SystemRole.EMPLOYER_ADMIN || roles[0] === 'EMPLOYER';
  },

  isProviderRole: () => {
    const { roles } = get();
    return isProviderRole(roles[0]);
  },

  canManageRbac: () => {
    const { roles } = get();
    return roles[0] === SystemRole.SUPER_ADMIN;
  },

  canManageSystem: () => {
    const { roles } = get();
    return roles[0] === SystemRole.SUPER_ADMIN;
  },

  canModifyUserWithRole: (targetRole) => {
    const { roles } = get();
    return canModifyRole(roles[0], targetRole);
  },

  canAssignRole: (roleToAssign) => {
    const { roles } = get();
    return getAssignableRoles(roles[0]).includes(roleToAssign);
  },

  getAssignableRoles: () => {
    const { roles } = get();
    return getAssignableRoles(roles[0]);
  },

  getPrivilegeLevel: () => {
    const { roles } = get();
    return getPrivilegeLevel(roles[0]);
  }
}));

// ==============================|| EXPORTED HOOKS ||============================== //

export const useRole = () => {
  const roles = useRBACStore((state) => state.roles);
  return roles.length > 0 ? roles[0] : null;
};

export const useRoles = () => useRBACStore((state) => state.roles);

export const useUser = () => useRBACStore((state) => state.user);



/**
 * Primary RBAC hook — role-based only, no permissions
 */
export const useRBAC = () => {
  const roles = useRBACStore((state) => state.roles);
  const user = useRBACStore((state) => state.user);
  const isInitialized = useRBACStore((state) => state.isInitialized);
  const hasRoleFn = useRBACStore((state) => state.hasRole);
  const getPrimaryRole = useRBACStore((state) => state.getPrimaryRole);
  const isSuperAdminFn = useRBACStore((state) => state.isSuperAdmin);
  const isEmployerRoleFn = useRBACStore((state) => state.isEmployerRole);
  const isProviderRoleFn = useRBACStore((state) => state.isProviderRole);
  const canManageRbac = useRBACStore((state) => state.canManageRbac);
  const canManageSystem = useRBACStore((state) => state.canManageSystem);
  const canModifyUserWithRole = useRBACStore((state) => state.canModifyUserWithRole);
  const canAssignRoleFn = useRBACStore((state) => state.canAssignRole);
  const getAssignableRolesFn = useRBACStore((state) => state.getAssignableRoles);
  const getPrivilegeLevelFn = useRBACStore((state) => state.getPrivilegeLevel);

  const primaryRole = getPrimaryRole();
  const isSuperAdmin = isSuperAdminFn();
  const isAuthenticated = !!user && isInitialized;

  return {
    roles,
    primaryRole,
    user,
    isInitialized,
    isAuthenticated,
    hasRole: hasRoleFn,
    isSuperAdmin,
    isEmployerRole: isEmployerRoleFn(),
    isProviderRole: isProviderRoleFn(),
    canManageRbac: canManageRbac(),
    canManageSystem: canManageSystem(),
    canModifyUserWithRole,
    canAssignRole: canAssignRoleFn,
    getAssignableRoles: getAssignableRolesFn,
    getPrivilegeLevel: getPrivilegeLevelFn
  };
};

export default useRBACStore;
