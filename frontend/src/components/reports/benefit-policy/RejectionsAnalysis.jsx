import PropTypes from 'prop-types';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Skeleton,
  Alert,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  LinearProgress
} from '@mui/material';
import {
  Block as RejectedIcon,
  AttachMoney as AmountIcon,
  Percent as RateIcon,
  Policy as PolicyIcon,
  Notes as ReasonIcon,
  Category as CategoryIcon
} from '@mui/icons-material';
import { formatCurrencyLYD as formatCurrency } from 'utils/formatters';

/**
 * KPI Card Component
 */
const KPICard = ({ title, value, subtitle, icon: Icon, color = 'error', loading }) => {
  if (loading) {
    return (
      <Paper sx={{ p: '1.0rem', height: '100%' }}>
        <Skeleton variant="text" width="60%" />
        <Skeleton variant="text" width="40%" height={40} />
        <Skeleton variant="text" width="80%" />
      </Paper>
    );
  }

  return (
    <Paper
      sx={{
        p: '1.0rem',
        height: '100%',
        borderRight: 4,
        borderColor: `${color}.main`
      }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Typography variant="subtitle2" color="text.secondary">
          {title}
        </Typography>
        <Icon sx={{ color: `${color}.main`, opacity: 0.7 }} />
      </Box>
      <Typography variant="h4" sx={{ my: 1, color: `${color}.main` }}>
        {value}
      </Typography>
      {subtitle && (
        <Typography variant="caption" color="text.secondary">
          {subtitle}
        </Typography>
      )}
    </Paper>
  );
};

KPICard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  subtitle: PropTypes.string,
  icon: PropTypes.elementType.isRequired,
  color: PropTypes.string,
  loading: PropTypes.bool
};

/**
 * Breakdown Table Component
 */
const BreakdownTable = ({ title, icon: Icon, data, nameKey, loading }) => {
  if (loading) {
    return (
      <Paper sx={{ p: '1.0rem' }}>
        <Skeleton variant="text" width="40%" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} variant="rectangular" height={40} sx={{ mb: 0.5 }} />
        ))}
      </Paper>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Paper sx={{ p: '1.0rem' }}>
        <Typography variant="subtitle2" sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.0rem' }}>
          <Icon fontSize="small" color="action" />
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          لا توجد بيانات
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: '1.0rem' }}>
      <Typography variant="subtitle2" sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.0rem' }}>
        <Icon fontSize="small" color="action" />
        {title}
      </Typography>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>البند</TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                العدد
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                المبلغ
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold', minWidth: '7.5rem' }}>
                النسبة
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.slice(0, 10).map((row, index) => (
              <TableRow key={index} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
                <TableCell>
                  <Typography variant="body2" noWrap sx={{ maxWidth: '12.5rem' }}>
                    {row[nameKey]}
                  </Typography>
                </TableCell>
                <TableCell align="center">
                  <Chip label={row.count} size="small" color="error" variant="outlined" />
                </TableCell>
                <TableCell align="right">
                  <Typography variant="body2">{formatCurrency(row.amount)}</Typography>
                </TableCell>
                <TableCell align="center">
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Box sx={{ flexGrow: 1 }}>
                      <LinearProgress variant="determinate" value={row.percentage} color="error" sx={{ height: '0.375rem', borderRadius: '0.1875rem' }} />
                    </Box>
                    <Typography variant="caption" sx={{ minWidth: '2.5rem' }}>
                      {row.percentage.toFixed(1)}%
                    </Typography>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {data.length > 10 && (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          عرض أول 10 من {data.length}
        </Typography>
      )}
    </Paper>
  );
};

BreakdownTable.propTypes = {
  title: PropTypes.string.isRequired,
  icon: PropTypes.elementType.isRequired,
  data: PropTypes.array,
  nameKey: PropTypes.string.isRequired,
  loading: PropTypes.bool
};

/**
 * Section 4: Rejections Analysis (تحليل الرفض)
 *
 * KPIs:
 * - Total Rejected Claims
 * - Rejected Amount
 * - Rejection Rate %
 *
 * Breakdown:
 * - By BenefitPolicy
 * - By Reason
 * - By Service Category
 */
const RejectionsAnalysis = ({ rejectionsAnalysis = {}, loading = false }) => {
  const { kpis = {}, byPolicy = [], byReason = [], byCategory = [] } = rejectionsAnalysis;

  // Loading state
  if (loading) {
    return (
      <Box>
        <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
          {[1, 2, 3].map((i) => (
            <Grid key={i} size={{ xs: 12, sm: 4 }}>
              <Paper sx={{ p: '1.0rem' }}>
                <Skeleton variant="text" width="60%" />
                <Skeleton variant="text" width="40%" height={40} />
              </Paper>
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  // No rejections - good state
  if (kpis.totalRejectedClaims === 0) {
    return (
      <Alert severity="success" variant="outlined">
        <Typography variant="body2">✅ لا توجد مطالبات مرفوضة - أداء ممتاز!</Typography>
      </Alert>
    );
  }

  return (
    <Box>
      {/* KPIs Row */}
      <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <KPICard
            title="إجمالي المطالبات المرفوضة"
            value={kpis.totalRejectedClaims?.toLocaleString('en-US') || '0'}
            subtitle="عدد المطالبات بحالة REJECTED"
            icon={RejectedIcon}
            color="error"
            loading={loading}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <KPICard
            title="مبلغ المطالبات المرفوضة"
            value={formatCurrency(kpis.rejectedAmount)}
            subtitle="إجمالي المبالغ المطلوبة المرفوضة"
            icon={AmountIcon}
            color="error"
            loading={loading}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <KPICard
            title="نسبة الرفض"
            value={`${kpis.rejectionRate?.toFixed(1) || '0'}%`}
            subtitle="نسبة المرفوض من إجمالي المطالبات"
            icon={RateIcon}
            color={kpis.rejectionRate > 20 ? 'error' : kpis.rejectionRate > 10 ? 'warning' : 'success'}
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* High Rejection Warning */}
      {kpis.rejectionRate > 15 && (
        <Alert severity="warning" sx={{ mb: '1.5rem' }}>
          <Typography variant="body2">
            ⚠️ نسبة الرفض مرتفعة ({kpis.rejectionRate.toFixed(1)}%) - يُنصح بمراجعة أسباب الرفض وتحسين إجراءات التقديم
          </Typography>
        </Alert>
      )}

      <Divider sx={{ my: '1.0rem' }} />

      {/* Breakdowns */}
      <Typography variant="subtitle1" gutterBottom sx={{ mb: '1.0rem' }}>
        تحليل تفصيلي للرفض
      </Typography>

      <Grid container spacing={2}>
        {/* By Policy */}
        <Grid size={{ xs: 12, md: 4 }}>
          <BreakdownTable title="حسب الوثيقة" icon={PolicyIcon} data={byPolicy} nameKey="name" loading={loading} />
        </Grid>

        {/* By Reason */}
        <Grid size={{ xs: 12, md: 4 }}>
          <BreakdownTable title="حسب السبب" icon={ReasonIcon} data={byReason} nameKey="reason" loading={loading} />
        </Grid>

        {/* By Category */}
        <Grid size={{ xs: 12, md: 4 }}>
          <BreakdownTable title="حسب فئة الخدمة" icon={CategoryIcon} data={byCategory} nameKey="category" loading={loading} />
        </Grid>
      </Grid>

      {/* Note */}
      <Box sx={{ mt: '1.0rem', p: '0.75rem', bgcolor: 'action.hover', borderRadius: 1 }}>
        <Typography variant="caption" color="text.secondary">
          ⚠️ السبب يُستخرج من حقل "تعليق المراجع" (reviewerComment). إذا لم يتوفر السبب يظهر "غير محدد"
        </Typography>
      </Box>
    </Box>
  );
};

RejectionsAnalysis.propTypes = {
  rejectionsAnalysis: PropTypes.shape({
    kpis: PropTypes.shape({
      totalRejectedClaims: PropTypes.number,
      rejectedAmount: PropTypes.number,
      rejectionRate: PropTypes.number
    }),
    byPolicy: PropTypes.arrayOf(
      PropTypes.shape({
        name: PropTypes.string,
        count: PropTypes.number,
        amount: PropTypes.number,
        percentage: PropTypes.number
      })
    ),
    byReason: PropTypes.arrayOf(
      PropTypes.shape({
        reason: PropTypes.string,
        count: PropTypes.number,
        amount: PropTypes.number,
        percentage: PropTypes.number
      })
    ),
    byCategory: PropTypes.arrayOf(
      PropTypes.shape({
        category: PropTypes.string,
        count: PropTypes.number,
        amount: PropTypes.number,
        percentage: PropTypes.number
      })
    )
  }),
  loading: PropTypes.bool
};

export default RejectionsAnalysis;



