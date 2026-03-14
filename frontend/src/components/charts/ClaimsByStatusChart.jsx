import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import ReactApexChart from 'react-apexcharts';
import { useTheme } from '@mui/material/styles';
import { Box, Typography, Stack, Skeleton } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'utils/axios';

// ==============================|| CLAIMS BY STATUS CHART ||============================== //

export default function ClaimsByStatusChart({ height = 365 }) {
  const theme = useTheme();

  const { data: chartData, isLoading } = useQuery({
    queryKey: ['claimsByStatus'],
    queryFn: async () => {
      const response = await axios.get('/dashboard/claims-by-status');
      return response.data;
    },
    initialData: {
      labels: ['Pending', 'Approved', 'Rejected'],
      values: [0, 0, 0]
    }
  });

  const [options, setOptions] = useState({
    chart: {
      type: 'donut',
      height: height
    },
    labels: chartData?.labels || [],
    colors: [theme.palette.warning.main, theme.palette.success.main, theme.palette.error.main],
    legend: {
      show: true,
      position: 'bottom',
      fontFamily: theme.typography.fontFamily
    },
    dataLabels: {
      enabled: true,
      formatter: function (val) {
        return val.toFixed(1) + '%';
      }
    },
    plotOptions: {
      pie: {
        donut: {
          size: '65%'
        }
      }
    },
    tooltip: {
      theme: 'light'
    }
  });

  const [series, setSeries] = useState(chartData?.values || []);

  useEffect(() => {
    if (chartData) {
      setOptions((prevState) => ({
        ...prevState,
        labels: chartData.labels
      }));
      setSeries(chartData.values);
    }
  }, [chartData]);

  if (isLoading) {
    return (
      <Box sx={{ p: '1.5rem' }}>
        <Skeleton variant="circular" width={height} height={height} sx={{ mx: 'auto' }} />
      </Box>
    );
  }

  return (
    <Box sx={{ p: '1.0rem' }}>
      <Stack spacing={1} sx={{ mb: '1.0rem' }}>
        <Typography variant="h6">Claims by Status</Typography>
        <Typography variant="caption" color="text.secondary">
          المطالبات حسب الحالة
        </Typography>
      </Stack>
      <ReactApexChart options={options} series={series} type="donut" height={height} />
    </Box>
  );
}

ClaimsByStatusChart.propTypes = {
  height: PropTypes.number
};
