import PropTypes from 'prop-types';
import { Grid, Card, CardContent, Typography, Box, Skeleton, Tooltip } from '@mui/material';
import {
  Receipt as ClaimsIcon,
  CheckCircle as ApprovedIcon,
  TrendingUp as UtilizationIcon,
  Analytics as AvgIcon,
  Info as InfoIcon
} from '@mui/icons-material';
import { formatCurrencyLYD, formatPercentage } from 'utils/formatters';

/**
 * KPI Card Configuration for Utilization Section
 */
const UTILIZATION_KPI_CONFIG = [
  {
    key: 'totalClaimsAmount',
    title: 'إجمالي المطالبات',
    titleEn: 'Total Claims Amount',
    description: 'SUM(requestedAmount)',
    icon: ClaimsIcon,
    color: '#1565c0',
    bgColor: '#e3f2fd',
    format: formatCurrencyLYD
  },
  {
    key: 'approvedAmount',
    title: 'المبالغ الموافق عليها',
    titleEn: 'Approved Amount',
    description: 'SUM(approvedAmount)',
    icon: ApprovedIcon,
    color: '#2e7d32',
    bgColor: '#e8f5e9',
    format: formatCurrencyLYD
  },
  {
    key: 'utilizationPercent',
    title: 'نسبة الاستخدام',
    titleEn: 'Utilization %',
    description: 'Approved / Annual Limit',
    icon: UtilizationIcon,
    color: '#7b1fa2',
    bgColor: '#f3e5f5',
    format: (val) => formatPercentage(val),
    showWarning: (val) => val > 80
  },
  {
    key: 'avgUtilizationPerPolicy',
    title: 'متوسط الاستخدام / وثيقة',
    titleEn: 'Avg Utilization / Policy',
    description: 'AVG per policy',
    icon: AvgIcon,
    color: '#0288d1',
    bgColor: '#e1f5fe',
    format: formatCurrencyLYD
  }
];

/**
 * Single Utilization KPI Card Component
 */
const UtilizationKPICard = ({ config, value, loading }) => {
  const Icon = config.icon;
  const showWarning = config.showWarning && config.showWarning(value);

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        border: '1px solid',
        borderColor: showWarning ? 'warning.main' : 'divider',
        borderRadius: '0.25rem',
        transition: 'box-shadow 0.2s',
        '&:hover': {
          boxShadow: 2
        }
      }}
    >
      <CardContent sx={{ p: '1.25rem' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
          <Box sx={{ flex: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  fontSize: '0.75rem',
                  fontWeight: 500
                }}
              >
                {config.title}
              </Typography>
              <Tooltip title={config.description} arrow>
                <InfoIcon sx={{ fontSize: '0.75rem', color: 'text.disabled', cursor: 'help' }} />
              </Tooltip>
            </Box>

            {loading ? (
              <Skeleton variant="text" width={80} height={40} />
            ) : (
              <Typography
                variant="h4"
                sx={{
                  fontWeight: 700,
                  color: showWarning ? 'warning.main' : config.color,
                  lineHeight: 1.2,
                  fontSize: '1.5rem'
                }}
              >
                {config.format(value)}
              </Typography>
            )}

            <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.75rem' }}>
              {config.titleEn}
            </Typography>
          </Box>

          <Box
            sx={{
              width: '2.75rem',
              height: '2.75rem',
              borderRadius: '0.25rem',
              backgroundColor: showWarning ? 'warning.lighter' : config.bgColor,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}
          >
            <Icon sx={{ color: showWarning ? 'warning.main' : config.color, fontSize: '1.375rem' }} />
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};

UtilizationKPICard.propTypes = {
  config: PropTypes.shape({
    key: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    titleEn: PropTypes.string.isRequired,
    description: PropTypes.string.isRequired,
    icon: PropTypes.elementType.isRequired,
    color: PropTypes.string.isRequired,
    bgColor: PropTypes.string.isRequired,
    format: PropTypes.func.isRequired,
    showWarning: PropTypes.func
  }).isRequired,
  value: PropTypes.number,
  loading: PropTypes.bool
};

/**
 * Utilization KPIs Component (Section 2)
 *
 * Displays 4 KPI cards for Coverage Utilization:
 * 1. Total Claims Amount - SUM(requestedAmount)
 * 2. Approved Amount - SUM(approvedAmount)
 * 3. Utilization % - Approved / Annual Limit
 * 4. Avg Utilization / Policy - AVG
 *
 * ⚠️ Utilization تقريبي (Client-side)
 */
const UtilizationKPIs = ({ utilizationKpis = {}, loading = false }) => {
  return (
    <Box>
      <Grid container spacing={2}>
        {UTILIZATION_KPI_CONFIG.map((config) => (
          <Grid key={config.key} size={{ xs: 12, sm: 6, md: 3 }}>
            <UtilizationKPICard config={config} value={utilizationKpis[config.key] ?? 0} loading={loading} />
          </Grid>
        ))}
      </Grid>

      {/* Approximation Notice */}
      <Box
        sx={{
          mt: '0.75rem',
          p: '0.75rem',
          bgcolor: 'action.hover',
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 1
        }}
      >
        <InfoIcon sx={{ fontSize: '1.0rem', color: 'info.main' }} />
        <Typography variant="caption" color="text.secondary">
          ⚠️ نسبة الاستخدام تقريبية (Client-side) - تعتمد على maxClaimAmount كحد سنوي تقريبي
        </Typography>
      </Box>
    </Box>
  );
};

UtilizationKPIs.propTypes = {
  utilizationKpis: PropTypes.shape({
    totalClaimsAmount: PropTypes.number,
    approvedAmount: PropTypes.number,
    utilizationPercent: PropTypes.number,
    avgUtilizationPerPolicy: PropTypes.number,
    totalAnnualLimit: PropTypes.number,
    claimsCount: PropTypes.number
  }),
  loading: PropTypes.bool
};

export default UtilizationKPIs;




