import { useMemo } from 'react';
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import useEmployerDashboardKPIs, { CLAIM_STATUS_LABELS, CLAIM_STATUS_COLORS } from 'hooks/useEmployerDashboardKPIs';
import { formatNumber, formatCurrency } from 'utils/formatters';

// MUI Components
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Skeleton,
  Alert,
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  IconButton,
  Tooltip,
  Stack,
  Paper,
  Divider
} from '@mui/material';
import { useTheme } from '@mui/material/styles';

// MUI Icons
import PeopleIcon from '@mui/icons-material/People';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import DescriptionIcon from '@mui/icons-material/Description';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import RefreshIcon from '@mui/icons-material/Refresh';
import BusinessIcon from '@mui/icons-material/Business';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';

// ──────────────────────────────────────────────────────────────────────────────
// KPI Card Component
// ──────────────────────────────────────────────────────────────────────────────
const KPICard = ({ title, value, subtitle, icon: Icon, color, loading, error }) => {
  const theme = useTheme();

  return (
    <Card
      sx={{
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        '&::before': {
          content: '""',
          position: 'absolute',
          top: 0,
          right: 0,
          width: '6.25rem',
          height: '6.25rem',
          background: `radial-gradient(circle at top right, ${theme.palette[color]?.light || theme.palette.primary.light}40, transparent)`,
          borderRadius: '0 0 0 100%'
        }
      }}
    >
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {title}
            </Typography>

            {loading ? (
              <Skeleton variant="text" width={80} height={48} />
            ) : error ? (
              <Typography color="error" variant="body2">
                {error}
              </Typography>
            ) : (
              <>
                <Typography variant="h3" fontWeight={600}>
                  {value}
                </Typography>
                {subtitle && (
                  <Typography variant="caption" color="text.secondary">
                    {subtitle}
                  </Typography>
                )}
              </>
            )}
          </Box>

          <Box
            sx={{
              p: '0.75rem',
              borderRadius: '0.25rem',
              bgcolor: `${theme.palette[color]?.main || theme.palette.primary.main}15`
            }}
          >
            <Icon
              sx={{
                fontSize: '1.75rem',
                color: theme.palette[color]?.main || theme.palette.primary.main
              }}
            />
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
};

// ──────────────────────────────────────────────────────────────────────────────
// Claims Status Grid Component
// ──────────────────────────────────────────────────────────────────────────────
const ClaimsStatusGrid = ({ claimsByStatusArray, loading }) => {
  const theme = useTheme();

  const getStatusColor = (colorName) => {
    const colorMap = {
      default: theme.palette.grey[500],
      info: theme.palette.info.main,
      warning: theme.palette.warning.main,
      success: theme.palette.success.main,
      error: theme.palette.error.main,
      secondary: theme.palette.secondary.main
    };
    return colorMap[colorName] || theme.palette.grey[500];
  };

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" gutterBottom sx={{ mb: '1.0rem' }}>
          المطالبات حسب الحالة
        </Typography>

        {loading ? (
          <Grid container spacing={1}>
            {[1, 2, 3, 4, 5, 6, 7].map((i) => (
              <Grid key={i} size={{ xs: 6, sm: 4, md: 3 }}>
                <Skeleton variant="rounded" height={70} />
              </Grid>
            ))}
          </Grid>
        ) : (
          <Grid container spacing={1.5}>
            {claimsByStatusArray.map(({ status, label, count, color }) => (
              <Grid key={status} size={{ xs: 6, sm: 4, md: 3 }}>
                <Paper
                  variant="outlined"
                  sx={{
                    p: '0.75rem',
                    textAlign: 'center',
                    borderColor: getStatusColor(color),
                    borderWidth: 1,
                    borderRadius: '0.25rem',
                    transition: 'all 0.2s',
                    '&:hover': {
                      bgcolor: `${getStatusColor(color)}10`,
                      transform: 'translateY(-2px)'
                    }
                  }}
                >
                  <Typography variant="h4" fontWeight={600} sx={{ color: getStatusColor(color) }}>
                    {formatNumber(count)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {label}
                  </Typography>
                </Paper>
              </Grid>
            ))}
          </Grid>
        )}
      </CardContent>
    </Card>
  );
};

// ──────────────────────────────────────────────────────────────────────────────
// Amount Summary Card Component
// ──────────────────────────────────────────────────────────────────────────────
const AmountSummaryCard = ({ approvedAmount, rejectedAmount, loading }) => {
  const theme = useTheme();

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          ملخص المبالغ
        </Typography>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: '1.0rem' }}>
          إجمالي المبالغ للفترة الكاملة
        </Typography>

        {loading ? (
          <Stack spacing={2}>
            <Skeleton variant="rounded" height={60} />
            <Skeleton variant="rounded" height={60} />
          </Stack>
        ) : (
          <Stack spacing={2}>
            {/* Approved Amount */}
            <Paper
              sx={{
                p: '1.0rem',
                bgcolor: `${theme.palette.success.main}10`,
                borderRadius: '0.25rem'
              }}
            >
              <Stack direction="row" alignItems="center" spacing={1}>
                <CheckCircleIcon sx={{ fontSize: '1.25rem', color: theme.palette.success.main }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    المبالغ المعتمدة (موافق عليها + تمت التسوية)
                  </Typography>
                  <Typography variant="h5" fontWeight={600} color="success.main">
                    {formatCurrency(approvedAmount)}
                  </Typography>
                </Box>
              </Stack>
            </Paper>

            {/* Rejected Amount */}
            <Paper
              sx={{
                p: '1.0rem',
                bgcolor: `${theme.palette.error.main}10`,
                borderRadius: '0.25rem'
              }}
            >
              <Stack direction="row" alignItems="center" spacing={1}>
                <CancelIcon sx={{ fontSize: '1.25rem', color: theme.palette.error.main }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    المبالغ المرفوضة
                  </Typography>
                  <Typography variant="h5" fontWeight={600} color="error.main">
                    {formatCurrency(rejectedAmount)}
                  </Typography>
                </Box>
              </Stack>
            </Paper>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
};

// ──────────────────────────────────────────────────────────────────────────────
// Main Dashboard Component
// ──────────────────────────────────────────────────────────────────────────────
const EmployerDashboard = () => {
  // Employer/partner filter removed by business request.
  const effectiveEmployerId = undefined;

  // Fetch KPIs
  const {
    totalMembers,
    activeMembers,
    membersLoading,
    membersError,
    totalVisits,
    visitsLoading,
    visitsError,
    totalClaims,
    claimsByStatusArray,
    approvedAmount,
    rejectedAmount,
    claimsLoading,
    claimsError,
    isLoading,
    hasError,
    refresh
  } = useEmployerDashboardKPIs(effectiveEmployerId);

  return (
    <Box>
      <UnifiedPageHeader
        title="لوحة مؤشرات الشريك"
        subtitle="متابعة أداء الشركاء والمطالبات"
        icon={BusinessIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'لوحة المؤشرات' }]}
        additionalActions={
          <Stack direction="row" spacing={2} alignItems="center">
            <Tooltip title="تحديث البيانات">
              <IconButton onClick={refresh} disabled={isLoading} color="primary">
                <RefreshIcon
                  sx={{
                    fontSize: '1.25rem',
                    animation: isLoading ? 'spin 1s linear infinite' : 'none'
                  }}
                />
              </IconButton>
            </Tooltip>
          </Stack>
        }
      />

      <MainCard>
        {/* Global Error Alert */}
        {hasError && (
          <Alert severity="warning" icon={<WarningIcon sx={{ fontSize: '1.25rem' }} />} sx={{ mb: '1.5rem' }}>
            حدث خطأ في تحميل بعض البيانات. البيانات المعروضة قد تكون غير كاملة.
          </Alert>
        )}

        {/* KPI Cards Row */}
        <Grid container spacing={3} sx={{ mb: '1.5rem' }}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <KPICard
              title="إجمالي الأعضاء"
              value={formatNumber(totalMembers)}
              subtitle={`${formatNumber(activeMembers)} عضو نشط`}
              icon={PeopleIcon}
              color="primary"
              loading={membersLoading}
              error={membersError}
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <KPICard
              title="إجمالي الزيارات"
              value={formatNumber(totalVisits)}
              icon={LocalHospitalIcon}
              color="info"
              loading={visitsLoading}
              error={visitsError}
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <KPICard
              title="إجمالي المطالبات"
              value={formatNumber(totalClaims)}
              icon={DescriptionIcon}
              color="warning"
              loading={claimsLoading}
              error={claimsError}
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <KPICard
              title="المبلغ المعتمد"
              value={formatCurrency(approvedAmount)}
              icon={AttachMoneyIcon}
              color="success"
              loading={claimsLoading}
              error={claimsError}
            />
          </Grid>
        </Grid>

        {/* Second Row: Status Grid + Amount Summary */}
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 8 }}>
            <ClaimsStatusGrid claimsByStatusArray={claimsByStatusArray} loading={claimsLoading} />
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <AmountSummaryCard approvedAmount={approvedAmount} rejectedAmount={rejectedAmount} loading={claimsLoading} />
          </Grid>
        </Grid>

        {/* CSS for spinner animation */}
        <style>
          {`
          @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}
        </style>
      </MainCard>
    </Box>
  );
};

export default EmployerDashboard;



