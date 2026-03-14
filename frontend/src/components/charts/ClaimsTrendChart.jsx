import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import ReactApexChart from 'react-apexcharts';
import { useTheme } from '@mui/material/styles';
import { Box, Typography, Stack, Skeleton } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'utils/axios';

// ==============================|| CLAIMS TREND CHART ||============================== //

export default function ClaimsTrendChart({ height = 365 }) {
  const theme = useTheme();
  const { primary, secondary } = theme.palette.text;
  const line = theme.palette.divider;

  const { data: chartData, isLoading } = useQuery({
    queryKey: ['claimsTrend'],
    queryFn: async () => {
      const response = await axios.get('/dashboard/claims-trend');
      return response.data;
    },
    initialData: {
      months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      claims: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    }
  });

  const [options, setOptions] = useState({
    chart: {
      type: 'line',
      height: height,
      toolbar: {
        show: false
      }
    },
    dataLabels: {
      enabled: false
    },
    stroke: {
      curve: 'smooth',
      width: '0.125rem'
    },
    grid: {
      borderColor: line
    },
    xaxis: {
      categories: chartData?.months || [],
      labels: {
        style: {
          colors: secondary
        }
      }
    },
    yaxis: {
      labels: {
        style: {
          colors: secondary
        }
      }
    },
    colors: [theme.palette.primary.main],
    tooltip: {
      theme: 'light'
    }
  });

  const [series, setSeries] = useState([
    {
      name: 'Claims',
      data: chartData?.claims || []
    }
  ]);

  useEffect(() => {
    if (chartData) {
      setOptions((prevState) => ({
        ...prevState,
        xaxis: {
          ...prevState.xaxis,
          categories: chartData.months
        }
      }));
      setSeries([
        {
          name: 'المطالبات',
          data: chartData.claims
        }
      ]);
    }
  }, [chartData]);

  if (isLoading) {
    return (
      <Box sx={{ p: '1.5rem' }}>
        <Skeleton variant="rectangular" height={height} />
      </Box>
    );
  }

  return (
    <Box sx={{ p: '1.0rem' }}>
      <Stack spacing={1} sx={{ mb: '1.0rem' }}>
        <Typography variant="h6">Claims Trend</Typography>
        <Typography variant="caption" color="text.secondary">
          اتجاه المطالبات الشهرية
        </Typography>
      </Stack>
      <ReactApexChart options={options} series={series} type="line" height={height} />
    </Box>
  );
}

ClaimsTrendChart.propTypes = {
  height: PropTypes.number
};
