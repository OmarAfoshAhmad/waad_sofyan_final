import { Card, CardContent, Typography, Box, Stack, Chip } from '@mui/material';
import { CheckCircle, Pending, Cancel, TrendingUp, TrendingDown, Remove } from '@mui/icons-material';

/**
 * Dashboard statistics card
 */
const DashboardStatCard = ({ title, value, change, icon: Icon, color = 'primary' }) => {
  const isPositive = change > 0;
  const isNegative = change < 0;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          {/* Header with icon */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {title}
              </Typography>
              <Typography variant="h4" fontWeight="bold">
                {value?.toLocaleString('en-US') || 0}
              </Typography>
            </Box>
            {Icon && (
              <Box
                sx={{
                  p: '0.75rem',
                  borderRadius: '0.125rem',
                  bgcolor: `${color}.lighter`,
                  color: `${color}.main`
                }}
              >
                <Icon />
              </Box>
            )}
          </Box>

          {/* Change indicator */}
          {change !== undefined && change !== null && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              {isPositive && <TrendingUp fontSize="small" color="success" />}
              {isNegative && <TrendingDown fontSize="small" color="error" />}
              {!isPositive && !isNegative && <Remove fontSize="small" color="disabled" />}
              <Typography
                variant="body2"
                color={isPositive ? 'success.main' : isNegative ? 'error.main' : 'text.secondary'}
                fontWeight="medium"
              >
                {isPositive && '+'}
                {change}% من الشهر الماضي
              </Typography>
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
};

export default DashboardStatCard;


