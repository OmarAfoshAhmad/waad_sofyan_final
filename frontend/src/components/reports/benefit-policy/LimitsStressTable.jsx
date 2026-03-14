import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Typography,
  Skeleton,
  LinearProgress,
  Alert,
  Tooltip
} from '@mui/material';
import { CheckCircle as HealthyIcon, Warning as WarningIcon, Error as CriticalIcon } from '@mui/icons-material';
import { formatCurrencyLYD as formatCurrency } from 'utils/formatters';

/**
 * Get status icon and color
 */
const getStatusConfig = (status) => {
  switch (status) {
    case 'critical':
      return {
        icon: <CriticalIcon fontSize="small" />,
        color: 'error',
        bgColor: 'error.lighter'
      };
    case 'warning':
      return {
        icon: <WarningIcon fontSize="small" />,
        color: 'warning',
        bgColor: 'warning.lighter'
      };
    default: // healthy
      return {
        icon: <HealthyIcon fontSize="small" />,
        color: 'success',
        bgColor: 'success.lighter'
      };
  }
};

/**
 * Section 3: Limits Pressure Analysis (ضغط الحدود)
 *
 * Table: Policy Limits Stress
 * - BenefitPolicy
 * - Annual Limit
 * - Used Amount
 * - Remaining
 * - Utilization %
 * - Status (Healthy / Warning / Critical)
 *
 * Status Logic:
 * < 60% → Healthy (صحي)
 * 60%–85% → Warning (تحذير)
 * > 85% → Critical (حرج)
 */
const LimitsStressTable = ({ data = [], loading = false }) => {
  // Loading skeleton
  if (loading) {
    return (
      <Paper sx={{ p: '1.0rem' }}>
        <Skeleton variant="rectangular" height={40} sx={{ mb: 1 }} />
        {[1, 2, 3, 4, 5].map((i) => (
          <Skeleton key={i} variant="rectangular" height={52} sx={{ mb: 0.5 }} />
        ))}
      </Paper>
    );
  }

  // Empty state
  if (!data || data.length === 0) {
    return (
      <Alert severity="info" variant="outlined">
        <Typography variant="body2">لا توجد بيانات لتحليل ضغط الحدود</Typography>
      </Alert>
    );
  }

  // Summary stats
  const criticalCount = data.filter((d) => d.status === 'critical').length;
  const warningCount = data.filter((d) => d.status === 'warning').length;
  const healthyCount = data.filter((d) => d.status === 'healthy').length;

  return (
    <Box>
      {/* Summary Chips */}
      <Box sx={{ display: 'flex', gap: 1, mb: '1.0rem', flexWrap: 'wrap' }}>
        <Chip
          icon={<CriticalIcon />}
          label={`حرج: ${criticalCount}`}
          color="error"
          size="small"
          variant={criticalCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip
          icon={<WarningIcon />}
          label={`تحذير: ${warningCount}`}
          color="warning"
          size="small"
          variant={warningCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip icon={<HealthyIcon />} label={`صحي: ${healthyCount}`} color="success" size="small" variant="outlined" />
      </Box>

      {/* Critical Alert */}
      {criticalCount > 0 && (
        <Alert severity="error" sx={{ mb: '1.0rem' }}>
          <Typography variant="body2">⚠️ يوجد {criticalCount} وثيقة/وثائق تجاوزت 85% من الحد السنوي - تتطلب مراجعة فورية</Typography>
        </Alert>
      )}

      {/* Table */}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.hover' }}>
              <TableCell sx={{ fontWeight: 'bold' }}>الوثيقة</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>الشريك</TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                الحد السنوي
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                المستهلك
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                المتبقي
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold', minWidth: '9.375rem' }}>
                نسبة الاستخدام
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                الحالة
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((row) => {
              const statusConfig = getStatusConfig(row.status);

              return (
                <TableRow
                  key={row.id}
                  sx={{
                    bgcolor: row.status === 'critical' ? 'error.lighter' : row.status === 'warning' ? 'warning.lighter' : 'inherit',
                    '&:hover': { bgcolor: 'action.hover' }
                  }}
                >
                  <TableCell>
                    <Typography variant="body2" fontWeight="medium">
                      {row.policyName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {row.policyCode}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{row.employerName}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {row.memberCount} عضو
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2">{formatCurrency(row.annualLimit)}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" color={row.status === 'critical' ? 'error.main' : 'inherit'}>
                      {formatCurrency(row.usedAmount)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" color={row.remaining < 0 ? 'error.main' : 'success.main'}>
                      {formatCurrency(row.remaining)}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Box sx={{ flexGrow: 1, minWidth: '5.0rem' }}>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(row.utilizationPercent, 100)}
                          color={statusConfig.color}
                          sx={{ height: '0.375rem', borderRadius: '0.375rem' }}
                        />
                      </Box>
                      <Typography variant="body2" fontWeight="bold" color={`${statusConfig.color}.main`} sx={{ minWidth: '2.8125rem' }}>
                        {row.utilizationPercent.toFixed(1)}%
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title={`${row.statusLabel} - ${row.utilizationPercent.toFixed(1)}%`}>
                      <Chip icon={statusConfig.icon} label={row.statusLabel} color={statusConfig.color} size="small" />
                    </Tooltip>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Legend */}
      <Box sx={{ mt: '1.0rem', p: '0.75rem', bgcolor: 'action.hover', borderRadius: 1 }}>
        <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
          تصنيف حالة الضغط:
        </Typography>
        <Box sx={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
          <Typography variant="caption">
            <Chip label="صحي" color="success" size="small" sx={{ mr: 0.5 }} />
            أقل من 60%
          </Typography>
          <Typography variant="caption">
            <Chip label="تحذير" color="warning" size="small" sx={{ mr: 0.5 }} />
            60% - 85%
          </Typography>
          <Typography variant="caption">
            <Chip label="حرج" color="error" size="small" sx={{ mr: 0.5 }} />
            أكثر من 85%
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
          ⚠️ الحد السنوي = حد الوثيقة × عدد الأعضاء (تقديري)
        </Typography>
      </Box>
    </Box>
  );
};

LimitsStressTable.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      policyName: PropTypes.string,
      policyCode: PropTypes.string,
      employerName: PropTypes.string,
      memberCount: PropTypes.number,
      annualLimit: PropTypes.number,
      usedAmount: PropTypes.number,
      remaining: PropTypes.number,
      utilizationPercent: PropTypes.number,
      status: PropTypes.oneOf(['healthy', 'warning', 'critical']),
      statusLabel: PropTypes.string,
      statusColor: PropTypes.string
    })
  ),
  loading: PropTypes.bool
};

export default LimitsStressTable;


