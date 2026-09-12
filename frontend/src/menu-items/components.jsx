// material-ui icons
import {
  Dashboard as DashboardIcon,
  Business as BusinessIcon,
  LocalHospital as LocalHospitalIcon,
  Receipt as ReceiptIcon,
  Description as DescriptionIcon,
  PeopleAlt as PeopleAltIcon,
  Category as CategoryIcon,
  Assignment as AssignmentIcon,
  Settings as SettingsIcon,
  Assessment as AssessmentIcon,
  Inbox as InboxIcon,
  Payment as PaymentIcon,
  Policy as PolicyIcon,
  Handshake as HandshakeIcon,
  Security as SecurityIcon,
  HowToReg as HowToRegIcon,
  FormatListBulleted as FormatListBulletedIcon,
  Folder as FolderIcon,
  VerifiedUser as VerifiedUserIcon,
  History as HistoryIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon,
  MenuBook as MenuBookIcon,
  ManageSearch as ManageSearchIcon
} from '@mui/icons-material';

// ═══════════════════════════════════════════════════════════════════════════════
// MENU VISIBILITY
// ═══════════════════════════════════════════════════════════════════════════════
//
// Two kinds of item, decided by what the SERVER checks on the screen's
// primary endpoint (docs/security/MENU_PERMISSION_DERIVATION.md):
//
//  1. Permission-gated on the server (@permissionGuard.has('X')): the item
//     declares requiredPermission(s) and is shown iff user.permissions --
//     the effective set from /session/me, role defaults ± per-user
//     overrides -- satisfies them. The role map is NOT consulted, so an
//     override granted in RBAC reveals the screen and a revoked one hides it.
//
//  2. Role-gated on the server (hasAnyRole(...)): no permission exists to
//     derive, so the item stays on ROLE_RESOURCE_ACCESS until the endpoint
//     migrates. Both gates together would be wrong: a granted override
//     could never reveal a screen still fenced by role here.
//
// ═══════════════════════════════════════════════════════════════════════════════

import { ROLE_RESOURCE_ACCESS } from 'config/roleAccessMap';

/**
 * @param {Array} items - Menu items to filter
 * @param {string} role - User's canonical role (e.g. 'SUPER_ADMIN')
 * @param {boolean} providerPortalEnabled - Whether the provider portal is enabled
 * @param {boolean} batchClaimsEnabled - Whether legacy/monthly batch intake is enabled
 * @param {string[]} permissions - user.permissions from /session/me (effective set)
 * @returns {Array} Filtered menu items visible to this user
 */
export const filterMenuItemsByRole = (items, role, providerPortalEnabled = false, batchClaimsEnabled = true, permissions = []) => {
  const allowedResources = ROLE_RESOURCE_ACCESS[role] || [];
  const effectivePermissions = new Set(permissions || []);

  const declaresPermissions = (item) => Boolean(item?.requiredPermission || item?.requiredPermissions);

  const permissionsSatisfied = (item) => {
    if (item?.requiredPermission && !effectivePermissions.has(item.requiredPermission)) return false;
    if (item?.requiredPermissions) {
      return item.requireAllPermissions === false
        ? item.requiredPermissions.some((permission) => effectivePermissions.has(permission))
        : item.requiredPermissions.every((permission) => effectivePermissions.has(permission));
    }
    return true;
  };

  const isAllowed = (resource, item) => {
    if (item?.featureFlag === 'BATCH_CLAIMS_ENABLED' && !batchClaimsEnabled) return false;
    if (!resource) return true; // group headers without resource → always visible
    if (resource === 'provider_portal' && !providerPortalEnabled) return false;
    if (resource.startsWith('__hidden_')) return false; // Explicitly hidden items
    // Containers are decided by their children: one that ends up empty is
    // pruned below. A resource gate on the container itself only ever
    // subtracted -- it hid a section from a role whose override had just
    // granted the permission on a screen inside it.
    if (item?.type === 'group' || item?.type === 'collapse') return true;
    if (declaresPermissions(item)) return permissionsSatisfied(item);
    if (allowedResources.includes('*')) return true; // SUPER_ADMIN wildcard
    return allowedResources.includes(resource);
  };

  return items
    .filter((item) => isAllowed(item.resource, item))
    .map((item) => ({
      ...item,
      children: item.children ? filterMenuItemsByRole(item.children, role, providerPortalEnabled, batchClaimsEnabled, permissions) : undefined
    }))
    .filter((item) => {
      // Remove groups/collapses with no visible children
      if ((item.type === 'group' || item.type === 'collapse') && item.children) {
        return item.children.length > 0;
      }
      return true;
    });
};

// ═══════════════════════════════════════════════════════════════════════════════
// MENU ITEMS (Static Role → Resource Map)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 PROFESSIONAL TPA SYSTEM - NAVIGATION MENU (2026 STANDARD)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * DESIGN PHILOSOPHY:
 * ✅ Professional TPA Industry Standards
 * ✅ Visibility derived from server permissions (docs/security/MENU_PERMISSION_DERIVATION.md)
 * ✅ Future-proof structure
 * ✅ Role map only for screens the server still gates by role
 *
 * NAVIGATION STRUCTURE:
 * 📊 Dashboard          → resource: 'dashboard'
 * 👥 Members            → resource: 'members'
 * 🏥 Provider Portal    → resource: 'provider_portal'
 * 🏢 Employers          → resource: 'employers'
 * 🏥 Providers          → resource: 'providers'
 * 💰 Claims & Approvals → resource: 'claims', 'pre_auth'
 * 💰 Settlements        → resource: 'settlements'
 * 📈 Reports            → resource: 'report_*'
 * 📂 Documents          → resource: 'documents'
 * ⚙️ System Settings    → resource: 'system_settings', 'users', 'audit_logs'
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */

const menuItem = [
  // ═══════════════════════════════════════════════════════════════════════════
  // 📊 DASHBOARD
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-statistics',
    title: 'لوحة المعلومات',
    titleEn: 'Dashboard',
    type: 'group',
    children: [
      {
        id: 'dashboard',
        title: 'لوحة المعلومات',
        titleEn: 'Dashboard',
        type: 'item',
        url: '/dashboard',
        icon: AssessmentIcon,
        resource: 'dashboard',
        action: 'view',
        breadcrumbs: false,
        chip: {
          label: '📊',
          color: 'info',
          size: 'small',
          variant: 'outlined'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 👥 MEMBERS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-members',
    title: 'المستفيدين',
    titleEn: 'Insured',
    type: 'group',
    children: [
      {
        id: 'members-list',
        title: 'قائمة المستفيدين',
        titleEn: 'Insured List',
        type: 'item',
        url: '/members',
        // Derived from the server's @PreAuthorize on this screen's primary endpoint
        // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
        requiredPermissions: ['MEMBER_VIEW'],
        icon: PeopleAltIcon,
        resource: 'members',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏥 PROVIDER PORTAL (VISIT-CENTRIC FLOW)
  // For Provider Staff only
  // ═══════════════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-provider-portal',
    title: 'بوابة مقدم الخدمة',
    titleEn: 'Provider Portal',
    type: 'group',
    children: [
      {
        id: 'provider-portal',
        title: 'بوابة مقدم الخدمة',
        titleEn: 'Provider Portal',
        type: 'collapse',
        icon: LocalHospitalIcon,
        resource: 'provider_portal',
        action: 'view',
        children: [
          {
            id: 'provider-eligibility-check',
            title: 'التحقق من الأهلية',
            titleEn: 'Eligibility Check',
            type: 'item',
            url: '/provider/eligibility-check',
            icon: HowToRegIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '1️⃣',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'provider-visit-log',
            title: 'سجل الزيارات',
            titleEn: 'Visit Log',
            type: 'item',
            url: '/provider/visits',
            icon: AssignmentIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '2️⃣',
              color: 'info',
              size: 'small'
            }
          },
          {
            id: 'provider-documents',
            title: 'المستندات',
            titleEn: 'Documents',
            type: 'item',
            url: '/provider/documents',
            icon: FolderIcon,
            resource: 'provider_portal',
            action: 'view',
            chip: {
              label: '3️⃣',
              color: 'secondary',
              size: 'small'
            }
          },
          {
            id: 'provider-reports-divider',
            type: 'divider'
          },
          {
            id: 'provider-claims-report',
            title: 'تقرير المطالبات',
            titleEn: 'Claims Report',
            type: 'item',
            url: '/provider/reports/claims',
            icon: ReceiptIcon,
            resource: 'provider_portal',
            action: 'view'
          },
          {
            id: 'provider-preauth-report',
            title: 'تقرير الموافقات',
            titleEn: 'Pre-Auth Report',
            type: 'item',
            url: '/provider/reports/pre-auth',
            icon: VerifiedUserIcon,
            resource: 'provider_portal',
            action: 'view'
          },
          {
            id: 'provider-visits-report',
            title: 'تقرير الزيارات',
            titleEn: 'Visits Report',
            type: 'item',
            url: '/provider/reports/visits',
            icon: AssessmentIcon,
            resource: 'provider_portal',
            action: 'view'
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏢 EMPLOYERS (PARTNERS)
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-employers',
    title: 'جهات العمل',
    titleEn: 'Employers',
    type: 'group',
    children: [
      {
        id: 'employers',
        title: 'إدارة جهات العمل',
        titleEn: 'Employers Management',
        type: 'collapse',
        icon: BusinessIcon,
        resource: 'employers',
        action: 'view',
        children: [
          {
            id: 'employers-list',
            title: 'قائمة جهات العمل',
            titleEn: 'Employers List',
            type: 'item',
            url: '/employers',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['EMPLOYER_VIEW'],
            icon: FormatListBulletedIcon,
            resource: 'employers',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'benefit-policies',
            title: 'وثائق التأمين',
            titleEn: 'Benefit Policies',
            type: 'item',
            url: '/benefit-policies',
            icon: PolicyIcon,
            resource: 'benefit_policies',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 🏥 PROVIDERS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-providers',
    title: 'مقدمو الخدمات',
    titleEn: 'Providers',
    type: 'group',
    children: [
      {
        id: 'providers',
        title: 'إدارة مقدمي الخدمات',
        titleEn: 'Providers Management',
        type: 'collapse',
        icon: LocalHospitalIcon,
        resource: 'providers',
        action: 'view',
        children: [
          {
            id: 'providers-list',
            title: 'قائمة المقدمين',
            titleEn: 'Providers List',
            type: 'item',
            url: '/providers',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['PROVIDER_VIEW'],
            icon: FormatListBulletedIcon,
            resource: 'providers',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'provider-contracts',
            title: 'عقود مقدمي الخدمات',
            titleEn: 'Provider Contracts',
            type: 'item',
            url: '/provider-contracts',
            icon: HandshakeIcon,
            resource: 'provider_contracts',
            action: 'view',
            requiredPermission: 'CONTRACT_VIEW',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          }
          // hidden: إدارة حسابات المقدمين
          // { id: 'provider-users', title: 'إدارة حسابات المقدمين', ... }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 💰 CLAIMS & APPROVALS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-claims-approvals',
    title: 'المطالبات والموافقات',
    titleEn: 'Claims & Approvals',
    type: 'group',
    children: [
      {
        id: 'claims-approvals',
        title: 'مراجعة المطالبات والموافقات',
        titleEn: 'Review Claims & Approvals',
        type: 'collapse',
        icon: ReceiptIcon,
        resource: 'claims',
        action: 'view',
        children: [
          // NOTE: Claims/Pre-Auth creation happens ONLY from Provider Portal (Visit-Based Flow)
          // Admin panel has NO direct creation - only review and processing
          {
            id: 'claims-batches',
            title: 'نظام الدفعات (Batches)',
            titleEn: 'Claims Batch System',
            type: 'item',
            url: '/claims/batches',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['CLAIM_VIEW'],
            icon: FolderIcon,
            resource: 'claims',
            action: 'view',
            featureFlag: 'BATCH_CLAIMS_ENABLED'
          },
          {
            id: 'preauth-inbox',
            title: 'مراجعة الموافقات (Pre-Auth)',
            titleEn: 'Pre-Auth Review',
            type: 'item',
            url: '/pre-approvals/inbox',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['PREAUTH_REVIEW'],
            icon: InboxIcon,
            resource: 'pre_auth',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'claims-report',
            title: 'تقرير المطالبات (مراجعة)',
            titleEn: 'Claims Report',
            type: 'item',
            url: '/reports/claims',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['CLAIM_VIEW'],
            icon: AssessmentIcon,
            resource: 'claims',
            action: 'view',
            chip: {
              label: 'تقرير',
              color: 'info',
              size: 'small'
            }
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 💰 SETTLEMENT MODULE
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-settlement',
    title: 'التسويات المالية',
    titleEn: 'Financial Settlement',
    type: 'group',
    children: [
      {
        id: 'settlement',
        title: 'إدارة التسويات',
        titleEn: 'Settlement Management',
        type: 'collapse',
        icon: PaymentIcon,
        resource: 'settlements',
        action: 'view',
        children: [
          {
            id: 'provider-accounts',
            title: 'مطالبات التسوية لمقدمي الخدمة',
            titleEn: 'Provider Settlement Claims',
            type: 'item',
            url: '/settlement/provider-accounts',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['SETTLEMENT_VIEW'],
            icon: BusinessIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'معدل',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'provider-payments',
            title: 'الدفعات المالية لمقدمي الخدمة',
            titleEn: 'Provider Financial Payments',
            type: 'item',
            url: '/settlement/provider-payments',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['SETTLEMENT_VIEW'],
            icon: AccountBalanceWalletIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'payments-management',
            title: 'إدارة الدفعات والتسديدات',
            titleEn: 'Payments Management',
            type: 'item',
            url: '/settlement/payments',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['SETTLEMENT_VIEW'],
            icon: AccountBalanceWalletIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'provider-payment-reconciliation',
            title: 'مطابقة ودفعات مقدمي الخدمة (تجريبي)',
            titleEn: 'Provider Payment Reconciliation (Preview)',
            type: 'item',
            url: '/settlement/reconciliation',
            // Derived from the server's @PreAuthorize on this screen's primary endpoint
            // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
            requiredPermissions: ['SETTLEMENT_VIEW'],
            icon: AccountBalanceWalletIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'تجريبي',
              color: 'warning',
              size: 'small'
            }
          },
          {
            id: 'financial-consolidation',
            title: 'الخلاصة المالية المجمعة',
            titleEn: 'Financial Consolidation',
            type: 'item',
            url: '/reports/financial-consolidation',
            icon: AssessmentIcon,
            resource: 'report_provider_settlement',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'secondary',
              size: 'small'
            }
          },
          {
            id: 'accountant-profit',
            title: 'تقرير أرباح الخصومات',
            titleEn: 'Accountant Profit Report',
            type: 'item',
            url: '/reports/accountant-profit',
            icon: AssessmentIcon,
            resource: 'report_provider_settlement',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'success',
              size: 'small'
            }
          }
        ]
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // 📂 DOCUMENTS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-documents',
    title: 'الوثائق',
    titleEn: 'Documents',
    type: 'group',
    children: [
      {
        id: 'documents-library',
        title: 'مكتبة الوثائق',
        titleEn: 'Documents Library',
        type: 'item',
        url: '/documents',
        icon: DescriptionIcon,
        resource: '__hidden_documents', // Hidden per user request
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      }
    ]
  },

  // ═══════════════════════════════════════════════════════════════════════════
  // ⚙️ SYSTEM SETTINGS
  // ═══════════════════════════════════════════════════════════════════════════
  {
    id: 'group-system-settings',
    title: 'إعدادات النظام',
    titleEn: 'System Settings',
    type: 'group',
    children: [
      {
        id: 'users-management',
        title: 'إدارة المستخدمين',
        titleEn: 'User Management',
        type: 'item',
        url: '/admin/users',
        // Derived from the server's @PreAuthorize on this screen's primary endpoint
        // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
        requiredPermissions: ['USER_VIEW'],
        icon: SecurityIcon,
        resource: 'users',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'medical-categories',
        title: 'إدارة التصنيفات',
        titleEn: 'Manage Categories',
        type: 'item',
        url: '/medical-categories',
        icon: CategoryIcon,
        resource: 'medical_catalog',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'medical-dictionary',
        title: 'القاموس الطبي',
        titleEn: 'Medical Dictionary',
        type: 'item',
        url: '/medical-dictionary',
        icon: MenuBookIcon,
        resource: 'medical_catalog',
        action: 'view',
        requiredPermission: 'PRICE_LIST_IMPORT',
        chip: {
          label: '🧠',
          color: 'info',
          size: 'small'
        }
      },
      {
        id: 'price-list-classifier',
        title: 'تنظيم قوائم الأسعار',
        titleEn: 'Price List Classifier',
        type: 'item',
        url: '/price-list-classifier',
        icon: ManageSearchIcon,
        resource: 'medical_catalog',
        action: 'view',
        requiredPermission: 'PRICE_LIST_IMPORT',
        chip: {
          label: 'Excel',
          color: 'warning',
          size: 'small',
          variant: 'outlined'
        }
      },
      {
        id: 'price-list-sessions',
        title: 'القوائم المصنفة',
        titleEn: 'Classified Price Lists',
        type: 'item',
        url: '/price-list-sessions',
        icon: HistoryIcon,
        resource: 'medical_catalog',
        action: 'view',
        requiredPermissions: ['PRICE_LIST_IMPORT', 'PRICE_LIST_POST'],
        requireAllPermissions: false,
        chip: {
          label: 'تتبع',
          color: 'info',
          size: 'small',
          variant: 'outlined'
        }
      },
      {
        id: 'system-configuration',
        title: 'تكوين النظام والمؤسسة',
        titleEn: 'System & Organization Configuration',
        type: 'item',
        url: '/settings/system',
        icon: SettingsIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'kinship-mismatch',
        title: 'تصحيح بيانات المستفيدين',
        titleEn: 'Beneficiary Kinship Mismatch',
        type: 'item',
        url: '/settings/kinship-mismatch',
        // Derived from the server's @PreAuthorize on this screen's primary endpoint
        // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
        requiredPermissions: ['SYSTEM_SETTINGS_VIEW'],
        icon: PeopleAltIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: 'جديد',
          color: 'primary',
          size: 'small'
        }
      },
      {
        id: 'medical-audit-logs',
        title: 'سجل التدقيق الطبي',
        titleEn: 'Medical Audit Logs',
        type: 'item',
        url: '/admin/users/medical-audit-logs',
        icon: HistoryIcon,
        resource: 'users',
        action: 'view',
        chip: {
          label: 'جديد',
          color: 'warning',
          size: 'small'
        }
      },
      {
        id: 'member-duplicates',
        title: 'دمج السجلات المكررة',
        titleEn: 'Member Duplicates Resolver',
        type: 'item',
        url: '/settings/member-duplicates',
        // Derived from the server's @PreAuthorize on this screen's primary endpoint
        // (docs/security/MENU_PERMISSION_DERIVATION.md). Decides visibility on its own.
        requiredPermissions: ['SYSTEM_SETTINGS_VIEW', 'DANGER_ZONE_EXECUTE'],
        icon: PeopleAltIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: 'هام',
          color: 'error',
          size: 'small'
        }
      }
    ]
  }
];

export default menuItem;


