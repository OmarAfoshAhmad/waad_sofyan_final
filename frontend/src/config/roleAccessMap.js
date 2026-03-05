/**
 * ROLE_RESOURCE_ACCESS — Static Role → Resource Visibility Map
 *
 * AUTHORITATIVE source for which menu resources each role can see.
 * '*' means all resources (SUPER_ADMIN only).
 *
 * This map is consumed by filterMenuItemsByRole() in menu-items/components.jsx
 * and drives the entire sidebar/navigation visibility.
 *
 * RULES:
 * - Backend enforces actual data access — this is UI visibility only.
 * - PROVIDER_STAFF only sees 'provider_portal' — no extra runtime scoping needed.
 * - Add new resources here when adding new menu items.
 */
export const ROLE_RESOURCE_ACCESS = Object.freeze({
  SUPER_ADMIN: ['*'],

  MEDICAL_REVIEWER: [
    'claims',
    'pre_auth',
    'approvals_dashboard',
    'documents',
    'report_claims',
    'report_pre_approvals'
  ],

  ACCOUNTANT: [
    'settlements',
    'provider_accounts',
    'documents',
    'report_financial',
    'report_provider_settlement'
  ],

  PROVIDER_STAFF: [
    'provider_portal'
  ],

  EMPLOYER_ADMIN: [
    'members',
    'benefit_policies',
    'documents',
    'report_employers',
    'report_beneficiaries',
    'report_benefit_policy'
  ],

  DATA_ENTRY: [
    'members',
    'employers',
    'providers',
    'claims',
    'documents',
    'provider_mapping',
    'medical_catalog'
  ],

  FINANCE_VIEWER: [
    'report_financial',
    'report_provider_settlement'
  ]
});

export default ROLE_RESOURCE_ACCESS;
