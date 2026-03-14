import { useState, useCallback } from 'react';
import { Box, Alert, AlertTitle, Typography, Divider, Chip, Stack } from '@mui/material';
import {
  Policy as PolicyIcon,
  Warning as WarningIcon,
  TrendingUp as UtilizationIcon,
  Speed as StressIcon,
  Block as RejectIcon,
  EmojiEvents as RankingIcon,
  Info as InfoIcon
} from '@mui/icons-material';
import { formatNumber } from 'utils/formatters';

// Layout
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Hooks
import { useEmployerScope } from 'hooks/useEmployerScope';
import { useBenefitPolicyReport, DEFAULT_FILTERS } from 'hooks/useBenefitPolicyReport';

// Report Components
import {
  BenefitPolicyKPIs,
  UtilizationKPIs,
  LimitsStressTable,
  RejectionsAnalysis,
  PolicyEffectivenessTable,
  BenefitPolicyFilters,
  BenefitPolicyTable,
  BenefitPolicyInsights
} from 'components/reports/benefit-policy';
import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';

/**
 * BenefitPolicy Coverage & Utilization Report
 *
 * Strategic Management Report (READ-ONLY)
 *
 * Purpose:
 * - هل BenefitPolicy مصمم بشكل صحيح؟
 * - أين يتم الاستهلاك الحقيقي؟
 * - أي BenefitPolicy يسبب أكبر ضغط مالي؟
 * - هل الحدود (Limits) مبالغ فيها أو منخفضة؟
 *
 * RBAC:
 * - SUPER_ADMIN / ADMIN → All partners
 * - EMPLOYER_ADMIN → Own partner only
 * - REVIEWER → Read-only access
 * - PROVIDER → No access
 *
 * Route: /reports/benefit-policy
 */
const BenefitPolicyReport = () => {
  // ═══════════════════════════════════════════════════════════════════════════
  // EMPLOYER SCOPE (Centralized RBAC)
  // ═══════════════════════════════════════════════════════════════════════════

  const {
    effectiveEmployerId,
    selectedEmployerId,
    setSelectedEmployerId,
    employers,
    employersLoading,
    canSelectEmployer,
    isEmployerLocked,
    currentUser
  } = useEmployerScope();

  // ═══════════════════════════════════════════════════════════════════════════
  // FILTERS STATE
  // ═══════════════════════════════════════════════════════════════════════════

  const [filters, setFilters] = useState(DEFAULT_FILTERS);

  // ═══════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ═══════════════════════════════════════════════════════════════════════════

  const {
    policies,
    kpis,
    utilizationKpis,
    limitsStressData,
    rejectionsAnalysis,
    policyEffectivenessRanking,
    insights,
    loading,
    error,
    refetch
  } = useBenefitPolicyReport({
    employerId: effectiveEmployerId,
    filters
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════

  const handleFilterChange = useCallback((newFilters) => {
    setFilters(newFilters);
  }, []);

  const handleRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  // ═══════════════════════════════════════════════════════════════════════════
  // LARGE DATASET WARNING
  // ═══════════════════════════════════════════════════════════════════════════

  const showLargeDatasetWarning = policies.length > 500;

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title="تقرير تغطية واستخدام وثائق المنافع"
        titleEn="BenefitPolicy Coverage & Utilization Report"
        subtitle="تقرير استراتيجي لتحليل تصميم وفعالية وثائق المنافع"
        icon={PolicyIcon}
        showRefresh
        onRefresh={handleRefresh}
        refreshLoading={loading}
      />

      <MainCard>
        {/* Documentation Banner */}
        <Alert severity="info" icon={<InfoIcon />} sx={{ mb: '1.5rem' }}>
          <AlertTitle>تقرير قراري - Decision Making Report</AlertTitle>
          <Typography variant="body2" paragraph sx={{ mb: 1 }}>
            هذا التقرير يساعد الإدارة في اتخاذ قرارات تتعلق بتصميم وثائق المنافع:
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label="تحليل استخدام الوثائق" size="small" />
            <Chip label="اكتشاف الوثائق غير المستخدمة" size="small" />
            <Chip label="توزيع التغطية" size="small" />
            <Chip label="مقارنة أداء الوثائق" size="small" />
          </Stack>
        </Alert>

        {/* Large Dataset Warning */}
        {showLargeDatasetWarning && (
          <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: '1.5rem' }}>
            <AlertTitle>تحذير: كمية بيانات كبيرة</AlertTitle>
            <Typography variant="body2">
              يتم عرض {formatNumber(policies.length)} وثيقة. استخدم الفلاتر لتضييق النتائج وتحسين الأداء.
            </Typography>
          </Alert>
        )}

        {/* Error Display */}
        {error && (
          <Alert severity="error" sx={{ mb: '1.5rem' }}>
            <AlertTitle>خطأ في تحميل البيانات</AlertTitle>
            {error}
          </Alert>
        )}

        {/* Employer Filter */}
        {canSelectEmployer && (
          <Box sx={{ mb: '1.5rem' }}>
            <EmployerFilterSelector />
          </Box>
        )}

        {/* Locked Employer Notice */}
        {isEmployerLocked && (
          <Alert severity="info" variant="outlined" sx={{ mb: '1.5rem' }}>
            <Typography variant="body2">تم تحديد نطاق التقرير للشريك الخاص بك تلقائياً.</Typography>
          </Alert>
        )}

        {/* KPIs Section 1 - Policy Overview */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <PolicyIcon fontSize="small" />
            القسم 1: نظرة عامة على الوثائق
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Section 1: BenefitPolicy Overview
          </Typography>
          <BenefitPolicyKPIs kpis={kpis} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* KPIs Section 2 - Coverage Utilization */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <UtilizationIcon fontSize="small" />
            القسم 2: الاستهلاك والاستخدام
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Section 2: Coverage Utilization
          </Typography>
          <UtilizationKPIs utilizationKpis={utilizationKpis} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* Section 3 - Limits Pressure Analysis */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <StressIcon fontSize="small" />
            القسم 3: تحليل ضغط الحدود
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Section 3: Limits Pressure Analysis
          </Typography>
          <LimitsStressTable data={limitsStressData} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* Section 4 - Rejections Analysis */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <RejectIcon fontSize="small" />
            القسم 4: تحليل الرفض
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Section 4: Rejections Analysis
          </Typography>
          <RejectionsAnalysis rejectionsAnalysis={rejectionsAnalysis} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* Section 5 - Policy Effectiveness Ranking */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <RankingIcon fontSize="small" />
            القسم 5: ترتيب فعالية الوثائق
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Section 5: BenefitPolicy Effectiveness Ranking
          </Typography>
          <PolicyEffectivenessTable data={policyEffectivenessRanking} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* Insights Section */}
        <Box sx={{ mb: '2.0rem' }}>
          <Typography variant="h6" gutterBottom>
            التحليلات والرؤى
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Insights & Analytics
          </Typography>
          <BenefitPolicyInsights insights={insights} loading={loading} />
        </Box>

        <Divider sx={{ my: '1.5rem' }} />

        {/* Filters Section */}
        <Box sx={{ mb: '1.5rem' }}>
          <Typography variant="h6" gutterBottom>
            الفلاتر
          </Typography>
          <BenefitPolicyFilters
            filters={filters}
            onFilterChange={handleFilterChange}
            showEmployerFilter={canSelectEmployer}
            loading={loading}
          />
        </Box>

        {/* Policies Table */}
        <Box>
          <Typography variant="h6" gutterBottom>
            قائمة وثائق المنافع
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
            Benefit Policies List • {formatNumber(policies.length)} وثيقة
          </Typography>
          <BenefitPolicyTable policies={policies} loading={loading} />
        </Box>
      </MainCard>
    </>
  );
};

export default BenefitPolicyReport;
