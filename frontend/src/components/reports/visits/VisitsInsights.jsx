import PropTypes from 'prop-types';
import { Grid, Card, CardContent, Typography, Box, Stack, Skeleton, Chip, LinearProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// MUI Icons
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import PersonIcon from '@mui/icons-material/Person';

/**
 * Insight Card Component
 */
const InsightCard = ({ title, icon: Icon, items, loading, color, maxItems = 5 }) => {
  const theme = useTheme();

  // Calculate max count for progress bar
  const maxCount = items.length > 0 ? Math.max(...items.map((i) => i.count)) : 1;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: '1.0rem' }}>
          <Icon sx={{ color: theme.palette[color]?.main || theme.palette.primary.main }} />
          <Typography variant="subtitle1" fontWeight={600}>
            {title}
          </Typography>
        </Stack>

        {loading ? (
          <Stack spacing={1.5}>
            {Array.from({ length: maxItems }).map((_, i) => (
              <Skeleton key={i} variant="rounded" height={36} />
            ))}
          </Stack>
        ) : items.length === 0 ? (
          <Box sx={{ py: '1.5rem', textAlign: 'center' }}>
            <Typography variant="body2" color="text.disabled">
              لا توجد بيانات
            </Typography>
          </Box>
        ) : (
          <Stack spacing={1.5}>
            {items.slice(0, maxItems).map((item, index) => (
              <Box key={index}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                  <Typography
                    variant="body2"
                    sx={{
                      maxWidth: '70%',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap'
                    }}
                    title={item.name}
                  >
                    {item.name}
                  </Typography>
                  <Chip label={item.count.toLocaleString('en-US')} size="small" color={color} variant="outlined" />
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={(item.count / maxCount) * 100}
                  sx={{
                    height: '0.375rem',
                    borderRadius: '0.1875rem',
                    backgroundColor: `${theme.palette[color]?.main || theme.palette.primary.main}20`,
                    '& .MuiLinearProgress-bar': {
                      borderRadius: '0.1875rem',
                      backgroundColor: theme.palette[color]?.main || theme.palette.primary.main
                    }
                  }}
                />
              </Box>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
};

/**
 * VisitsInsights Component
 *
 * Displays insights cards: Top Providers, Top Services, Top Members
 *
 * @param {Object} insights - Insights data
 * @param {boolean} loading - Loading state
 */
const VisitsInsights = ({ insights, loading }) => {
  return (
    <Grid container spacing={2} sx={{ mt: '1.0rem' }}>
      {/* Top 5 Providers */}
      <Grid size={{ xs: 12, md: 4 }}>
        <InsightCard
          title="أكثر 5 مقدمي خدمة زيارةً"
          icon={LocalHospitalIcon}
          items={insights.topProviders}
          loading={loading}
          color="primary"
          maxItems={5}
        />
      </Grid>

      {/* Top 5 Services */}
      <Grid size={{ xs: 12, md: 4 }}>
        <InsightCard
          title="أكثر 5 خدمات استخدامًا"
          icon={MedicalServicesIcon}
          items={insights.topServices}
          loading={loading}
          color="info"
          maxItems={5}
        />
      </Grid>

      {/* Top 10 Members */}
      <Grid size={{ xs: 12, md: 4 }}>
        <InsightCard
          title="أكثر 10 أعضاء زيارةً"
          icon={PersonIcon}
          items={insights.topMembers}
          loading={loading}
          color="success"
          maxItems={10}
        />
      </Grid>
    </Grid>
  );
};

VisitsInsights.propTypes = {
  insights: PropTypes.shape({
    topProviders: PropTypes.arrayOf(
      PropTypes.shape({
        name: PropTypes.string,
        count: PropTypes.number
      })
    ),
    topServices: PropTypes.arrayOf(
      PropTypes.shape({
        name: PropTypes.string,
        count: PropTypes.number
      })
    ),
    topMembers: PropTypes.arrayOf(
      PropTypes.shape({
        name: PropTypes.string,
        count: PropTypes.number
      })
    )
  }).isRequired,
  loading: PropTypes.bool
};

VisitsInsights.defaultProps = {
  loading: false
};

export default VisitsInsights;
