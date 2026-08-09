/**
 * System Role Definitions — Phase 5 (Static Role-Based Auth)
 *
 * AUTHORITATIVE source for role constants in frontend.
 * Mirrors backend SystemRole enum exactly (9 roles).
 *
 * ARCHITECTURE (2026-02-18):
 * - Backend removed dynamic RBAC (permissions table, roles table)
 * - Each user has exactly ONE role stored in user.userType
 * - Login returns: { roles: ["SUPER_ADMIN"], permissions: [] }
 * - All authorization is role-based — no permission strings
 */

// ============================================
// System Roles (matches backend SystemRole enum)
// ============================================

export const SystemRole = Object.freeze({
  SUPER_ADMIN: 'SUPER_ADMIN',
  MEDICAL_REVIEWER: 'MEDICAL_REVIEWER',
  MEDICAL_REVIEW_HEAD: 'MEDICAL_REVIEW_HEAD',
  INSURANCE_MANAGER: 'INSURANCE_MANAGER',
  ACCOUNTANT: 'ACCOUNTANT',
  PROVIDER_STAFF: 'PROVIDER_STAFF',
  EMPLOYER_ADMIN: 'EMPLOYER_ADMIN',
  DATA_ENTRY: 'DATA_ENTRY',
  FINANCE_VIEWER: 'FINANCE_VIEWER'
});

// ============================================
// Role Display Names (Arabic + English)
// ============================================

export const RoleDisplayNames = Object.freeze({
  [SystemRole.SUPER_ADMIN]: { ar: 'مدير النظام', en: 'Super Admin' },
  [SystemRole.MEDICAL_REVIEWER]: { ar: 'مراجع طبي', en: 'Medical Reviewer' },
  [SystemRole.MEDICAL_REVIEW_HEAD]: { ar: 'رئيس قسم المراجعين', en: 'Medical Review Head' },
  [SystemRole.INSURANCE_MANAGER]: { ar: 'مدير التأمين', en: 'Insurance Manager' },
  [SystemRole.ACCOUNTANT]: { ar: 'محاسب', en: 'Accountant' },
  [SystemRole.PROVIDER_STAFF]: { ar: 'موظف مقدم خدمة', en: 'Provider Staff' },
  [SystemRole.EMPLOYER_ADMIN]: { ar: 'مدير جهة العمل', en: 'Employer Admin' },
  [SystemRole.DATA_ENTRY]: { ar: 'مدخل بيانات', en: 'Data Entry' },
  [SystemRole.FINANCE_VIEWER]: { ar: 'مشاهد مالي', en: 'Finance Viewer' }
});

// ============================================
// Role Privilege Levels (for hierarchy checks)
// ============================================

export const RolePrivilegeLevel = Object.freeze({
  [SystemRole.SUPER_ADMIN]: 999,
  [SystemRole.MEDICAL_REVIEWER]: 80,
  [SystemRole.MEDICAL_REVIEW_HEAD]: 90,
  [SystemRole.INSURANCE_MANAGER]: 95,
  [SystemRole.ACCOUNTANT]: 70,
  [SystemRole.DATA_ENTRY]: 60,
  [SystemRole.FINANCE_VIEWER]: 50,
  [SystemRole.EMPLOYER_ADMIN]: 40,
  [SystemRole.PROVIDER_STAFF]: 30
});

// ============================================
// Utility Functions
// ============================================

export const isSuperAdminRole = (role) => role === SystemRole.SUPER_ADMIN;

export const getPrivilegeLevel = (role) => RolePrivilegeLevel[role] ?? 0;

export const getAssignableRoles = (currentRole) => {
  if (currentRole === SystemRole.SUPER_ADMIN) {
    return Object.values(SystemRole);
  }
  return [];
};

export const canModifyRole = (currentRole, targetRole) => {
  if (currentRole === SystemRole.SUPER_ADMIN) return true;
  return getPrivilegeLevel(currentRole) > getPrivilegeLevel(targetRole);
};

export const getRoleDisplayName = (role, lang = 'ar') => {
  const names = RoleDisplayNames[role];
  return names ? names[lang] : role;
};

export const isProviderRole = (role) => {
  return role === SystemRole.PROVIDER_STAFF;
};

export const isAdminRole = (role) => {
  return role === SystemRole.SUPER_ADMIN;
};

export default {
  SystemRole,
  RoleDisplayNames,
  RolePrivilegeLevel,
  isSuperAdminRole,
  getPrivilegeLevel,
  getAssignableRoles,
  canModifyRole,
  getRoleDisplayName,
  isProviderRole,
  isAdminRole
};
