import useAuth from 'hooks/useAuth';
import { Navigate } from 'react-router-dom';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

/**
 * Get user's primary role from user object
 */
const getUserRole = (user) => {
  if (!user) return null;
  if (user.role) return user.role.toString().trim().toUpperCase().replace(/\s+/g, '_');
  if (Array.isArray(user.roles) && user.roles.length > 0) {
    const r = user.roles[0];
    return (typeof r === 'string' ? r : r?.name || '').toString().trim().toUpperCase().replace(/\s+/g, '_');
  }
  return null;
};

const isSuperAdminUser = (user) => getUserRole(user) === 'SUPER_ADMIN';

const isProviderUser = (role) => role === 'PROVIDER_STAFF';

/**
 * Permission-aware guard. Capability checks consume the effective permission
 * snapshot returned by the server, so explicit per-user revocations remain
 * authoritative. allowedRoles is retained only for routes not migrated yet.
 *
 * Usage:
 *
 * 1. Route protection (redirect if unauthorized):
 *    <RoleGuard allowedRoles={['SUPER_ADMIN', 'ACCOUNTANT']} isRouteGuard>
 *      <SettlementPage />
 *    </RoleGuard>
 *
 * 2. Component protection (hide if unauthorized):
 *    <RoleGuard allowedRoles={['SUPER_ADMIN']}>
 *      <DeleteButton />
 *    </RoleGuard>
 *
 * 3. Any authenticated user:
 *    <RoleGuard isRouteGuard>
 *      <DashboardPage />
 *    </RoleGuard>
 *
 */
const RoleGuard = ({ allowedRoles, requiredPermission, requiredPermissions, requireAll = true, isRouteGuard = false, children, fallback = null }) => {
  const { user, authStatus } = useAuth();

  if (authStatus === 'INITIALIZING') return null;

  if (!user) {
    return isRouteGuard ? <Navigate to="/login" replace /> : fallback;
  }

  const userRole = getUserRole(user);

  const requestedPermissions = [
    ...(requiredPermission ? [requiredPermission] : []),
    ...(requiredPermissions || [])
  ];
  if (requestedPermissions.length > 0) {
    const effective = new Set(user.permissions || []);
    const allowed = requireAll
      ? requestedPermissions.every((permission) => effective.has(permission))
      : requestedPermissions.some((permission) => effective.has(permission));
    if (allowed) return children;
    if (isRouteGuard) return <Navigate to={getDefaultRouteForRole(userRole)} replace />;
    return fallback;
  }

  // Legacy role guard only. Capability checks above never bypass the effective
  // permission snapshot, including explicit per-user revocations.
  // SUPER_ADMIN bypasses legacy role checks.
  if (isSuperAdminUser(user)) {
    return children;
  }

  // If no specific roles required → any authenticated user can access
  if (!allowedRoles || allowedRoles.length === 0) {
    return children;
  }

  // Check if user's role is in the allowed list
  if (allowedRoles.includes(userRole)) {
    return children;
  }

  // Unauthorized
  if (isRouteGuard) {
    const redirectPath = getDefaultRouteForRole(userRole);
    return <Navigate to={redirectPath} replace />;
  }

  return fallback;
};

/**
 * Hook: check if current user has one of the allowed roles
 * SUPER_ADMIN always returns true.
 */
export const useHasRole = (allowedRoles) => {
  const { user } = useAuth();
  if (!user) return false;
  if (isSuperAdminUser(user)) return true;
  if (!allowedRoles || allowedRoles.length === 0) return true;
  const userRole = getUserRole(user);
  return allowedRoles.includes(userRole);
};

// Default export = RoleGuard
// Also export as PermissionGuard for backward compatibility
export default RoleGuard;
