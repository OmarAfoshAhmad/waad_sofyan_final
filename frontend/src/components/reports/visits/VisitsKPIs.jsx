import PropTypes from 'prop-types';
import { Grid, Card, CardContent, Typography, Skeleton, Stack, Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// MUI Icons
import EventIcon from '@mui/icons-material/Event';
import TodayIcon from '@mui/icons-material/Today';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import PersonIcon from '@mui/icons-material/Person';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

/**
 * KPI Card Component
 */
const KPICard = ({ title, value, subtitle, icon: Icon, color, loading }) => {
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
          width: '5.0rem',
          height: '5.0rem',
          background: `radial-gradient(circle at top right, ${theme.palette[color]?.light || theme.palette.primary.light}40, transparent)`,
          borderRadius: '0 0 0 100%'
        }
      }}
    >
      <CardContent sx={{ p: '1.0rem' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="caption" color="text.secondary" gutterBottom display="block">
              {title}
            </Typography>

            {loading ? (
              <Skeleton variant="text" width={60} height={36} />
            ) : (
              <>
                <Typography variant="h4" fontWeight={600}>
                  {typeof value === 'number' ? value.toLocaleString('en-US') : value}
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
              p: 1,
              borderRadius: '0.125rem',
              bgcolor: `${theme.palette[color]?.main || theme.palette.primary.main}15`
            }}
          >
            <Icon
              sx={{
                fontSize: '1.5rem',
                color: theme.palette[color]?.main || theme.palette.primary.main
              }}
            />
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
};

/**
 * VisitsKPIs Component
 *
 * Displays KPI cards for visits report
 *
 * @param {Object} kpis - KPI values
 * @param {boolean} loading - Loading state
 */
const VisitsKPIs = ({ kpis, loading }) => {
  const kpiCards = [
    {
      title: 'إجمالي الزيارات',
      value: kpis.totalVisits,
      icon: EventIcon,
      color: 'primary'
    },
    {
      title: 'زيارات اليوم',
      value: kpis.visitsToday,
      icon: TodayIcon,
      color: 'success'
    },
    {
      title: 'زيارات هذا الشهر',
      value: kpis.visitsThisMonth,
      icon: CalendarMonthIcon,
      color: 'info'
    },
    {
      title: 'متوسط الزيارات / عضو',
      value: kpis.avgVisitsPerMember,
      icon: PersonIcon,
      color: 'warning'
    },
    {
      title: 'عدد مقدمي الخدمة',
      value: kpis.distinctProviders,
      icon: LocalHospitalIcon,
      color: 'secondary'
    }
  ];

  return (
    <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
      {kpiCards.map((kpi, index) => (
        <Grid key={index} size={{ xs: 6, sm: 4, md: 2.4 }}>
          <KPICard title={kpi.title} value={kpi.value} subtitle={kpi.subtitle} icon={kpi.icon} color={kpi.color} loading={loading} />
        </Grid>
      ))}
    </Grid>
  );
};

VisitsKPIs.propTypes = {
  kpis: PropTypes.shape({
    totalVisits: PropTypes.number,
    visitsToday: PropTypes.number,
    visitsThisMonth: PropTypes.number,
    avgVisitsPerMember: PropTypes.number,
    distinctProviders: PropTypes.number
  }).isRequired,
  loading: PropTypes.bool
};

VisitsKPIs.defaultProps = {
  loading: false
};

export default VisitsKPIs;

