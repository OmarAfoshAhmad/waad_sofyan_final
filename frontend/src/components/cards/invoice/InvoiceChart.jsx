import PropTypes from 'prop-types';
import { useState, useEffect } from 'react';

// material-ui
import { useColorScheme, useTheme } from '@mui/material/styles';

import ReactApexChart from 'react-apexcharts';

// project imports
import { ThemeMode } from 'config';
import useConfig from 'hooks/useConfig';

// chart options
const areaChartOptions = {
  chart: {
    id: 'invoice-state',
    toolbar: { show: false },
    sparkline: { enabled: true },
    background: 'transparent'
  },
  dataLabels: { enabled: false },
  stroke: { width: '0.0625rem', curve: 'smooth' },
  grid: { show: false },
  yaxis: { show: false, stepSize: 50 },
  tooltip: {
    x: { show: false },
    y: {
      formatter(val) {
        return `$${val}`;
      }
    }
  }
};

// ==============================|| INVOICE - CHART ||============================== //

export default function InvoiceChart({ color, data }) {
  const theme = useTheme();
  const { colorScheme } = useColorScheme();
  const {
    state: { fontFamily }
  } = useConfig();

  const backgroundPaper = theme.vars.palette.background.paper;

  const [options, setOptions] = useState(areaChartOptions);

  useEffect(() => {
    setOptions({
      ...areaChartOptions,
      chart: { ...areaChartOptions.chart, fontFamily: fontFamily },
      colors: [color.main],
      fill: {
        gradient: {
          colorStops: [
            [
              { offset: 0, color: color.main, opacity: 0.25 },
              { offset: 100, color: backgroundPaper, opacity: 0 }
            ]
          ]
        }
      },
      theme: { mode: colorScheme === ThemeMode.DARK ? 'dark' : 'light' }
    });
  }, [colorScheme, fontFamily, color, backgroundPaper]);

  const [series] = useState([{ name: 'Sales', data: data }]);

  return <ReactApexChart options={options} series={series} type="area" height={80} />;
}

InvoiceChart.propTypes = { color: PropTypes.any, data: PropTypes.any };
