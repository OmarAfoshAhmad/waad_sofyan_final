import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Card, CardContent, CardHeader, Box, Skeleton, useTheme } from '@mui/material';
import ReactApexChart from 'react-apexcharts';
import { useColorScheme } from '@mui/material/styles';
import { ThemeMode } from 'config';
import useConfig from 'hooks/useConfig';
import { formatCurrency, formatNumber } from 'utils/currency-formatter';

/**
 * Bar Chart: التكاليف حسب مقدم الخدمة
 * Using ApexCharts
 */
const CostsBarChart = ({ data, loading }) => {
  const theme = useTheme();
  const { colorScheme } = useColorScheme();
  const {
    state: { fontFamily }
  } = useConfig();

  const [options, setOptions] = useState({
    chart: {
      id: 'costs-by-provider',
      toolbar: { show: true },
      fontFamily: fontFamily,
      background: 'transparent'
    },
    plotOptions: {
      bar: {
        horizontal: true,
        borderRadius: '0.375rem',
        dataLabels: {
          position: 'right'
        }
      }
    },
    dataLabels: {
      enabled: true,
      formatter: (val) => formatNumber(val),
      style: {
        colors: [theme.palette.text.primary],
        fontFamily: fontFamily
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
        },
        formatter: (val) => formatNumber(val)
      }
    },
    yaxis: {
      labels: {
        style: {
          colors: theme.palette.text.secondary,
          fontFamily: fontFamily
        }
      }
    },
    colors: [theme.palette.info.main],
    tooltip: {
      theme: colorScheme === ThemeMode.DARK ? 'dark' : 'light',
      y: {
        formatter: (val) => formatCurrency(val)
      }
    }
  });

  const [series, setSeries] = useState([
    {
      name: 'التكلفة',
      data: []
    }
  ]);

  useEffect(() => {
    if (data && data.length > 0) {
      const categories = data.map((item) => item.providerName || item.name || 'غير محدد');
      const values = data.map((item) => item.cost || item.amount || 0);

      setOptions((prev) => ({
        ...prev,
        xaxis: {
          ...prev.xaxis,
          categories
        }
      }));

      setSeries([
        {
          name: 'التكلفة',
          data: values
        }
      ]);
    }
  }, [data]);

  if (loading) {
    return (
      <Card>
        <CardHeader title="التكاليف حسب مقدم الخدمة" />
        <CardContent>
          <Skeleton variant="rectangular" height={350} />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="التكاليف حسب مقدم الخدمة" subheader="أعلى 10 مقدمي خدمة" />
      <CardContent>
        <Box sx={{ direction: 'rtl' }}>
          <ReactApexChart options={options} series={series} type="bar" height={350} />
        </Box>
      </CardContent>
    </Card>
  );
};

CostsBarChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      providerName: PropTypes.string,
      name: PropTypes.string,
      cost: PropTypes.number,
      amount: PropTypes.number
    })
  ),
  loading: PropTypes.bool
};

export default CostsBarChart;

