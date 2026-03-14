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
  Alert,
  Tooltip,
  LinearProgress,
  IconButton,
  Collapse
} from '@mui/material';
import {
  TrendingUp as ExpandIcon,
  TrendingDown as ReduceIcon,
  Build as RedesignIcon,
  CheckCircle as ExcellentIcon,
  ThumbUp as GoodIcon,
  HelpOutline as ReviewIcon,
  Block as UnusedIcon,
  KeyboardArrowDown as ArrowDownIcon,
  KeyboardArrowUp as ArrowUpIcon
} from '@mui/icons-material';
import { useState } from 'react';
import { formatCurrencyLYD as formatCurrency } from 'utils/formatters';

/**
 * Get recommendation icon
 */
const getRecommendationIcon = (recommendation) => {
  switch (recommendation) {
    case 'توسيع الحدود':
      return <ExpandIcon fontSize="small" />;
    case 'تقليص أو دمج':
      return <ReduceIcon fontSize="small" />;
    case 'إعادة تصميم':
      return <RedesignIcon fontSize="small" />;
    case 'أداء ممتاز':
      return <ExcellentIcon fontSize="small" />;
    case 'أداء جيد':
      return <GoodIcon fontSize="small" />;
    case 'غير مستخدم':
      return <UnusedIcon fontSize="small" />;
    default:
      return <ReviewIcon fontSize="small" />;
  }
};

/**
 * Expandable Row Component
 */
const ExpandableRow = ({ row }) => {
  const [open, setOpen] = useState(false);

  return (
    <>
      <TableRow
        sx={{
          '&:hover': { bgcolor: 'action.hover' },
          bgcolor: row.memberCount === 0 ? 'error.lighter' : 'inherit'
        }}
      >
        <TableCell>
          <IconButton size="small" onClick={() => setOpen(!open)}>
            {open ? <ArrowUpIcon /> : <ArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Typography variant="body2" fontWeight="medium">
            {row.policyName}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {row.policyCode}
          </Typography>
        </TableCell>
        <TableCell align="center">
          <Chip label={row.memberCount} size="small" color={row.memberCount > 0 ? 'default' : 'error'} variant="outlined" />
        </TableCell>
        <TableCell align="center">
          <Typography variant="body2">{row.claimsCount}</Typography>
        </TableCell>
        <TableCell align="center">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <LinearProgress
              variant="determinate"
              value={Math.min(row.approvalRate, 100)}
              color={row.approvalRate >= 80 ? 'success' : row.approvalRate >= 50 ? 'warning' : 'error'}
              sx={{ flexGrow: 1, height: '0.375rem', borderRadius: '0.1875rem', minWidth: '3.125rem' }}
            />
            <Typography variant="caption" sx={{ minWidth: '2.5rem' }}>
              {row.approvalRate}%
            </Typography>
          </Box>
        </TableCell>
        <TableCell align="right">
          <Typography variant="body2">{formatCurrency(row.avgClaimAmount)}</Typography>
        </TableCell>
        <TableCell align="center">
          <Tooltip title={`${row.utilizationPercent}% من الحد السنوي`}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <LinearProgress
                variant="determinate"
                value={Math.min(row.utilizationPercent, 100)}
                color={row.utilizationPercent > 85 ? 'error' : row.utilizationPercent > 60 ? 'warning' : 'success'}
                sx={{ flexGrow: 1, height: '0.375rem', borderRadius: '0.1875rem', minWidth: '3.125rem' }}
              />
              <Typography variant="caption" sx={{ minWidth: '2.5rem' }}>
                {row.utilizationPercent}%
              </Typography>
            </Box>
          </Tooltip>
        </TableCell>
        <TableCell align="center">
          <Typography
            variant="body2"
            color={row.rejectionRate > 30 ? 'error.main' : row.rejectionRate > 15 ? 'warning.main' : 'text.primary'}
          >
            {row.rejectionRate}%
          </Typography>
        </TableCell>
        <TableCell align="center">
          <Tooltip title={`نقاط الفعالية: ${row.effectivenessScore}`}>
            <Chip
              icon={getRecommendationIcon(row.recommendation)}
              label={row.recommendation}
              color={row.recommendationColor}
              size="small"
            />
          </Tooltip>
        </TableCell>
      </TableRow>

      {/* Expanded Details */}
      <TableRow>
        <TableCell colSpan={9} sx={{ py: 0 }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ py: '1.0rem', px: '1.5rem', bgcolor: 'action.hover' }}>
              <Typography variant="subtitle2" gutterBottom>
                تفاصيل إضافية
              </Typography>
              <Box sx={{ display: 'flex', gap: '2.0rem', flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    الشريك
                  </Typography>
                  <Typography variant="body2">{row.employerName}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    حالة الوثيقة
                  </Typography>
                  <Typography variant="body2">{row.status}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    الحد السنوي التقديري
                  </Typography>
                  <Typography variant="body2">{formatCurrency(row._annualLimit)}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    المطالبات المعتمدة
                  </Typography>
                  <Typography variant="body2">{row._rawClaimsData?.approvedClaims || 0}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    المطالبات المرفوضة
                  </Typography>
                  <Typography variant="body2">{row._rawClaimsData?.rejectedClaims || 0}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    إجمالي المبالغ المطلوبة
                  </Typography>
                  <Typography variant="body2">{formatCurrency(row._rawClaimsData?.totalRequestedAmount)}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    إجمالي المبالغ المعتمدة
                  </Typography>
                  <Typography variant="body2">{formatCurrency(row._rawClaimsData?.totalApprovedAmount)}</Typography>
                </Box>
              </Box>
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
};

ExpandableRow.propTypes = {
  row: PropTypes.object.isRequired
};

/**
 * Section 5: BenefitPolicy Effectiveness Ranking
 *
 * Table: Policy Performance
 * - Members
 * - Claims Count
 * - Approval Rate
 * - Avg Claim Amount
 * - Utilization %
 * - Rejection %
 *
 * Goal: Identify policies to expand, reduce, or redesign
 */
const PolicyEffectivenessTable = ({ data = [], loading = false }) => {
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
        <Typography variant="body2">لا توجد بيانات لتحليل فعالية الوثائق</Typography>
      </Alert>
    );
  }

  // Summary stats
  const excellentCount = data.filter((d) => d.recommendation === 'أداء ممتاز').length;
  const goodCount = data.filter((d) => d.recommendation === 'أداء جيد').length;
  const expandCount = data.filter((d) => d.recommendation === 'توسيع الحدود').length;
  const reduceCount = data.filter((d) => d.recommendation === 'تقليص أو دمج').length;
  const redesignCount = data.filter((d) => d.recommendation === 'إعادة تصميم').length;
  const unusedCount = data.filter((d) => d.recommendation === 'غير مستخدم').length;

  return (
    <Box>
      {/* Summary Chips */}
      <Box sx={{ display: 'flex', gap: 1, mb: '1.0rem', flexWrap: 'wrap' }}>
        <Chip
          icon={<ExcellentIcon />}
          label={`أداء ممتاز: ${excellentCount}`}
          color="success"
          size="small"
          variant={excellentCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip icon={<GoodIcon />} label={`أداء جيد: ${goodCount}`} color="success" size="small" variant="outlined" />
        <Chip
          icon={<ExpandIcon />}
          label={`يحتاج توسيع: ${expandCount}`}
          color="warning"
          size="small"
          variant={expandCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip
          icon={<ReduceIcon />}
          label={`يحتاج تقليص: ${reduceCount}`}
          color="info"
          size="small"
          variant={reduceCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip
          icon={<RedesignIcon />}
          label={`يحتاج إعادة تصميم: ${redesignCount}`}
          color="error"
          size="small"
          variant={redesignCount > 0 ? 'filled' : 'outlined'}
        />
        <Chip icon={<UnusedIcon />} label={`غير مستخدم: ${unusedCount}`} color="error" size="small" variant="outlined" />
      </Box>

      {/* Recommendations Alert */}
      {(redesignCount > 0 || unusedCount > 0) && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          <Typography variant="body2">
            ⚠️ يوجد {redesignCount + unusedCount} وثيقة/وثائق تحتاج مراجعة عاجلة ({redesignCount > 0 ? `${redesignCount} للتصميم` : ''}
            {redesignCount > 0 && unusedCount > 0 ? '، ' : ''}
            {unusedCount > 0 ? `${unusedCount} غير مستخدمة` : ''})
          </Typography>
        </Alert>
      )}

      {/* Table */}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.hover' }}>
              <TableCell sx={{ width: '3.125rem' }} />
              <TableCell sx={{ fontWeight: 'bold' }}>الوثيقة</TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                الأعضاء
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                المطالبات
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold', minWidth: '7.5rem' }}>
                نسبة القبول
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                متوسط المطالبة
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold', minWidth: '7.5rem' }}>
                الاستخدام
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                الرفض
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                التوصية
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((row) => (
              <ExpandableRow key={row.id} row={row} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Legend */}
      <Box sx={{ mt: '1.0rem', p: '0.75rem', bgcolor: 'action.hover', borderRadius: 1 }}>
        <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
          معايير التوصية:
        </Typography>
        <Box sx={{ display: 'flex', gap: '1.0rem', flexWrap: 'wrap' }}>
          <Typography variant="caption">
            <Chip label="أداء ممتاز" color="success" size="small" sx={{ mr: 0.5 }} />
            قبول &gt;80%، استخدام 30-70%
          </Typography>
          <Typography variant="caption">
            <Chip label="توسيع الحدود" color="warning" size="small" sx={{ mr: 0.5 }} />
            استخدام &gt;85%
          </Typography>
          <Typography variant="caption">
            <Chip label="إعادة تصميم" color="error" size="small" sx={{ mr: 0.5 }} />
            رفض &gt;40%
          </Typography>
          <Typography variant="caption">
            <Chip label="تقليص" color="info" size="small" sx={{ mr: 0.5 }} />
            استخدام &lt;20%، مطالبات &lt;5
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
          ⚠️ التوصيات مبنية على تحليل البيانات المتاحة وتحتاج مراجعة إدارية قبل التطبيق
        </Typography>
      </Box>
    </Box>
  );
};

PolicyEffectivenessTable.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      policyName: PropTypes.string,
      policyCode: PropTypes.string,
      employerName: PropTypes.string,
      status: PropTypes.string,
      memberCount: PropTypes.number,
      claimsCount: PropTypes.number,
      approvalRate: PropTypes.number,
      avgClaimAmount: PropTypes.number,
      utilizationPercent: PropTypes.number,
      rejectionRate: PropTypes.number,
      effectivenessScore: PropTypes.number,
      recommendation: PropTypes.string,
      recommendationColor: PropTypes.string
    })
  ),
  loading: PropTypes.bool
};

export default PolicyEffectivenessTable;


