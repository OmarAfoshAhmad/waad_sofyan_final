import { useMemo } from 'react';
import useAuth from 'hooks/useAuth';
import { useEmployersList } from 'hooks/useEmployers';

/**
 * useEmployerScope Hook
 *
 * Centralized employer scope resolution for all reports.
 * Enforces RBAC rules for employer selection.
 *
 * RBAC Rules:
 * - SUPER_ADMIN / ADMIN → Can select any employer (selector enabled)
 * - EMPLOYER_ADMIN / REVIEWER → Locked to own employer (selector disabled)
 * - PROVIDER → No access (should be blocked by route guard)
 *
 * @param {number|null} selectedEmployerId - User-selected employer (for admin roles)
 * @returns {Object} Employer scope context
 */
export const useEmployerScope = (selectedEmployerId = null) => {
  const { user } = useAuth();

  // Extract user role (handle both single role and roles array)
  const userRole = user?.role || user?.roles?.[0] || '';

  // RBAC: Determine access level
  const isAdminRole = useMemo(() => ['SUPER_ADMIN', 'ADMIN'].includes(userRole), [userRole]);

  const isEmployerLocked = useMemo(() => ['EMPLOYER_ADMIN', 'MEDICAL_REVIEWER'].includes(userRole), [userRole]);

  const canSelectEmployer = isAdminRole;

  // Effective employer ID for API calls
  // EMPLOYER_ADMIN/MEDICAL_REVIEWER: Always use their own employer
  // ADMIN/SUPER_ADMIN: Use selected employer or null (all)
  const effectiveEmployerId = useMemo(() => {
    if (isEmployerLocked) {
      return user?.employerId ?? null;
    }
    return selectedEmployerId;
  }, [isEmployerLocked, user?.employerId, selectedEmployerId]);

  // Fetch employers list (for admin selector)
  const { data: employersData, loading: employersLoading } = useEmployersList();

  const employers = useMemo(() => {
    if (!employersData) return [];
    const list = employersData.items ?? employersData.content ?? employersData;
    return Array.isArray(list) ? list : [];
  }, [employersData]);

  // Get current employer name (for display)
  const currentEmployerName = useMemo(() => {
    if (!effectiveEmployerId) return 'جميع الشركاء';
    const employer = employers.find((e) => e.id === effectiveEmployerId);
    return employer?.name ?? `الشريك #${effectiveEmployerId}`;
  }, [effectiveEmployerId, employers]);

  return {
    // User context
    user,
    userRole,

    // Access flags
    isAdminRole,
    isEmployerLocked,
    canSelectEmployer,

    // Employer data
    effectiveEmployerId,
    employers,
    employersLoading,
    currentEmployerName,

    // User's own employer (for locked roles)
    userEmployerId: user?.employerId ?? null
  };
};

export default useEmployerScope;
