/** Mirrors the backend effective-permission checks at action level. */
export const getMemberCapabilities = (user) => {
  const permissions = new Set(user?.permissions || []);
  return Object.freeze({
    create: permissions.has('MEMBER_CREATE'),
    edit: permissions.has('MEMBER_EDIT_IDENTITY'),
    transfer: permissions.has('MEMBER_TRANSFER_EMPLOYER'),
    lifecycle: permissions.has('MEMBER_CHANGE_STATUS'),
    reinstateTerminated: permissions.has('MEMBER_REINSTATE_TERMINATED'),
    hardDelete: permissions.has('MEMBER_HARD_DELETE'),
    bulkTerminate: permissions.has('MEMBER_CHANGE_STATUS'),
    import: permissions.has('MEMBER_IMPORT'),
    export: permissions.has('MEMBER_EXPORT'),
    // Gates the ceiling column. The backend refuses the bulk read without
    // this permission anyway; hiding the column keeps a user who lacks it
    // from meeting a request that can only fail.
    viewLimits: permissions.has('MEMBER_LIMIT_VIEW')
  });
};
