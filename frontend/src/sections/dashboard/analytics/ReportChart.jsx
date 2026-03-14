// material-ui
import { useTheme } from '@mui/material/styles';

import { chartsGridClasses, LineChart } from '@mui/x-charts';

const data = [58, 90, 38, 83, 63, 75, 35];
const labels = ['Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// ==============================|| REPORT AREA CHART ||============================== //

export default function ReportChart() {
  const theme = useTheme();

  return (
    <LineChart
      hideLegend
      grid={{ horizontal: true }}
      xAxis={[{ data: labels, scaleType: 'point', disableLine: true, tickSize: 7 }]}
      yAxis={[{ position: 'none' }]}
      series={[
        {
          data,
          showMark: false,
          id: 'ReportAreaChart',
          color: theme.vars.palette.warning.main,
          label: 'Income',
          valueFormatter: (value) => `$ ${value}`
        }
      ]}
      height={340}
      margin={{ top: '15.0rem', bottom: '12.5rem', left: '1.25rem', right: '1.25rem' }}
      sx={{
        '& .MuiLineElement-root': { strokeWidth: 2 },
        [`& .${chartsGridClasses.line}`]: { strokeDasharray: '4 4' },
        '& .MuiChartsAxis-root.MuiChartsAxis-directionX .MuiChartsAxis-tick': { stroke: 'transparent' }
      }}
    />
  );
}
