import PropTypes from 'prop-types';
import { Card, CardContent, Stack, Typography, Box, Avatar, Skeleton, useTheme, alpha } from '@mui/material';
import { TrendingUp, TrendingDown, Remove } from '@mui/icons-material';

/**
 * Professional Summary Card Component
 * Inspired by Mantis Invoice Dashboard
 */
const SummaryCard = ({ title, value, subLabel, subValue, icon: Icon, color = 'primary', loading, trend }) => {
  const theme = useTheme();

  const getTrendIcon = () => {
    if (trend === undefined || trend === null) return null;
    if (trend > 0) return <TrendingUp sx={{ fontSize: '1.0rem', color: 'success.main' }} />;
    if (trend < 0) return <TrendingDown sx={{ fontSize: '1.0rem', color: 'error.main' }} />;
    return <Remove sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />;
  };

  const getTrendColor = () => {
    if (trend === undefined || trend === null) return 'text.secondary';
    if (trend > 0) return 'success.main';
    if (trend < 0) return 'error.main';
    return 'text.secondary';
  };

  return (
    <Card
      sx={{
        height: '100%',
        borderRadius: '0.25rem',
        boxShadow: `0 1px 3px 0 ${alpha(theme.palette.common.black, 0.1)}`,
        transition: 'all 0.2s',
        '&:hover': {
          boxShadow: `0 4px 6px -1px ${alpha(theme.palette.common.black, 0.1)}, 0 2px 4px -1px ${alpha(theme.palette.common.black, 0.06)}`
        },
        position: 'relative',
        overflow: 'hidden'
      }}
    >
      {/* Left Accent Bar */}
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: '0.375rem',
          bgcolor: `${color}.main`
        }}
      />

      <CardContent sx={{ p: '1.5rem', pl: '2.0rem' }}>
        <Stack spacing={2}>
          {/* Header */}
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {title}
              </Typography>
              {loading ? (
                <Skeleton variant="text" width={80} height={40} />
              ) : (
                <Typography variant="h3" fontWeight={700} color="text.primary">
                  {typeof value === 'number' ? value.toLocaleString('en-US') : (value ?? '—')}
                </Typography>
              )}
            </Box>
            {Icon && (
              <Avatar
                sx={{
                  bgcolor: alpha(theme.palette[color].main, 0.1),
                  color: `${color}.main`,
                  width: '3.5rem',
                  height: '3.5rem'
                }}
              >
                <Icon sx={{ fontSize: '1.75rem' }} />
              </Avatar>
            )}
          </Stack>

          {/* Sub Label & Trend */}
          {(subLabel || trend !== undefined) && (
            <Stack direction="row" alignItems="center" spacing={1}>
              {subLabel && subValue !== undefined && (
                <Typography variant="body2" color="text.secondary">
                  {subLabel}: <strong>{subValue}</strong>
                </Typography>
              )}
              {trend !== undefined && trend !== null && (
                <Stack direction="row" alignItems="center" spacing={0.5}>
                  {getTrendIcon()}
                  <Typography variant="body2" color={getTrendColor()} fontWeight={600}>
                    {trend > 0 ? '+' : ''}
                    {trend}%
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    من الشهر الماضي
                  </Typography>
                </Stack>
              )}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

SummaryCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  subLabel: PropTypes.string,
  subValue: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  icon: PropTypes.elementType,
  color: PropTypes.oneOf(['primary', 'secondary', 'success', 'error', 'warning', 'info']),
  loading: PropTypes.bool,
  trend: PropTypes.number
};

export default SummaryCard;

