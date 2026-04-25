import { lazy } from 'react';
import { Outlet, Navigate } from 'react-router-dom';

// project imports
import Loadable from 'components/Loadable';
import SidebarLayout from 'layout/SidebarLayout';
import PermissionGuard from 'components/PermissionGuard';
import ProviderPortalGuard from 'components/guards/ProviderPortalGuard';

// Contexts - Phase D2.3 Table Refresh
import { TableRefreshLayout, TableRefreshProvider } from 'contexts/TableRefreshContext';

// ==============================|| LAZY LOADING - DASHBOARD ||============================== //

const Dashboard = Loadable(lazy(() => import('pages/dashboard')));

// ==============================|| LAZY LOADING - MEMBERS (UNIFIED ARCHITECTURE) ||============================== //
// 🆕 Unified Members Architecture - Self-referencing Member entity (Principal + Dependents)
// Replaces legacy Member + FamilyMember anti-pattern

const UnifiedMembersList = Loadable(lazy(() => import('pages/members/UnifiedMembersList')));
const UnifiedMemberCreate = Loadable(lazy(() => import('pages/members/UnifiedMemberCreate')));
const UnifiedMemberView = Loadable(lazy(() => import('pages/members/UnifiedMemberView')));
const UnifiedMemberEdit = Loadable(lazy(() => import('pages/members/UnifiedMemberEdit')));
const AddDependent = Loadable(lazy(() => import('pages/members/AddDependent')));
const EligibilityCheck = Loadable(lazy(() => import('pages/members/EligibilityCheck')));
const EligibilityCheckPage = Loadable(lazy(() => import('pages/eligibility/EligibilityCheckPage')));
const FamilyEligibilityPage = Loadable(lazy(() => import('pages/eligibility/FamilyEligibilityPage')));

// ==============================|| LAZY LOADING - EMPLOYERS ||============================== //

const EmployersList = Loadable(lazy(() => import('pages/employers/EmployersList')));
const EmployerCreate = Loadable(lazy(() => import('pages/employers/EmployerCreate')));
const EmployerEdit = Loadable(lazy(() => import('pages/employers/EmployerEdit')));
const EmployerView = Loadable(lazy(() => import('pages/employers/EmployerView')));

// ==============================|| LAZY LOADING - CLAIMS ||============================== //
// NOTE: Claims creation happens ONLY from Provider Portal (visit-based flow)
// Medical Review and Review List are kept for reviewers to process claims

const ClaimViewMedicalReview = Loadable(lazy(() => import('pages/claims/ClaimViewMedicalReview')));
const ClaimBatchManagement = Loadable(lazy(() => import('pages/claims/batches/ClaimBatchManagement')));
const ClaimBatchEntry = Loadable(lazy(() => import('pages/claims/batches/ClaimBatchEntry')));
const ClaimBatchDetail = Loadable(lazy(() => import('pages/claims/batches/ClaimBatchDetail')));

// ==============================|| LAZY LOADING - PROVIDERS ||============================== //

const ProvidersList = Loadable(lazy(() => import('pages/providers/ProvidersList')));
const ProviderCreate = Loadable(lazy(() => import('pages/providers/ProviderCreate')));
const ProviderEdit = Loadable(lazy(() => import('pages/providers/ProviderEdit')));
const ProviderView = Loadable(lazy(() => import('pages/providers/ProviderView')));

// Provider Portal Reports
const ProviderClaimsReport = Loadable(lazy(() => import('pages/provider/reports/ProviderClaimsReport')));
const ProviderPreAuthReport = Loadable(lazy(() => import('pages/provider/reports/ProviderPreAuthReport')));
const ProviderVisitsReport = Loadable(lazy(() => import('pages/provider/reports/ProviderVisitsReport')));

// ==============================|| LAZY LOADING - PROVIDER CONTRACTS ||============================== //

const ProviderContractsList = Loadable(lazy(() => import('pages/provider-contracts')));
const ProviderContractView = Loadable(lazy(() => import('pages/provider-contracts/ProviderContractView')));
const ProviderContractCreate = Loadable(lazy(() => import('pages/provider-contracts/ProviderContractCreate')));
const ProviderContractEdit = Loadable(lazy(() => import('pages/provider-contracts/ProviderContractEdit')));

// ==============================|| LAZY LOADING - VISITS ||============================== //

const VisitsList = Loadable(lazy(() => import('pages/visits/VisitsList')));
const VisitCreate = Loadable(lazy(() => import('pages/visits/VisitCreate')));
const VisitEdit = Loadable(lazy(() => import('pages/visits/VisitEdit')));
const VisitView = Loadable(lazy(() => import('pages/visits/VisitView')));

// ==============================|| LAZY LOADING - PROVIDER PORTAL ||============================== //

const ProviderEligibilityCheck = Loadable(lazy(() => import('pages/provider/ProviderEligibilityCheck')));
const ProviderClaimsSubmission = Loadable(lazy(() => import('pages/provider/ProviderClaimsSubmission')));
const ProviderPreApprovalSubmission = Loadable(lazy(() => import('pages/provider/ProviderPreApprovalSubmission')));
const ProviderVisitLog = Loadable(lazy(() => import('pages/provider/ProviderVisitLog')));
const ProviderDocuments = Loadable(lazy(() => import('pages/provider/ProviderDocuments')));
const ProviderPreAuthInbox = Loadable(lazy(() => import('pages/provider/PreAuthInbox')));

// ==============================|| POLICIES MODULE REMOVED ||============================== //
// Policy module deleted - NO Policy concept in backend. Use BenefitPolicy only.

// ==============================|| LAZY LOADING - PRE-APPROVALS ||============================== //
// NOTE: Pre-approvals can ONLY be created from Provider Portal (visit-based flow)
// Old PreApprovalCreate and PreApprovalEdit removed - architectural law enforcement

const PreApprovalsList = Loadable(lazy(() => import('pages/pre-approvals/PreApprovalsList')));
const PreApprovalView = Loadable(lazy(() => import('pages/pre-approvals/PreApprovalView')));
const PreAuthAuditPage = Loadable(lazy(() => import('pages/pre-approvals/PreAuthAuditPage')));
const PreAuthDashboard = Loadable(lazy(() => import('pages/pre-approvals/PreAuthDashboard')));
const EmailPreAuthInbox = Loadable(lazy(() => import('pages/pre-approvals/EmailPreAuthInbox')));

// ==============================|| LAZY LOADING - APPROVALS DASHBOARD ||============================== //

// Unified Approvals Dashboard (Restored & Corrected)
// ==============================|| LAZY LOADING - BENEFIT PACKAGES ||============================== //

const BenefitPackagesList = Loadable(lazy(() => import('pages/benefit-packages/BenefitPackagesList')));
const BenefitPackageCreate = Loadable(lazy(() => import('pages/benefit-packages/BenefitPackageCreate')));
const BenefitPackageEdit = Loadable(lazy(() => import('pages/benefit-packages/BenefitPackageEdit')));
const BenefitPackageView = Loadable(lazy(() => import('pages/benefit-packages/BenefitPackageView')));

// ==============================|| LAZY LOADING - BENEFIT POLICIES ||============================== //

const BenefitPoliciesList = Loadable(lazy(() => import('pages/benefit-policies/BenefitPoliciesList')));
const BenefitPolicyView = Loadable(lazy(() => import('pages/benefit-policies/BenefitPolicyView')));
const BenefitPolicyCreate = Loadable(lazy(() => import('pages/benefit-policies/BenefitPolicyCreate')));
const BenefitPolicyEdit = Loadable(lazy(() => import('pages/benefit-policies/BenefitPolicyEdit')));

// ==============================|| LAZY LOADING - MEDICAL CATALOG ||============================== //

const MedicalCategoriesPage = Loadable(lazy(() => import('pages/medical-categories')));
const MedicalCategoryCreate = Loadable(lazy(() => import('pages/medical-categories/MedicalCategoryCreate')));
const MedicalCategoryEdit = Loadable(lazy(() => import('pages/medical-categories/MedicalCategoryEdit')));

// ==============================|| LAZY LOADING - DOCUMENTS ||============================== //

const DocumentsLibrary = Loadable(lazy(() => import('pages/documents/DocumentsLibrary')));

// ==============================|| LAZY LOADING - UNDER DEVELOPMENT ||============================== //

const UnderDevelopment = Loadable(lazy(() => import('pages/under-development')));

// Companies — single TPA mode: redirect to company settings
// No multi-company management needed (single TPA context)

// ==============================|| LAZY LOADING - ADMIN ||============================== //

const AdminUsersList = Loadable(lazy(() => import('pages/rbac/users')));
const AdminUserDetails = Loadable(lazy(() => import('pages/rbac/users/UserDetails')));
const AdminUserCreate = Loadable(lazy(() => import('pages/rbac/users/UserCreate')));
const AdminUserEdit = Loadable(lazy(() => import('pages/rbac/users/UserEdit')));
const AdminMedicalAuditLogs = Loadable(lazy(() => import('pages/admin/MedicalAuditLogs')));
// ==============================|| LAZY LOADING - SETTINGS ||============================== //

const Settings = Loadable(lazy(() => import('pages/settings')));

const SystemSettingsPage = Loadable(lazy(() => import('pages/settings/SystemSettingsPage')));
const FacilityPricePreparationPage = Loadable(lazy(() => import('pages/settings/FacilityPricePreparationPage')));
const AIKeySettingsPage = Loadable(lazy(() => import('pages/settings/AIKeySettingsPage')));

// ==============================|| LAZY LOADING - PROFILE ||============================== //

const ProfileOverview = Loadable(lazy(() => import('pages/profile/ProfileOverview')));
const AccountSettings = Loadable(lazy(() => import('pages/profile/AccountSettings')));

// ==============================|| LAZY LOADING - REPORTS ||============================== //

const ReportsPage = Loadable(lazy(() => import('pages/reports')));
const EmployerDashboard = Loadable(lazy(() => import('pages/reports/employer-dashboard')));
// ProviderDashboard REMOVED (2026-01-14) - No business value, Provider role restricted
const ClaimsReport = Loadable(lazy(() => import('pages/reports/claims')));
const ClaimStatementPreview = Loadable(lazy(() => import('pages/reports/claims/ClaimStatementPreview')));
const PreApprovalsReport = Loadable(lazy(() => import('pages/reports/pre-approvals')));
const VisitsReport = Loadable(lazy(() => import('pages/reports/visits')));
const BenefitPolicyReport = Loadable(lazy(() => import('pages/reports/benefit-policy')));
const BeneficiariesReports = Loadable(lazy(() => import('pages/reports/BeneficiariesReports')));
const FinancialReports = Loadable(lazy(() => import('pages/reports/FinancialReports')));
const ProviderSettlementReport = Loadable(lazy(() => import('pages/reports/ProviderSettlementReport')));


// ==============================|| LAZY LOADING - ERROR PAGES ||============================== //

const NoAccess = Loadable(lazy(() => import('pages/errors/NoAccess')));
const Error403 = Loadable(lazy(() => import('pages/errors/Forbidden403')));
const Error404 = Loadable(lazy(() => import('pages/errors/NotFound404')));
const Error500 = Loadable(lazy(() => import('pages/errors/ServerError500')));

// ==============================|| LAZY LOADING - SETTLEMENT (Phase 3B) ||============================== //
// Batch-based settlement system for provider payments

const ProviderAccountsList = Loadable(lazy(() => import('pages/settlement/ProviderAccountsList')));
const ProviderPaymentsList = Loadable(lazy(() => import('pages/settlement/ProviderPaymentsList')));
const ProviderAccountView = Loadable(lazy(() => import('pages/settlement/ProviderAccountView')));


// ==============================|| MAIN ROUTING ||============================== //

const MainRoutes = {
  path: '/',
  element: <SidebarLayout />,
  children: [
    // Dashboard (Permission-guarded)
    {
      path: 'dashboard',
      element: (
        <PermissionGuard resource="dashboard" action="view" isRouteGuard>
          <Dashboard />
        </PermissionGuard>
      )
    },

    // Members Module - Unified Architecture (Principal + Dependents in same table)
    {
      path: 'members',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <UnifiedMembersList />
            </PermissionGuard>
          )
        },
        {
          path: 'add',
          element: (
            <PermissionGuard isRouteGuard>
              <UnifiedMemberCreate />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <UnifiedMemberView />
            </PermissionGuard>
          )
        },
        {
          path: ':id/edit',
          element: (
            <PermissionGuard isRouteGuard>
              <UnifiedMemberEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id/add-dependent',
          element: (
            <PermissionGuard isRouteGuard>
              <AddDependent />
            </PermissionGuard>
          )
        },
        {
          path: 'eligibility',
          element: (
            <PermissionGuard isRouteGuard>
              <EligibilityCheck />
            </PermissionGuard>
          )
        },
        {
          path: 'family-eligibility',
          element: (
            <PermissionGuard isRouteGuard>
              <FamilyEligibilityPage />
            </PermissionGuard>
          )
        }
      ]
    },

    // Employers Module
    {
      path: 'employers',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <EmployersList />
            </PermissionGuard>
          )
        },
        {
          path: 'create',
          element: (
            <PermissionGuard isRouteGuard>
              <EmployerCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <EmployerEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <EmployerView />
            </PermissionGuard>
          )
        }
      ]
    },

    // ═══════════════════════════════════════════════════════════════════════════
    // Claims Module - Medical Review Only (2026-02-07)
    // ⚠️ ARCHITECTURAL LAW: Claims/Pre-Auth creation happens ONLY from Provider Portal
    //    via Visit-Based Flow. NO direct creation routes in admin panel.
    // Reviewers can ONLY view and process claims created by providers.
    // ═══════════════════════════════════════════════════════════════════════════
    {
      path: 'claims',
      children: [
        // Medical Review Page - For reviewers to process claims
        {
          path: ':id/medical-review',
          element: (
            <PermissionGuard isRouteGuard>
              <ClaimViewMedicalReview />
            </PermissionGuard>
          )
        },
        {
          path: 'batches',
          element: (
            <PermissionGuard isRouteGuard>
              <ClaimBatchManagement />
            </PermissionGuard>
          )
        },
        {
          path: 'batches/entry',
          element: (
            <PermissionGuard isRouteGuard>
              <ClaimBatchEntry />
            </PermissionGuard>
          )
        },
        {
          path: 'batches/detail',
          element: (
            <PermissionGuard isRouteGuard>
              <ClaimBatchDetail />
            </PermissionGuard>
          )
        }
      ]
    },

    // ═══════════════════════════════════════════════════════════════════════════
    // Settlement Module - Updated to use Resource+Action (2026-02-05)
    // Batch-based provider settlement system with permission-based access control
    // ═══════════════════════════════════════════════════════════════════════════
    {
      path: 'settlement',
      children: [
        // Provider Accounts - View balances
        {
          path: 'provider-accounts',
          element: (
            <PermissionGuard resource="provider_accounts" action="view" isRouteGuard>
              <ProviderAccountsList />
            </PermissionGuard>
          )
        },
        {
          path: 'provider-payments',
          element: (
            <PermissionGuard resource="provider_accounts" action="view" isRouteGuard>
              <ProviderPaymentsList />
            </PermissionGuard>
          )
        },
        {
          path: 'provider-payments/:providerId',
          element: (
            <PermissionGuard resource="provider_accounts" action="view" isRouteGuard>
              <ProviderAccountView />
            </PermissionGuard>
          )
        },
      ]
    },

    // Providers Module
    {
      path: 'providers',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <ProvidersList />
            </PermissionGuard>
          )
        },
        {
          path: 'add',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderView />
            </PermissionGuard>
          )
        },
      ]
    },

    // Provider Contracts Module
    {
      path: 'provider-contracts',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderContractsList />
            </PermissionGuard>
          )
        },
        {
          path: 'create',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderContractCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderContractEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderContractView />
            </PermissionGuard>
          )
        }
      ]
    },

    // Visits Module
    {
      path: 'visits',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <VisitsList />
            </PermissionGuard>
          )
        },
        {
          path: 'add',
          element: (
            <PermissionGuard isRouteGuard>
              <VisitCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <VisitEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <VisitView />
            </PermissionGuard>
          )
        }
      ]
    },

    // NOTE: Policies module REMOVED - Use BenefitPolicy only (no Policy concept in backend)

    // ═══════════════════════════════════════════════════════════════════════════
    // 🔒 PRE-APPROVALS MODULE - Permission-Based (2026-02-02)
    // Roles: ACCOUNTANT, MEDICAL_REVIEWER (inbox only), PROVIDER_STAFF (own records via portal)
    // Reviewers can only VIEW and process inbox, not CREATE/EDIT
    // Creation happens via Provider Portal OR /pre-approvals/add for PROVIDER role
    // ═══════════════════════════════════════════════════════════════════════════
    {
      path: 'pre-approvals',
      element: <TableRefreshLayout />,
      children: [
        {
          path: 'email-inbox',
          element: (
            <PermissionGuard resource="pre_auth" action="view" isRouteGuard>
              <EmailPreAuthInbox />
            </PermissionGuard>
          )
        },
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <PreApprovalsList />
            </PermissionGuard>
          )
        },
        // NOTE: 'add' route removed - Pre-approvals created ONLY from Provider Portal visit flow
        {
          path: 'dashboard',
          element: (
            <PermissionGuard isRouteGuard>
              <PreAuthDashboard />
            </PermissionGuard>
          )
        },
        // NOTE: 'edit/:id' route removed - Pre-approvals edited ONLY from Provider Portal
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <PreApprovalView />
            </PermissionGuard>
          )
        },
        {
          path: ':id/audit',
          element: (
            <PermissionGuard isRouteGuard>
              <PreAuthAuditPage />
            </PermissionGuard>
          )
        }
      ]
    },

    // NOTE: Benefit Packages main routes are defined below (line ~674)
    // Medical Categories (for category creation/maintenance workflows)
    {
      path: 'medical-categories',
      element: <TableRefreshLayout />,
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <MedicalCategoriesPage />
            </PermissionGuard>
          )
        },
        {
          path: 'add',
          element: (
            <PermissionGuard isRouteGuard>
              <MedicalCategoryCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <MedicalCategoryEdit />
            </PermissionGuard>
          )
        },
      ]
    },

    // Benefit Packages Module - Wrapped with TableRefreshLayout
    {
      path: 'benefit-packages',
      element: <TableRefreshLayout />,
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPackagesList />
            </PermissionGuard>
          )
        },
        {
          path: 'create',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPackageCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPackageEdit />
            </PermissionGuard>
          )
        },
        {
          path: 'view/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPackageView />
            </PermissionGuard>
          )
        }
      ]
    },

    // Benefit Policies Module (NEW)
    {
      path: 'benefit-policies',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPoliciesList />
            </PermissionGuard>
          )
        },
        {
          path: 'create',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPolicyCreate />
            </PermissionGuard>
          )
        },
        {
          path: 'edit/:id',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPolicyEdit />
            </PermissionGuard>
          )
        },
        {
          path: ':id',
          element: (
            <PermissionGuard isRouteGuard>
              <BenefitPolicyView />
            </PermissionGuard>
          )
        }
      ]
    },

    // Eligibility Check Module (Unified - Card Number & Barcode Only)
    {
      path: 'eligibility',
      element: (
        <PermissionGuard isRouteGuard>
          <EligibilityCheckPage />
        </PermissionGuard>
      )
    },

    // Provider Portal Module (Healthcare Provider Interface)
    {
      path: 'provider',
      element: (
        <ProviderPortalGuard>
          <Outlet />
        </ProviderPortalGuard>
      ),
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderEligibilityCheck />
            </PermissionGuard>
          )
        },
        {
          path: 'eligibility-check',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderEligibilityCheck />
            </PermissionGuard>
          )
        },
        {
          path: 'visits',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderVisitLog />
            </PermissionGuard>
          )
        },
        {
          path: 'pre-auth-inbox',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderPreAuthInbox />
            </PermissionGuard>
          )
        },
        {
          path: 'claims/submit',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderClaimsSubmission />
            </PermissionGuard>
          )
        },
        {
          path: 'pre-approvals/submit',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderPreApprovalSubmission />
            </PermissionGuard>
          )
        },
        {
          path: 'documents',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderDocuments />
            </PermissionGuard>
          )
        },
        {
          path: 'reports/claims',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderClaimsReport />
            </PermissionGuard>
          )
        },
        {
          path: 'reports/pre-auth',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderPreAuthReport />
            </PermissionGuard>
          )
        },
        {
          path: 'reports/visits',
          element: (
            <PermissionGuard isRouteGuard>
              <ProviderVisitsReport />
            </PermissionGuard>
          )
        }
      ]
    },

    // Companies — single TPA mode: redirect to company settings page
    {
      path: 'companies',
      element: <Navigate to="/settings/system" replace />
    },

    // Admin Module
    {
      path: 'admin',
      children: [
        {
          path: 'users',
          element: <TableRefreshLayout />,
          children: [
            {
              path: '',
              element: (
                <PermissionGuard isRouteGuard>
                  <AdminUsersList />
                </PermissionGuard>
              )
            },
            {
              path: 'create',
              element: (
                <PermissionGuard isRouteGuard>
                  <AdminUserCreate />
                </PermissionGuard>
              )
            },
            {
              path: ':id',
              element: (
                <PermissionGuard isRouteGuard>
                  <AdminUserDetails />
                </PermissionGuard>
              )
            },
            {
              path: ':id/edit',
              element: (
                <PermissionGuard isRouteGuard>
                  <AdminUserEdit />
                </PermissionGuard>
              )
            },
            {
              path: 'medical-audit-logs',
              element: (
                <PermissionGuard isRouteGuard>
                  <AdminMedicalAuditLogs />
                </PermissionGuard>
              )
            }
          ]
        },
      ]
    },

    // Settings
    {
      path: 'settings',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard isRouteGuard>
              <Settings />
            </PermissionGuard>
          )
        },
        {
          path: 'company',
          element: <Navigate to="/settings/system" replace />
        },
        {
          path: 'system',
          element: (
            <PermissionGuard isRouteGuard>
              <SystemSettingsPage />
            </PermissionGuard>
          )
        },
        {
          path: 'facility-price-preparation',
          element: (
            <PermissionGuard isRouteGuard>
              <FacilityPricePreparationPage />
            </PermissionGuard>
          )
        },
        {
          path: 'ai-key',
          element: (
            <PermissionGuard isRouteGuard>
              <AIKeySettingsPage />
            </PermissionGuard>
          )
        }
      ]
    },

    // Profile
    {
      path: 'profile',
      children: [
        {
          path: '',
          element: <ProfileOverview />
        },
        {
          path: 'account',
          element: <AccountSettings />
        }
      ]
    },

    // Reports Module - Unified Reviewer/Accountant View
    {
      path: 'reports',
      children: [
        {
          path: '',
          element: (
            <PermissionGuard resource="report_provider_settlement" action="view" isRouteGuard>
              <ProviderSettlementReport />
            </PermissionGuard>
          )
        },
        {
          path: 'claims',
          element: (
            <PermissionGuard resource="claims" action="view" isRouteGuard>
              <ClaimsReport />
            </PermissionGuard>
          )
        },
        {
          path: 'claims/statement-preview',
          element: (
            <PermissionGuard resource="claims" action="view" isRouteGuard>
              <ClaimStatementPreview />
            </PermissionGuard>
          )
        },
        {
          path: 'unified',
          element: (
            <PermissionGuard resource="report_provider_settlement" action="view" isRouteGuard>
              <ProviderSettlementReport />
            </PermissionGuard>
          )
        }
      ]
    },

    // Under Development Placeholder
    {
      // Documents
      path: 'documents',
      element: (
        <PermissionGuard isRouteGuard>
          <TableRefreshProvider>
            <DocumentsLibrary />
          </TableRefreshProvider>
        </PermissionGuard>
      )
    },
    {
      path: 'under-development',
      element: <UnderDevelopment />
    },

    // Error Pages
    {
      path: '403',
      element: <NoAccess />
    },
    {
      path: 'forbidden',
      element: <Error403 />
    },
    {
      path: '404',
      element: <Error404 />
    },
    {
      path: '500',
      element: <Error500 />
    },
    {
      path: '*',
      element: <Error404 />
    }
  ]
};

export default MainRoutes;
