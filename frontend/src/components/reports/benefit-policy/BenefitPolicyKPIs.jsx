import PropTypes from 'prop-types';
import { Grid, Card, CardContent, Typography, Box, Skeleton, Chip } from '@mui/material';
import {
  Policy as PolicyIcon,
  CheckCircle as ActiveIcon,
  People as PeopleIcon,
  Percent as AvgIcon,
  Warning as WarningIcon
} from '@mui/icons-material';
import { formatNumber } from 'utils/formatters';

/**
 * KPI Card Configuration
 */
const KPI_CONFIG = [
  {
    key: 'totalPolicies',
    title: 'إجمالي الوثائق',
    titleEn: 'Total Benefit Policies',
    icon: PolicyIcon,
    color: 'primary.main',
    bgColor: 'primary.lighter',
    format: (val) => formatNumber(val)
  },
  {
    key: 'activePolicies',
    title: 'الوثائق النشطة',
    titleEn: 'Active Policies',
    icon: ActiveIcon,
    color: '#2e7d32',
    bgColor: '#e8f5e9',
    format: (val) => formatNumber(val)
  },
  {
    key: 'membersCovered',
    title: 'الأعضاء المغطون',
    titleEn: 'Members Covered',
    icon: PeopleIcon,
    color: '#7b1fa2',
    bgColor: '#f3e5f5',
    format: (val) => formatNumber(val)
  },
  {
    key: 'avgMembersPerPolicy',
    title: 'متوسط الأعضاء / وثيقة',
    titleEn: 'Avg Members / Policy',
    icon: AvgIcon,
    color: 'info.main',
    bgColor: 'info.lighter',
    format: (val) => formatNumber(val)
  },
  {
    key: 'policiesWithNoUsage',
    title: 'وثائق بدون استخدام',
    titleEn: 'Policies With No Usage',
    icon: WarningIcon,
    color: '#ed6c02',
    bgColor: '#fff3e0',
    format: (val) => formatNumber(val),
    showWarning: true
  }
];

/**
 * Single KPI Card Component
 */
const KPICard = ({ config, value, loading }) => {
  const Icon = config.icon;

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        border: '1px solid',
        borderColor: 'divider',
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
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                display: 'block',
                mb: 0.5,
                fontSize: '0.75rem',
                fontWeight: 500
              }}
            >
              {config.title}
            </Typography>

            {loading ? (
              <Skeleton variant="text" width={60} height={40} />
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography
                  variant="h4"
                  sx={{
                    fontWeight: 700,
                    color: config.color,
                    lineHeight: 1.2
                  }}
                >
                  {config.format(value)}
                </Typography>
                {config.showWarning && value > 0 && (
                  <Chip label="يحتاج مراجعة" size="small" color="warning" sx={{ fontSize: '0.75rem', height: '1.25rem' }} />
                )}
              </Box>
            )}

            <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.75rem' }}>
              {config.titleEn}
            </Typography>
          </Box>

          <Box
            sx={{
              width: '3.0rem',
              height: '3.0rem',
              borderRadius: '0.25rem',
              backgroundColor: config.bgColor,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}
          >
            <Icon sx={{ color: config.color, fontSize: '1.5rem' }} />
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};

KPICard.propTypes = {
  config: PropTypes.shape({
    key: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    titleEn: PropTypes.string.isRequired,
    icon: PropTypes.elementType.isRequired,
    color: PropTypes.string.isRequired,
    bgColor: PropTypes.string.isRequired,
    format: PropTypes.func.isRequired,
    showWarning: PropTypes.bool
  }).isRequired,
  value: PropTypes.number,
  loading: PropTypes.bool
};

/**
 * BenefitPolicy KPIs Component
 *
 * Displays 5 KPI cards:
 * 1. Total Benefit Policies - عدد الخطط
 * 2. Active Policies - الخطط الفعالة
 * 3. Members Covered - عدد الأعضاء المرتبطين
 * 4. Avg Members / Policy - متوسط التغطية
 * 5. Policies With No Usage - خطط لم تُستخدم
 */
const BenefitPolicyKPIs = ({ kpis = {}, loading = false }) => {
  return (
    <Grid container spacing={2}>
      {KPI_CONFIG.map((config) => (
        <Grid key={config.key} size={{ xs: 12, sm: 6, md: 2.4 }}>
          <KPICard config={config} value={kpis[config.key] ?? 0} loading={loading} />
        </Grid>
      ))}
    </Grid>
  );
};

BenefitPolicyKPIs.propTypes = {
  kpis: PropTypes.shape({
    totalPolicies: PropTypes.number,
    activePolicies: PropTypes.number,
    membersCovered: PropTypes.number,
    avgMembersPerPolicy: PropTypes.number,
    policiesWithNoUsage: PropTypes.number
  }),
  loading: PropTypes.bool
};

export default BenefitPolicyKPIs;




