import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Card, CardContent, CardHeader, Box, Skeleton, useTheme } from '@mui/material';
import ReactApexChart from 'react-apexcharts';
import { useColorScheme } from '@mui/material/styles';
import { ThemeMode } from 'config';
import useConfig from 'hooks/useConfig';

/**
 * Area Chart: نمو الأعضاء
 * Using ApexCharts
 */
const MembersAreaChart = ({ data, loading }) => {
  const theme = useTheme();
  const { colorScheme } = useColorScheme();
  const {
    state: { fontFamily }
  } = useConfig();

  const [options, setOptions] = useState({
    chart: {
      id: 'members-growth',
      toolbar: { show: true },
      fontFamily: fontFamily,
      background: 'transparent'
    },
    dataLabels: { enabled: false },
    stroke: {
      curve: 'smooth',
      width: '0.125rem'
    },
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.7,
        opacityTo: 0.3,
        stops: [0, 90, 100],
        colorStops: [
          [
            { offset: 0, color: theme.palette.success.main, opacity: 0.7 },
            { offset: 100, color: theme.palette.success.main, opacity: 0.1 }
          ]
        ]
      }
    },
    grid: {
      borderColor: theme.palette.divider,
      strokeDashArray: 3
    },
    xaxis: {
      categories: [],
      labels: {
        style: {
          colors: theme.palette.text.secondary,
          fontFamily: fontFamily
        }
      }
    },
    yaxis: {
      labels: {
        style: {
          colors: theme.palette.text.secondary,
          fontFamily: fontFamily
        },
        formatter: (val) => val.toLocaleString('en-US')
      }
    },
    colors: [theme.palette.success.main],
    tooltip: {
      theme: colorScheme === ThemeMode.DARK ? 'dark' : 'light',
      y: {
        formatter: (val) => val.toLocaleString('en-US')
      }
    },
    legend: {
      show: true,
      position: 'top',
      horizontalAlign: 'right',
      fontFamily: fontFamily
    }
  });

  const [series, setSeries] = useState([
    {
      name: 'الأعضاء',
      data: []
    }
  ]);

  useEffect(() => {
    if (data && data.length > 0) {
      const categories = data.map((item) => {
        const date = new Date(item.date || item.month);
        return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
      });
      const values = data.map((item) => item.count || item.value || 0);

      setOptions((prev) => ({
        ...prev,
        xaxis: {
          ...prev.xaxis,
          categories
        }
      }));

      setSeries([
        {
          name: 'الأعضاء',
          data: values
        }
      ]);
    }
  }, [data]);

  if (loading) {
    return (
      <Card>
        <CardHeader title="نمو الأعضاء" />
        <CardContent>
          <Skeleton variant="rectangular" height={350} />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="نمو الأعضاء" subheader="آخر 12 شهر" />
      <CardContent>
        <Box sx={{ direction: 'rtl' }}>
          <ReactApexChart options={options} series={series} type="area" height={350} />
        </Box>
      </CardContent>
    </Card>
  );
};

MembersAreaChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      date: PropTypes.string,
      month: PropTypes.string,
      count: PropTypes.number,
      value: PropTypes.number
    })
  ),
  loading: PropTypes.bool
};

export default MembersAreaChart;
