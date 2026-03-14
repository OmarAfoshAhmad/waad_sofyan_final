import PropTypes from 'prop-types';
import { useState, useEffect } from 'react';

// material-ui
import { useTheme } from '@mui/material/styles';
import { LineChart } from '@mui/x-charts';

// project imports
import { withAlpha } from 'utils/colorUtils';

const weekLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const monthLabels = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const byWeekVolume = [100, 20, 60, 20, 20, 80, 20];
const byWeekMargin = [51, 40, 28, 51, 42, 109, 100];
const byWeekSales = [21, 40, 28, 51, 42, 109, 100];

const byMonthVolume = [100, 40, 60, 40, 40, 40, 80, 40, 40, 50, 40, 40];
const byMonthSales = [90, 85, 101, 98, 87, 105, 91, 114, 94, 86, 115, 35];
const byMonthMargin = [120, 85, 101, 98, 87, 105, 91, 114, 94, 86, 115, 35];

// ==============================|| ANALYTICS - INCOME ||============================== //

export default function IncomeChart({ slot, quantity }) {
  const theme = useTheme();

  const [data, setData] = useState(byMonthVolume);
  const [labels, setLabels] = useState(monthLabels);

  useEffect(() => {
    switch (slot) {
      case 'week':
        setData(quantity === 'By margin' ? byWeekMargin : quantity === 'By sales' ? byWeekSales : byWeekVolume);
        setLabels(weekLabels);
        break;
      case 'month':
        setData(quantity === 'By margin' ? byMonthMargin : quantity === 'By sales' ? byMonthSales : byMonthVolume);
        setLabels(monthLabels);
        break;
    }
  }, [slot, quantity]);

  return (
    <LineChart
      hideLegend
      grid={{ horizontal: true, vertical: true }}
      xAxis={[{ data: labels, scaleType: 'point', disableLine: true, tickSize: 7 }]}
      yAxis={[{ disableLine: true, tickMaxStep: '10.0rem', tickSize: 7 }]}
      series={[
        {
          curve: 'linear',
          data,
          showMark: false,
          area: true,
          id: 'IncomeChart',
          color: theme.vars.palette.primary.main,
          label: 'Income',
          valueFormatter: (value) => `$ ${value}`
        }
      ]}
      height={355}
      margin={{ top: '15.0rem', bottom: '12.5rem', left: 0, right: '1.375rem' }}
      sx={{
        '& .MuiLineElement-root': { strokeDasharray: 0, strokeWidth: 1 },
        '& .MuiAreaElement-series-IncomeChart': { fill: `url('#myGradient3')`, paintOrder: 'stroke' },
        '& .MuiChartsAxis-tick': { stroke: theme.vars.palette.divider },
        '& .MuiChartsAxis-root.MuiChartsAxis-directionX .MuiChartsAxis-tick': { stroke: theme.vars.palette.divider },
        '& .MuiChartsAxis-root.MuiChartsAxis-directionY .MuiChartsAxis-tick': { stroke: 'transparent' }
      }}
    >
      <defs>
        <linearGradient id="myGradient3" gradientTransform="rotate(90)">
          <stop offset="10%" stopColor={withAlpha(theme.vars.palette.primary.main, 0.2)} />
          <stop offset="80%" stopColor={withAlpha(theme.vars.palette.background.default, 0.4)} />
        </linearGradient>
      </defs>
    </LineChart>
  );
}

IncomeChart.propTypes = { slot: PropTypes.oneOf(['week', 'month']), quantity: PropTypes.oneOf(['By margin', 'By sales', 'By volume']) };
