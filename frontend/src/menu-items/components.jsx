// material-ui icons
import {
  Dashboard as DashboardIcon,
  Business as BusinessIcon,
  LocalHospital as LocalHospitalIcon,
  Receipt as ReceiptIcon,
  ReceiptLong as ReceiptLongIcon,
  Description as DescriptionIcon,
  PeopleAlt as PeopleAltIcon,
  MedicalServices as MedicalServicesIcon,
  Category as CategoryIcon,
  Inventory as InventoryIcon,
  Assignment as AssignmentIcon,
  Settings as SettingsIcon,
  Assessment as AssessmentIcon,
  Inbox as InboxIcon,
  Payment as PaymentIcon,
  Policy as PolicyIcon,
  Handshake as HandshakeIcon,
  Security as SecurityIcon,
  Timeline as TimelineIcon,
  HowToReg as HowToRegIcon,
  FormatListBulleted as FormatListBulletedIcon,
  Folder as FolderIcon,
  VerifiedUser as VerifiedUserIcon,
  AssignmentInd as AssignmentIndIcon,
  History as HistoryIcon,
  PriceCheck as PriceCheckIcon,
  BarChart as BarChartIcon,
  ErrorOutline as ErrorOutlineIcon
} from '@mui/icons-material';

// ═══════════════════════════════════════════════════════════════════════════════
// ROLE-BASED MENU FILTERING — Static ROLE_RESOURCE_ACCESS Map
// ═══════════════════════════════════════════════════════════════════════════════
//
// ARCHITECTURE (2026-02-18):
// - Each menu item has: resource (string)
// - ROLE_RESOURCE_ACCESS maps each role to its allowed resources
// - SUPER_ADMIN gets '*' → sees everything
// - No can(), no action-level checks, no permission matrix
//
// ═══════════════════════════════════════════════════════════════════════════════

import { ROLE_RESOURCE_ACCESS } from 'config/roleAccessMap';

/**
 * Filter menu items based on static Role → Resource map.
 *
 * @param {Array} items - Menu items to filter
 * @param {string} role - User's canonical role (e.g. 'SUPER_ADMIN')
 * @returns {Array} Filtered menu items visible to specified role
 */
export const filterMenuItemsByRole = (items, role) => {
  const allowedResources = ROLE_RESOURCE_ACCESS[role] || [];

  const isAllowed = (resource) => {
    if (!resource) return true; // group headers without resource → always visible
    if (resource.startsWith('__hidden_')) return false; // Explicitly hidden items
    if (allowedResources.includes('*')) return true; // SUPER_ADMIN wildcard
    return allowedResources.includes(resource);
  };

  return items
    .filter((item) => isAllowed(item.resource))
    .map((item) => ({
      ...item,
      children: item.children
        ? filterMenuItemsByRole(item.children, role)
        : undefined
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
 * ✅ Static ROLE_RESOURCE_ACCESS map drives visibility (see config/roleAccessMap.js)
 * ✅ Future-proof structure
 * ✅ No can(), no action-level checks, no permission matrix
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
    title: 'الإحصائيات والتقارير المالية',
    titleEn: 'Statistics & Analytics',
    type: 'group',
    children: [
      {
        id: 'dashboard',
        title: 'لوحة الإحصائيات',
        titleEn: 'Statistics Dashboard',
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
        resource: 'provider_portal', // controlled by PROVIDER_PORTAL_ENABLED feature flag
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
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'provider-users',
            title: 'إدارة حسابات المقدمين',
            titleEn: 'Provider Account Managers',
            type: 'item',
            url: '/admin/users?role=PROVIDER_STAFF',
            icon: PeopleAltIcon,
            resource: 'users',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'info',
              size: 'small'
            }
          }
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
            icon: FolderIcon,
            resource: 'claims',
            action: 'view',
          },
          {
            id: 'claims-report',
            title: 'تقرير المطالبات (مراجعة)',
            titleEn: 'Claims Report',
            type: 'item',
            url: '/reports/claims',
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
            title: 'حسابات مقدمي الخدمة',
            titleEn: 'Provider Accounts',
            type: 'item',
            url: '/settlement/provider-accounts',
            icon: BusinessIcon,
            resource: 'provider_accounts',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'settlement-batches',
            title: 'دفعات التسوية',
            titleEn: 'Settlement Batches',
            type: 'item',
            url: '/settlement/batches',
            icon: ReceiptLongIcon,
            resource: 'settlements',
            action: 'view',
            chip: {
              label: 'جديد',
              color: 'primary',
              size: 'small'
            }
          },
          {
            id: 'payment-center',
            title: 'مركز الدفع',
            titleEn: 'Payment Center',
            type: 'item',
            url: '/settlement/payments',
            icon: PriceCheckIcon,
            resource: 'payments',
            action: 'view'
          },
          {
            id: 'settlement-summary-report',
            title: 'كشف حساب المزودين (له/عليه)',
            titleEn: 'Provider Account Summary',
            type: 'item',
            url: '/reports/provider-settlement-summary',
            icon: BarChartIcon,
            resource: 'report_provider_settlement',
            action: 'view',
            chip: {
              label: 'محاسبة',
              color: 'error',
              size: 'small'
            }
          },
          {
            id: 'rejections-report',
            title: 'تقرير المرفوضات التفصيلي',
            titleEn: 'Detailed Rejections Report',
            type: 'item',
            url: '/reports/rejections',
            icon: ErrorOutlineIcon,
            resource: 'report_provider_settlement',
            action: 'view',
            chip: {
              label: 'مهم',
              color: 'error',
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
        resource: 'documents',
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
        id: 'medical-reviewers-assignment',
        title: 'تعيينات المراجعين الطبيين',
        titleEn: 'Medical Reviewer Assignments',
        type: 'item',
        url: '/admin/medical-reviewers',
        icon: AssignmentIndIcon,
        resource: 'reviewers',
        action: 'manage',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small'
        }
      },
      {
        id: 'audit',
        title: 'سجل التدقيق',
        titleEn: 'Audit Log',
        type: 'item',
        url: '/audit',
        icon: TimelineIcon,
        resource: 'audit_logs',
        action: 'view',
        chip: {
          label: '✅',
          color: 'success',
          size: 'small',
          variant: 'outlined'
        }
      },
      {
        id: 'medical-taxonomy',
        title: 'التصنيف الطبي',
        titleEn: 'Medical Taxonomy',
        type: 'collapse',
        icon: MedicalServicesIcon,
        resource: 'medical_catalog',
        action: 'view',
        children: [
          {
            id: 'medical-catalog',
            title: 'الكتالوج الطبي الموحد',
            titleEn: 'Medical Catalog',
            type: 'item',
            url: '/medical-catalog',
            icon: MedicalServicesIcon,
            resource: 'medical_catalog',
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
            action: 'view'
          },
          {
            id: 'medical-packages',
            title: 'الحزم الطبية',
            titleEn: 'Medical Packages',
            type: 'item',
            url: '/medical-packages',
            icon: InventoryIcon,
            resource: 'medical_packages',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          }
        ]
      },
      {
        id: 'cities-networks',
        title: 'المدن والشبكات',
        titleEn: 'Cities & Networks',
        type: 'item',
        url: '/under-development',
        icon: BusinessIcon,
        resource: 'system_settings',
        action: 'view',
        chip: {
          label: '⏳',
          color: 'warning',
          size: 'small'
        }
      },
      {
        id: 'settings',
        title: 'إعدادات عامة',
        titleEn: 'General Settings',
        type: 'collapse',
        icon: SettingsIcon,
        resource: 'system_settings',
        action: 'view',
        children: [
          {
            id: 'company-settings',
            title: 'معلومات المؤسسة',
            titleEn: 'Organization Information',
            type: 'item',
            url: '/settings/company',
            icon: BusinessIcon,
            resource: 'system_settings',
            action: 'view',
            chip: {
              label: '✅',
              color: 'success',
              size: 'small'
            }
          },
          {
            id: 'system-configuration',
            title: 'تكوين النظام',
            titleEn: 'System Configuration',
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
          }
        ]
      }
    ]
  }
];

export default menuItem;
