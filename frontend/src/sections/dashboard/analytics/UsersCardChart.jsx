// material-ui
import { useTheme } from '@mui/material/styles';

import { BarChart } from '@mui/x-charts/BarChart';

// project imports
import { withAlpha } from 'utils/colorUtils';

const data = [
  220, 230, 240, 220, 225, 215, 205, 195, 185, 150, 185, 195, 80, 205, 215, 225, 240, 225, 215, 205, 80, 215, 225, 240, 215, 210, 190
];

const xAxisData = [
  '2025-03-26',
  '2025-03-27',
  '2025-03-28',
  '2025-03-29',
  '2025-03-30',
  '2025-03-31',
  '2025-04-01',
  '2025-04-02',
  '2025-04-03',
  '2025-04-04',
  '2025-04-05',
  '2025-04-06',
  '2025-04-07',
  '2025-04-08',
  '2025-04-09',
  '2025-04-10',
  '2025-04-11',
  '2025-04-12',
  '2025-04-13',
  '2025-04-14',
  '2025-04-15',
  '2025-04-16',
  '2025-04-17',
  '2025-04-18',
  '2025-04-19',
  '2025-04-20',
  '2025-04-21'
];

// ==============================|| ANALYTICS - USERS ||============================== //

export default function UsersCardChart() {
  const theme = useTheme();

  return (
    <BarChart
      hideLegend
      height={100}
      series={[{ data, label: 'Users', color: withAlpha(theme.vars.palette.primary.main, 0.85) }]}
      xAxis={[{ id: 'usercard-x-axis', data: xAxisData, scaleType: 'band', position: 'none' }]}
      yAxis={[{ position: 'none' }]}
      axisHighlight={{ x: 'none' }}
      slotProps={{ tooltip: { trigger: 'item', sx: { '& .MuiChartsTooltip-root': { border: '1px solid ', borderColor: 'grey.200' } } } }}
      margin={{ top: -49, bottom: 0, left: '0.3125rem', right: '0.3125rem' }}
      sx={{ '& .MuiBarElement-root:hover': { opacity: 0.6 } }}
    />
  );
}
