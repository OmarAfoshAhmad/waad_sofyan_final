export const resolveMemberRole = (user) =>
  String(user?.userType || user?.role || user?.roles?.[0]?.name || user?.roles?.[0] || '').trim().toUpperCase();

/** Mirrors the backend member command/import/query policy at action level. */
export const getMemberCapabilities = (user) => {
  const role = resolveMemberRole(user);
  const superAdmin = role === 'SUPER_ADMIN';
  const employerAdmin = role === 'EMPLOYER_ADMIN';
  const dataEntry = role === 'DATA_ENTRY';
  return Object.freeze({
    create: superAdmin || employerAdmin || dataEntry,
    edit: superAdmin || employerAdmin || dataEntry,
    lifecycle: superAdmin || employerAdmin,
    hardDelete: superAdmin,
    bulkTerminate: superAdmin || employerAdmin,
    import: superAdmin || dataEntry,
    export: superAdmin || employerAdmin
  });
};
