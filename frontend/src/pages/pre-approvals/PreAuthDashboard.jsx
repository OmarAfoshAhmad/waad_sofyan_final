import { Box, Grid, Typography, Button, Stack, Alert, Card, CardContent, IconButton, Tooltip } from '@mui/material';
import { Refresh, Dashboard as DashboardIcon, TrendingUp, CheckCircle, Cancel, AttachMoney } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { ModernPageHeader } from 'components/tba';

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
    <Box>
      {/* Header */}
      <ModernPageHeader
        title="لوحة تحليلات الموافقات المسبقة"
        subtitle="تحليل شامل لطلبات الموافقات المسبقة والإحصائيات"
        icon={DashboardIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'الموافقات المسبقة', path: '/pre-approvals' }, { label: 'لوحة التحكم' }]}
        actions={
          <Tooltip title="تحديث البيانات">
            <IconButton onClick={handleRefreshAll} color="primary">
              <Refresh />
            </IconButton>
          </Tooltip>
        }
      />

      {/* Main Content */}
      <Grid container spacing={2}>
        {/* Row 1: Stats Cards */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatsCard
            title="إجمالي الطلبات"
            value={stats?.totalRequests || 0}
            change={stats?.requestsChangePercent}
            icon={TrendingUp}
            color="primary"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatsCard
            title="المعتمدة"
            value={stats?.totalApproved || 0}
            change={stats?.approvedChangePercent}
            icon={CheckCircle}
            color="success"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatsCard title="المرفوضة" value={stats?.totalRejected || 0} change={stats?.rejectedChangePercent} icon={Cancel} color="error" />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatsCard title="نسبة الموافقة" value={calculateApprovalRate()} icon={CheckCircle} color="info" suffix="%" />
        </Grid>

        {/* Row 2: Amount Stats */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent sx={{ py: '1.0rem' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    إجمالي المبالغ المطلوبة
                  </Typography>
                  <Typography variant="h6" fontWeight="bold">
                    {formatCurrency(stats?.totalRequestedAmount || 0)}
                  </Typography>
                </Box>
                <AttachMoney color="primary" sx={{ fontSize: '2.0rem', opacity: 0.7 }} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent sx={{ py: '1.0rem' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    إجمالي المبالغ المعتمدة
                  </Typography>
                  <Typography variant="h6" fontWeight="bold" color="success.main">
                    {formatCurrency(stats?.totalApprovedAmount || 0)}
                  </Typography>
                </Box>
                <AttachMoney color="success" sx={{ fontSize: '2.0rem', opacity: 0.7 }} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent sx={{ py: '1.0rem' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    متوسط المبلغ المطلوب
                  </Typography>
                  <Typography variant="h6" fontWeight="bold" color="info.main">
                    {formatCurrency(stats?.averageRequestedAmount || 0)}
                  </Typography>
                </Box>
                <AttachMoney color="info" sx={{ fontSize: '2.0rem', opacity: 0.7 }} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* Row 3: Charts */}
        <Grid size={{ xs: 12, md: 6 }}>
          <StatusDistributionChart data={dashboard?.statusDistribution || []} loading={dashboardLoading} />
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

