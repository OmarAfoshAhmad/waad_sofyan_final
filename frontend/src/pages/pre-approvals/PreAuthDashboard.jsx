import {
  Box,
  Grid,
  Typography,
  Button,
  Stack,
  Alert,
  Card,
  CardContent,
  IconButton,
  Tooltip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Divider
} from '@mui/material';
import {
  Refresh,
  Dashboard as DashboardIcon,
  TrendingUp,
  CheckCircle,
  Cancel,
  AttachMoney,
  Visibility as VisibilityIcon,
  AssignmentTurnedIn as AssignmentTurnedInIcon
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { ModernPageHeader } from 'components/tba';
import MainCard from 'components/MainCard';

import { usePreAuthDashboard, usePreAuthStats, useHighPriorityQueue, useExpiringSoon } from 'hooks/usePreAuthDashboard';

import {
  StatsCard,
  StatusDistributionChart,
  HighPriorityQueue,
  ExpiringSoonAlerts,
  TrendsChart,
  TopProvidersChart,
  RecentActivityTimeline
} from 'components/dashboard/PreAuthWidgets';
import { formatCurrency } from 'utils/currency-formatter';

/**
 * PreAuthorization Analytics Dashboard
 *
 * Displays comprehensive analytics for PreAuthorization requests including:
 * - Overall statistics (total, approved, rejected, amounts)
 * - Status distribution (pie chart)
 * - High priority queue (urgent/emergency requests)
 * - Expiring soon alerts
 * - Trends over time (line chart)
 * - Top providers (bar chart)
 * - Recent activity (timeline)
 */
const PreAuthDashboard = () => {
  const navigate = useNavigate();

  // Mock data for new requests
  const newRequests = [
    { id: 'REQ-99120', provider: 'مستشفى الحكمة', member: 'عمر المختار', date: '2026-06-30', status: 'PENDING', hasVariance: true },
    { id: 'REQ-99121', provider: 'عيادة النور', member: 'سالم علي', date: '2026-06-30', status: 'INFO_REQUESTED', hasVariance: false }
  ];

  // Dashboard settings
  const trendDays = 30;
  const topProvidersLimit = 10;

  // Fetch dashboard data
  const {
    dashboard,
    loading: dashboardLoading,
    error: dashboardError,
    refresh
  } = usePreAuthDashboard(
    trendDays,
    topProvidersLimit,
    true // auto-refresh enabled
  );

  // Fetch stats separately for real-time updates
  const { stats, refresh: refreshStats } = usePreAuthStats();

  // Fetch high priority queue
  const { queue, loading: queueLoading, refresh: refreshQueue } = useHighPriorityQueue(10);

  // Fetch expiring soon
  const { items: expiringSoon, loading: expiringSoonLoading, refresh: refreshExpiring } = useExpiringSoon(7, 10);

  // Calculate approval rate
  const calculateApprovalRate = () => {
    if (!stats) return 0;
    const total = stats.totalRequests || 0;
    const approved = stats.totalApproved || 0;
    return total > 0 ? Math.round((approved / total) * 100) : 0;
  };

  // Handle refresh all
  const handleRefreshAll = () => {
    refresh();
    refreshStats();
    refreshQueue();
    refreshExpiring();
  };

  // Handle view request
  const handleViewRequest = (request) => {
    if (request && request.id) {
      navigate(`/pre-approvals/${request.id}`);
    }
  };

  // Handle edit request
  const handleEditRequest = (request) => {
    if (request && request.id) {
      navigate(`/pre-approvals/${request.id}/edit`);
    }
  };

  // Show error if any
  if (dashboardError) {
    return (
      <Box sx={{ p: '1.5rem' }}>
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={handleRefreshAll}>
              إعادة المحاولة
            </Button>
          }
        >
          {dashboardError}
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* 🌟 ACTIONABLE HERO SECTION 🌟 */}
      <Box
        sx={{
          mb: 4,
          p: 4,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          borderRadius: 2,
          display: 'flex',
          flexWrap: 'wrap',
          gap: 3,
          justifyContent: 'space-between',
          alignItems: 'center',
          boxShadow: '0 8px 16px rgba(0,0,0,0.1)'
        }}
      >
        <Box>
          <Typography variant="h3" fontWeight="bold" mb={1}>
            مرحباً بك في لوحة المراجع الطبي
          </Typography>
          <Typography variant="h6" sx={{ opacity: 0.9, mb: 2 }}>
            يوجد طلبات بانتظار المراجعة والاعتماد. يُرجى التوجه إلى صندوق الوارد لإنجازها.
          </Typography>
          <Button
            variant="contained"
            color="secondary"
            size="large"
            startIcon={<AssignmentTurnedInIcon />}
            onClick={() => navigate('/pre-approvals')}
            sx={{ fontWeight: 'bold', px: 4, py: 1.5, fontSize: '1.1rem', borderRadius: 2, boxShadow: '0 4px 8px rgba(0,0,0,0.2)' }}
          >
            الذهاب إلى صندوق الموافقات (Inbox)
          </Button>
        </Box>
        <Box sx={{ display: 'flex', gap: 3, bgcolor: 'rgba(255,255,255,0.1)', p: 3, borderRadius: 2 }}>
          <Box textAlign="center">
            <Typography variant="h3" fontWeight="bold">
              {(dashboard?.statusDistribution?.pending || 0) + (dashboard?.statusDistribution?.underReview || 0)}
            </Typography>
            <Typography variant="body2" sx={{ opacity: 0.8 }}>
              قيد الانتظار
            </Typography>
          </Box>
          <Divider orientation="vertical" flexItem sx={{ borderColor: 'rgba(255,255,255,0.2)' }} />
          <Box textAlign="center">
            <Typography variant="h3" fontWeight="bold">
              {queue?.length || 0}
            </Typography>
            <Typography variant="body2" sx={{ opacity: 0.8 }}>
              طلبات عاجلة
            </Typography>
          </Box>
        </Box>
      </Box>

      <Typography variant="h5" mb={3} fontWeight="bold" color="text.primary">
        نظرة عامة على البيانات
      </Typography>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <StatusDistributionChart data={dashboard?.statusDistribution || {}} loading={dashboardLoading} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ExpiringSoonAlerts data={expiringSoon} loading={expiringSoonLoading} withinDays={7} />
        </Grid>

        {/* Row 4: Trends + High Priority */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <TrendsChart data={dashboard?.trends || []} loading={dashboardLoading} days={trendDays} />
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <HighPriorityQueue data={queue} loading={queueLoading} onView={handleViewRequest} onEdit={handleEditRequest} />
        </Grid>

        {/* Row 5: Top Providers + Recent Activity */}
        <Grid size={{ xs: 12, md: 7 }}>
          <TopProvidersChart data={dashboard?.topProviders || []} loading={dashboardLoading} limit={topProvidersLimit} />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <RecentActivityTimeline data={dashboard?.recentActivity || []} loading={dashboardLoading} limit={10} />
        </Grid>
      </Grid>

      {/* Info Footer */}
      <Box sx={{ mt: '1.0rem', py: 1, px: '1.0rem', bgcolor: 'grey.100', borderRadius: 1 }}>
        <Typography variant="caption" color="text.secondary" textAlign="center" display="block">
          📊 تحديث تلقائي كل دقيقتين | {new Date().toLocaleTimeString('en-US')}
        </Typography>
      </Box>
    </Box>
  );
};

export default PreAuthDashboard;
