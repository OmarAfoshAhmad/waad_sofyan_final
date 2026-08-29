/**
 * The permission each member action answers to, mirroring
 * MemberOperationPermissions on the server.
 *
 * The two lists are pinned together by test rather than by discipline. They
 * drifted once already: the server decided by role name while this file read
 * the permission catalogue, so a granted permission drew a button that the
 * server then refused. A capability here that names a permission the server
 * does not require -- or misses one it does -- is the same defect returning.
 */
export const MEMBER_CAPABILITY_PERMISSIONS = Object.freeze({
  create: 'MEMBER_CREATE',
  edit: 'MEMBER_EDIT_IDENTITY',
  transfer: 'MEMBER_TRANSFER_EMPLOYER',
  lifecycle: 'MEMBER_CHANGE_STATUS',
  reinstateTerminated: 'MEMBER_REINSTATE_TERMINATED',
  hardDelete: 'MEMBER_HARD_DELETE',
  bulkTerminate: 'MEMBER_CHANGE_STATUS',
  import: 'MEMBER_IMPORT',
  export: 'MEMBER_EXPORT',
  // One member's ceiling, opened from the drawer.
  viewLimits: 'MEMBER_LIMIT_VIEW',
  // A page of them: the list column, which is a different grant.
  viewLimitsList: 'MEMBER_LIMIT_LIST_VIEW',
  // Granting or ending an exceptional increase. A write, and neither of the
  // two reads above carries it.
  manageLimitUplift: 'MEMBER_LIMIT_UPLIFT_MANAGE'
});

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
    viewLimits: permissions.has('MEMBER_LIMIT_VIEW'),
    // Gates the ceiling column. The server refuses the bulk read without this
    // exact permission, so the column is absent rather than present-and-failing.
    viewLimitsList: permissions.has('MEMBER_LIMIT_LIST_VIEW'),
    manageLimitUplift: permissions.has('MEMBER_LIMIT_UPLIFT_MANAGE')
  });
};
