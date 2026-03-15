import { memo } from 'react';
import PropTypes from 'prop-types';

// material-ui
import { Box, Card, CardContent, Stack, Typography, useTheme } from '@mui/material';

// third-party
import { Line } from 'react-chartjs-2';
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler } from 'chart.js';

// Register ChartJS components
ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler);

/**
 * KpiCard Component
 * Phase B3 - Modern Dashboard KPI Card with mini sparkline chart
 *
 * Features:
 * - Theme-aware colors (respects Mantis 8 themes)
 * - Mini sparkline chart at bottom
 * - RTL support
 * - Responsive design
 */
const KpiCard = memo(({ title, value, icon: Icon, color = 'primary', sparklineData = [] }) => {
  const theme = useTheme();

  // Get color from theme palette
  const getColor = (colorKey) => {
    const colors = {
      primary: theme.palette.primary.main,
      success: theme.palette.success.main,
      error: theme.palette.error.main,
      warning: theme.palette.warning.main,
      info: theme.palette.info.main
    };
    return colors[colorKey] || colors.primary;
  };

  const mainColor = getColor(color);
  const lightColor = `${mainColor}15`; // 15% opacity

  // Default sparkline data if none provided
  const defaultSparklineData = [12, 19, 15, 25, 22, 30, 28, 35, 32, 40, 38, 45];
  const chartData = sparklineData.length > 0 ? sparklineData : defaultSparklineData;

  // Sparkline chart configuration
  const sparklineConfig = {
    labels: chartData.map((_, i) => i),
    datasets: [
      {
        data: chartData,
        borderColor: mainColor,
        backgroundColor: `${mainColor}20`,
        borderWidth: 2,
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 0
      }
    ]
  };

  const sparklineOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { enabled: false }
    },
    scales: {
      x: { display: false },
      y: { display: false }
    },
    elements: {
      line: { borderWidth: 2 }
    }
  };

  return (
    <Card
      sx={{
        height: '100%',
        background:
          theme.palette.mode === 'dark'
            ? `linear-gradient(135deg, ${theme.palette.background.paper} 0%, ${lightColor} 100%)`
            : `linear-gradient(135deg, ${theme.palette.background.paper} 0%, ${lightColor} 100%)`,
        transition: 'all 0.3s ease',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: theme.shadows[8]
        }
      }}
    >
      <CardContent>
        <Stack spacing={2}>
          {/* Header with icon and title */}
          <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
            <Box
              sx={{
                width: '2.75rem',
                height: '2.75rem',
                borderRadius: '0.25rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: lightColor,
                color: mainColor
              }}
            >
              {Icon && <Icon style={{ fontSize: '1.5rem' }} />}
            </Box>
          </Stack>

          {/* Title */}
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
            {title}
          </Typography>

          {/* Value */}
          <Typography variant="h3" sx={{ fontWeight: 700, color: theme.palette.text.primary }}>
            {value?.toLocaleString('en-US') || 0}
          </Typography>

          {/* Mini Sparkline Chart */}
          <Box sx={{ height: '3.125rem', mt: 1 }}>
            <Line data={sparklineConfig} options={sparklineOptions} />
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
});

KpiCard.displayName = 'KpiCard';

KpiCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.number,
  icon: PropTypes.elementType,
  color: PropTypes.oneOf(['primary', 'success', 'error', 'warning', 'info']),
  sparklineData: PropTypes.arrayOf(PropTypes.number)
};

export default KpiCard;
