import PropTypes from 'prop-types';
import { Card, CardContent, Stack, Typography, Box } from '@mui/material';
import { TrendingUp, TrendingDown, Remove } from '@mui/icons-material';

/**
 * Modern Statistics Card Component
 * Displays KPI metrics with trend indicators
 */
const ModernStatCard = ({ title, value, trend, trendValue, icon: Icon, color = 'primary', loading = false }) => {
  // Determine trend direction
  const getTrendIcon = () => {
    if (!trend || trend === 0) return <Remove sx={{ fontSize: '1rem' }} />;
    return trend > 0 ? <TrendingUp sx={{ fontSize: '1rem' }} /> : <TrendingDown sx={{ fontSize: '1rem' }} />;
  };

  const getTrendColor = () => {
    if (!trend || trend === 0) return 'text.secondary';
    return trend > 0 ? 'success.main' : 'error.main';
  };

  return (
    <Card
      sx={{
        height: '100%',
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: 3
        }
      }}
    >
      <CardContent>
        <Stack spacing={2}>
          {/* Icon and Title */}
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                {title}
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 600 }}>
                {loading ? '...' : value}
              </Typography>
            </Box>

            {Icon && (
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '3.0rem',
                  height: '3.0rem',
                  borderRadius: '0.375rem',
                  bgcolor: `${color}.lighter`,
                  color: `${color}.main`
                }}
              >
                <Icon sx={{ fontSize: '1.75rem' }} />
              </Box>
            )}
          </Stack>

          {/* Trend */}
          {trendValue && (
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  color: getTrendColor()
                }}
              >
                {getTrendIcon()}
              </Box>
              <Typography
                variant="caption"
                sx={{
                  color: getTrendColor(),
                  fontWeight: 500
                }}
              >
                {trendValue}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                مقارنة بالشهر السابق
              </Typography>
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

ModernStatCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  trend: PropTypes.number,
  trendValue: PropTypes.string,
  icon: PropTypes.elementType,
  color: PropTypes.oneOf(['primary', 'secondary', 'error', 'warning', 'info', 'success']),
  loading: PropTypes.bool
};

export default ModernStatCard;


