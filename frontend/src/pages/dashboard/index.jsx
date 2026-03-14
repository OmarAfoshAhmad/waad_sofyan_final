import { useMemo, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// material-ui
import {
  Box,
  Grid,
  Stack,
  Typography,
  Card,
  CardContent,
  Skeleton,
  IconButton,
  LinearProgress,
  useTheme,
  alpha,
  Divider
} from '@mui/material';

// Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import DashboardIcon from '@mui/icons-material/Dashboard';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import PeopleIcon from '@mui/icons-material/People';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Pending';
import CancelIcon from '@mui/icons-material/Cancel';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import AssignmentIcon from '@mui/icons-material/Assignment';
import BusinessIcon from '@mui/icons-material/Business';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import VerifiedIcon from '@mui/icons-material/Verified';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';

// project imports
import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';

// contexts
import { useEmployerFilter } from 'contexts/EmployerFilterContext';

// RBAC
import { useRBAC } from 'api/rbac';

// hooks
import { useDashboardStats } from 'hooks/useDashboardStats';
import { useClaimsList } from 'hooks/useClaims';
import { getDefaultRouteForRole } from 'utils/roleRoutes';
import useAuth from 'hooks/useAuth';

// ═══════════════════════════════════════════════════════════════════════════════
// SUB-COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Gradient KPI Card ──────────────────────────────────────────────────────

const GradientKPICard = ({ title, value, subtitle, icon: Icon, gradient, loading = false }) => {
  return (
    <Card
      sx={{
        height: '100%',
        background: gradient,
        color: '#fff',
        borderRadius: '0.125rem',
        overflow: 'hidden',
        position: 'relative',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        transition: 'transform 0.2s ease',
        '&:hover': { transform: 'translateY(-2px)' }
      }}
    >
      <CardContent sx={{ p: '0.75rem', '&:last-child': { pb: '0.75rem' }, position: 'relative', zIndex: 1 }}>
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="caption" sx={{ opacity: 0.9, fontWeight: 700, textTransform: 'uppercase' }}>
              {title}
            </Typography>
            <Box sx={{ p: 0.5, borderRadius: 1, bgcolor: 'rgba(255,255,255,0.2)', display: 'flex' }}>
              <Icon sx={{ fontSize: '1.125rem' }} />
            </Box>
          </Stack>

          {loading ? (
            <Skeleton variant="text" width="50%" height={24} sx={{ bgcolor: 'rgba(255,255,255,0.3)' }} />
          ) : (
            <Typography variant="h5" fontWeight={800} sx={{ fontFamily: "'Roboto', sans-serif" }}>
              {typeof value === 'number' ? value.toLocaleString('en-US') : value}
            </Typography>
          )}

          {subtitle && (
            <Typography variant="caption" sx={{ opacity: 0.8, fontSize: '0.75rem', display: 'block', noWrap: true }}>
              {subtitle}
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

// ─── SVG Donut Chart ────────────────────────────────────────────────────────

const DonutChart = ({ data, centerLabel, centerValue, size = 140 }) => {
  const total = data.reduce((sum, d) => sum + d.value, 0);
  const radius = 55;
  const strokeWidth = 20;
  const center = size / 2;

  let cumulativePercent = 0;

  const getCoordinatesForPercent = (percent) => {
    const x = Math.cos(2 * Math.PI * percent);
    const y = Math.sin(2 * Math.PI * percent);
    return [x, y];
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%', gap: 1 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        {total === 0 ? (
          <circle cx={center} cy={center} r={radius} fill="none" stroke="#e0e0e0" strokeWidth={strokeWidth} />
        ) : (
          data.map((segment, i) => {
            const percent = segment.value / total;
            const [startX, startY] = getCoordinatesForPercent(cumulativePercent);
            cumulativePercent += percent;
            const [endX, endY] = getCoordinatesForPercent(cumulativePercent);
            const largeArcFlag = percent > 0.5 ? 1 : 0;
            const pathData = [`M ${center + startX * radius} ${center + startY * radius}`, `A ${radius} ${radius} 0 ${largeArcFlag} 1 ${center + endX * radius} ${center + endY * radius}`].join(' ');

            return (
              <path
                key={i}
                d={pathData}
                fill="none"
                stroke={segment.color}
                strokeWidth={strokeWidth}
                strokeLinecap="round"
                style={{ transition: 'all 0.5s ease' }}
              />
            );
          })
        )}
        <text x={center} y={center - 5} textAnchor="middle" fontSize="20" fontWeight="800" fill="#333" fontFamily="Roboto, sans-serif">
          {centerValue}
        </text>
        <text x={center} y={center + 10} textAnchor="middle" fontSize="9" fill="#888" fontFamily="'Noto Sans Arabic', sans-serif">
          {centerLabel}
        </text>
      </svg>

      <Stack spacing={0.5} sx={{ width: '100%' }}>
        {data.map((segment, i) => (
          <Stack key={i} direction="row" alignItems="center" justifyContent="space-between" sx={{
            px: 1,
            py: 0.25,
            borderRadius: 1,
            bgcolor: alpha(segment.color, 0.04)
          }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Box sx={{ width: '0.375rem', height: '0.375rem', borderRadius: '50%', bgcolor: segment.color }} />
              <Typography sx={{ fontSize: '0.7rem' }} color="text.secondary">
                {segment.label}
              </Typography>
            </Stack>
            <Typography sx={{ fontSize: '0.7rem', fontWeight: 700 }}>
              {segment.value}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
};

// ─── Horizontal Bar Chart ───────────────────────────────────────────────────

const HorizontalBarChart = ({ data }) => {
  const theme = useTheme();
  const maxValue = Math.max(...data.map((d) => d.value), 1);
  const barColors = ['#0f766e', '#0891b2', '#059669', '#7c3aed', '#ea580c'];

  return (
    <Stack spacing={2}>
      {data.length === 0 ? (
        <Box sx={{ py: '2.0rem', textAlign: 'center' }}>
          <Typography variant="caption" color="text.disabled">لا يوجد بيانات</Typography>
        </Box>
      ) : (
        data.map((item, i) => (
          <Box key={i}>
            <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600 }} noWrap>
                {item.label}
              </Typography>
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 800 }}>{item.value}</Typography>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={(item.value / maxValue) * 100}
              sx={{ height: '0.375rem', borderRadius: '0.1875rem', bgcolor: alpha(barColors[i % 5], 0.1) }}
            />
          </Box>
        ))
      )}
    </Stack>
  );
};

// ─── Activity Timeline ──────────────────────────────────────────────────────

const ActivityTimeline = ({ claims, loading }) => {
  const events = useMemo(() => {
    if (!claims || !Array.isArray(claims) || claims.length === 0) return [];

    return claims.slice(0, 5).map((claim) => {
      const statusMap = {
        APPROVED: { color: '#059669', icon: CheckCircleIcon, text: 'تم اعتماد مطالبة' },
        SETTLED: { color: '#0891b2', icon: AttachMoneyIcon, text: 'تمت تسوية مطالبة' },
        UNDER_REVIEW: { color: '#d97706', icon: PendingIcon, text: 'مطالبة قيد المراجعة' },
        SUBMITTED: { color: '#2563eb', icon: AssignmentIcon, text: 'تم تقديم مطالبة' },
        REJECTED: { color: '#dc2626', icon: CancelIcon, text: 'تم رفض مطالبة' },
        DRAFT: { color: '#6b7280', icon: AccessTimeIcon, text: 'مسودة مطالبة' }
      };
      const config = statusMap[claim.status] || statusMap.DRAFT;
      const date = claim.createdAt ? new Date(claim.createdAt) : new Date();
      return {
        ...config,
        detail: `${claim.claimNumber || '#' + claim.id} — ${claim.providerName || claim.memberName || ''}`,
        time: date.toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit' })
      };
    });
  }, [claims]);

  if (loading) {
    return (
      <Stack spacing={2}>
        {[...Array(5)].map((_, i) => (
          <Skeleton key={i} variant="rectangular" height={32} sx={{ borderRadius: 1 }} />
        ))}
      </Stack>
    );
  }

  if (events.length === 0) {
    return (
      <Box sx={{ py: '2.5rem', textAlign: 'center' }}>
        <AccessTimeIcon sx={{ fontSize: '3.5rem', color: 'divider', mb: '0.75rem' }} />
        <Typography variant="body1" color="text.secondary" fontWeight={600}>
          النظام جاهز للعمل
        </Typography>
        <Typography variant="caption" color="text.disabled">
          ستظهر الأنشطة هنا عند بدء تشغيل النظام
        </Typography>
      </Box>
    );
  }

  return (
    <Stack spacing={0}>
      {events.map((event, i) => {
        const EventIcon = event.icon;
        return (
          <Stack key={i} direction="row" spacing={1.5} alignItems="flex-start" sx={{ py: '0.625rem', position: 'relative' }}>
            {/* Timeline line */}
            {i < events.length - 1 && (
              <Box
                sx={{
                  position: 'absolute',
                  right: '0.9375rem',
                  top: '18.0rem',
                  bottom: -8,
                  width: '0.125rem',
                  bgcolor: 'divider'
                }}
              />
            )}
            {/* Icon */}
            <Box
              sx={{
                p: 0.75,
                borderRadius: '50%',
                bgcolor: alpha(event.color, 0.12),
                color: event.color,
                display: 'flex',
                zIndex: 1,
                flexShrink: 0
              }}
            >
              <EventIcon sx={{ fontSize: '1.0rem' }} />
            </Box>
            {/* Text */}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="caption" fontWeight={600} color="text.primary">
                {event.text}
              </Typography>
              <Typography variant="caption" display="block" color="text.secondary" noWrap>
                {event.detail}
              </Typography>
            </Box>
            {/* Time */}
            <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0 }}>
              {event.time}
            </Typography>
          </Stack>
        );
      })}
    </Stack>
  );
};

// ─── Network Progress Card ──────────────────────────────────────────────────

const NetworkStatRow = ({ label, value, maxValue, icon: Icon, color }) => {
  const theme = useTheme();
  const colorValue = theme.palette[color]?.main || color;
  const percent = maxValue > 0 ? Math.min((value / maxValue) * 100, 100) : 0;

  return (
    <Stack spacing={0.75}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Stack direction="row" alignItems="center" spacing={1}>
          <Box
            sx={{
              p: 0.5,
              borderRadius: 1,
              bgcolor: alpha(colorValue, 0.1),
              color: colorValue,
              display: 'flex'
            }}
          >
            <Icon sx={{ fontSize: '1.0rem' }} />
          </Box>
          <Typography variant="body2" fontWeight={600}>
            {label}
          </Typography>
        </Stack>
        <Typography variant="body2" fontWeight={800} fontFamily="'Roboto', sans-serif" color={colorValue}>
          {typeof value === 'number' ? value.toLocaleString('en-US') : value}
        </Typography>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={percent}
        sx={{
          height: '0.375rem',
          borderRadius: '0.1875rem',
          bgcolor: alpha(colorValue, 0.08),
          '& .MuiLinearProgress-bar': {
            borderRadius: '0.1875rem',
            bgcolor: colorValue
          }
        }}
      />
    </Stack>
  );
};

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════════════════════

export default function Dashboard() {
  const navigate = useNavigate();
  const theme = useTheme();
  const { user } = useAuth();

  // ─── Role-based redirect ────────────────────────────────────────────────────

  const getCurrentUserRoles = useCallback(() => {
    try {
      const localUser = JSON.parse(localStorage.getItem('user') || '{}');
      const currentUser = user || localUser;
      const roles = [];

      if (Array.isArray(currentUser?.roles)) {
        roles.push(...currentUser.roles.map((role) => (typeof role === 'string' ? role : role?.name)).filter(Boolean));
      }

      if (typeof currentUser?.role === 'string' && currentUser.role.trim()) {
        roles.push(currentUser.role.trim());
      }

      return [...new Set(roles.map((role) => role.toUpperCase()))];
    } catch {
      return [];
    }
  }, [user]);

  const userRoles = useMemo(() => getCurrentUserRoles(), [getCurrentUserRoles]);
  const isMedicalReviewer = userRoles.includes('MEDICAL_REVIEWER');
  const isProviderRole = userRoles.includes('PROVIDER_STAFF') || userRoles.includes('PROVIDER');

  useEffect(() => {
    if (isMedicalReviewer) {
      navigate(getDefaultRouteForRole('MEDICAL_REVIEWER'), { replace: true });
      return;
    }
    if (isProviderRole) {
      navigate(getDefaultRouteForRole('PROVIDER_STAFF'), { replace: true });
    }
  }, [isMedicalReviewer, isProviderRole, navigate]);

  // ─── RBAC ───────────────────────────────────────────────────────────────────

  const { isSuperAdmin } = useRBAC();

  // ─── Employer Filter ────────────────────────────────────────────────────────

  const { selectedEmployerId } = useEmployerFilter();

  // ─── Data Fetching ──────────────────────────────────────────────────────────

  const { summary, loading: summaryLoading, refresh: refreshSummary } = useDashboardStats({
    enabled: !isMedicalReviewer && !isProviderRole,
    silentOnForbidden: true
  });

  const {
    data: claimsData,
    loading: claimsLoading,
    refresh: refreshClaims
  } = useClaimsList({
    page: 0,
    size: 10,
    employerId: selectedEmployerId,
    sortBy: 'createdAt',
    sortDir: 'desc'
  });

  const handleRefreshAll = useCallback(() => {
    refreshSummary();
    refreshClaims();
  }, [refreshSummary, refreshClaims]);

  // ─── Computed Values ────────────────────────────────────────────────────────

  const totalClaims = summary?.totalClaims || 0;
  const openClaims = summary?.openClaims || 0;
  const approvedClaims = summary?.approvedClaims || 0;
  const totalMembers = summary?.totalMembers || 0;
  const activeMembers = summary?.activeMembers || 0;
  const totalProviders = summary?.totalProviders || 0;
  const activeProviders = summary?.activeProviders || 0;
  const totalMedicalCost = summary?.totalMedicalCost ? parseFloat(summary.totalMedicalCost) : 0;
  const rejectedClaims = summary?.rejectedClaims || 0;

  const formatLYD = (amount) => {
    if (!amount) return '0.00 د.ل';
    return `${amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} د.ل`;
  };

  // ─── Chart Data ─────────────────────────────────────────────────────────────

  const donutData = useMemo(
    () => [
      { label: 'معتمدة', value: approvedClaims, color: '#059669' },
      { label: 'قيد المراجعة', value: openClaims, color: '#d97706' },
      { label: 'مرفوضة', value: rejectedClaims, color: '#dc2626' },
      { label: 'أخرى', value: Math.max(0, totalClaims - approvedClaims - openClaims - rejectedClaims), color: '#94a3b8' }
    ],
    [totalClaims, approvedClaims, openClaims, rejectedClaims]
  );

  // Top providers from claims data
  const topProviders = useMemo(() => {
    const claims = claimsData?.content || [];
    if (claims.length === 0) return [];

    const providerMap = {};
    claims.forEach((claim) => {
      const name = claim.providerName || claim.provider?.name;
      if (name) {
        providerMap[name] = (providerMap[name] || 0) + 1;
      }
    });

    return Object.entries(providerMap)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([label, value]) => ({ label, value }));
  }, [claimsData]);

  // ─── User display name ─────────────────────────────────────────────────────

  const displayName = useMemo(() => {
    const localUser = JSON.parse(localStorage.getItem('user') || '{}');
    const currentUser = user || localUser;
    return currentUser?.fullName || currentUser?.username || 'المشرف';
  }, [user]);

  const maxNetworkValue = Math.max(activeMembers, activeProviders, summary?.activeContracts || 0, 1);

  // ═════════════════════════════════════════════════════════════════════════════
  // RENDER
  // ═════════════════════════════════════════════════════════════════════════════

  return (
    <Box sx={{
      width: '100%',
      minHeight: 'calc(100vh - 110px)',
      display: 'flex',
      flexDirection: 'column',
      p: { xs: 1.5, sm: '1.0rem' }
    }}>
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '1.25rem', mb: '1.5rem' }}>
        {/* Welcome Bar - Softer Professional Green */}
        <Card sx={{
          background: 'linear-gradient(135deg, #0d9488 0%, #059669 100%)',
          borderRadius: '0.1875rem',
          color: '#fff',
          border: 'none',
          boxShadow: '0 8px 24px rgba(13, 148, 136, 0.15)'
        }}>
          <CardContent sx={{ py: '0.75rem', px: '1.25rem', '&:last-child': { pb: '0.75rem' } }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Stack>
                <Typography variant="h6" fontWeight={800} sx={{ letterSpacing: -0.5 }}>👋 مرحباً بك، {displayName}</Typography>
                <Typography sx={{ fontSize: '0.8rem', opacity: 0.9 }}>نظام إدارة العمليات الصحية - لوحة التحكم الموحدة</Typography>
              </Stack>
              <Stack direction="row" spacing={2} alignItems="center">
                <EmployerFilterSelector size="small" sx={{ minWidth: '18.75rem', '& .MuiInputBase-root': { color: '#fff', bgcolor: 'rgba(255,255,255,0.15)', height: '2.5rem', borderRadius: '0.125rem' } }} />
                <IconButton size="small" onClick={handleRefreshAll} sx={{ color: '#fff', bgcolor: 'rgba(255,255,255,0.2)', '&:hover': { bgcolor: 'rgba(255,255,255,0.3)' } }}>
                  <RefreshIcon sx={{ fontSize: '1.375rem' }} />
                </IconButton>
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        {/* Row 1: KPIs */}
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <GradientKPICard title="المستفيدين" value={activeMembers} subtitle={`إجمالي: ${totalMembers}`} icon={PeopleIcon} gradient="linear-gradient(135deg, #059669 0%, #10b981 100%)" loading={summaryLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <GradientKPICard title="مقدمي الخدمات" value={activeProviders} subtitle={`إجمالي: ${totalProviders}`} icon={LocalHospitalIcon} gradient="linear-gradient(135deg, #0ea5e9 0%, #38bdf8 100%)" loading={summaryLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <GradientKPICard title="إجمالي المطالبات" value={totalClaims} subtitle={formatLYD(totalMedicalCost)} icon={ReceiptLongIcon} gradient="linear-gradient(135deg, #0d9488 0%, #14b8a6 100%)" loading={summaryLoading} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <GradientKPICard title="قيد المراجعة" value={openClaims} subtitle={openClaims > 0 ? 'بحاجة لتدخل' : 'مكتمل'} icon={PendingIcon} gradient={openClaims > 0 ? 'linear-gradient(135deg, #e11d48 0%, #fb7185 100%)' : 'linear-gradient(135deg, #475569 0%, #64748b 100%)'} loading={summaryLoading} />
          </Grid>
        </Grid>

        {/* Row 2: Analysis & Records */}
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card sx={{ height: '22.5rem', borderRadius: '0.1875rem', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
              <CardContent sx={{ p: '1.25rem' }}>
                <Typography variant="subtitle2" fontWeight={800} sx={{ mb: '1.0rem' }}>توزيع الحالات</Typography>
                <Divider sx={{ mb: '1.25rem' }} />
                <DonutChart data={donutData} centerValue={totalClaims} centerLabel="إجمالي" size={140} />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card sx={{ height: '22.5rem', borderRadius: '0.1875rem', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
              <CardContent sx={{ p: '1.25rem' }}>
                <Typography variant="subtitle2" fontWeight={800} sx={{ mb: '1.0rem' }}>أعلى مقدمي الخدمات</Typography>
                <Divider sx={{ mb: '1.25rem' }} />
                <HorizontalBarChart data={topProviders} />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card sx={{ height: '22.5rem', borderRadius: '0.1875rem', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
              <CardContent sx={{ p: '1.25rem' }}>
                <Typography variant="subtitle2" fontWeight={800} sx={{ mb: '1.0rem' }}>إحصائيات الشبكة</Typography>
                <Divider sx={{ mb: '1.25rem' }} />
                <Stack spacing={2.5}>
                  <NetworkStatRow label="المقدمين" value={activeProviders} maxValue={maxNetworkValue} icon={LocalHospitalIcon} color="primary" />
                  <NetworkStatRow label="العقود" value={summary?.activeContracts || 0} maxValue={maxNetworkValue} icon={BusinessIcon} color="info" />
                  <NetworkStatRow label="المستفيدين" value={activeMembers} maxValue={maxNetworkValue} icon={PeopleIcon} color="#0d9488" />
                  <NetworkStatRow label="المطالبات" value={totalClaims} maxValue={Math.max(totalClaims, maxNetworkValue)} icon={ReceiptLongIcon} color="#7c3aed" />
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card sx={{ height: '22.5rem', borderRadius: '0.1875rem', boxShadow: '0 4px 20px rgba(0,0,0,0.06)' }}>
              <CardContent sx={{ p: '1.25rem' }}>
                <Typography variant="subtitle2" fontWeight={800} sx={{ mb: '1.0rem' }}>آخر الأنشطة</Typography>
                <Divider sx={{ mb: '1.25rem' }} />
                <ActivityTimeline claims={claimsData?.content || []} loading={claimsLoading} />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>

      {/* System status Footer - Pushed to bottom */}
      <Box sx={{
        p: '0.625rem',
        borderRadius: '0.125rem',
        bgcolor: alpha('#10b981', 0.05),
        border: '1px solid',
        borderColor: alpha('#10b981', 0.12),
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        mt: 'auto',
        mx: 0
      }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Box sx={{ width: '0.375rem', height: '0.375rem', borderRadius: '50%', bgcolor: '#10b981', animation: 'pulse 2s infinite' }} />
          <Typography sx={{ fontSize: '0.8rem', fontWeight: 700, color: '#065f46', opacity: 0.9 }}>
            اتصال النظام مستقر - تعمل جميع الخدمات بكفاءة عالية
          </Typography>
        </Stack>
      </Box>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.4; }
        }
      `}</style>
    </Box>
  );
}




