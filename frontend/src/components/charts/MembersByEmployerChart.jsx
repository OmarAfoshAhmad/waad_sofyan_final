import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import ReactApexChart from 'react-apexcharts';
import { useTheme } from '@mui/material/styles';
import { Box, Typography, Stack, Skeleton } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'utils/axios';

// ==============================|| MEMBERS BY EMPLOYER CHART ||============================== //

export default function MembersByEmployerChart({ height = 365 }) {
  const theme = useTheme();

  const { data: chartData, isLoading } = useQuery({
    queryKey: ['membersByEmployer'],
    queryFn: async () => {
      const response = await axios.get('/dashboard/members-by-employer');
      return response.data;
    },
    initialData: {
      labels: ['Employer 1', 'Employer 2', 'Employer 3'],
      values: [0, 0, 0]
    }
  });

  const [options, setOptions] = useState({
    chart: {
      type: 'pie',
      height: height
    },
    labels: chartData?.labels || [],
    colors: [
      theme.palette.primary.main,
      theme.palette.success.main,
      theme.palette.warning.main,
      theme.palette.error.main,
      theme.palette.info.main,
      theme.palette.secondary.main
    ],
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
    tooltip: {
      theme: 'light'
    },
    responsive: [
      {
        breakpoint: 480,
        options: {
          chart: {
            width: '17.5rem'
          },
          legend: {
            position: 'bottom'
          }
        }
      }
    ]
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
        <Typography variant="h6">Members by Employer</Typography>
        <Typography variant="caption" color="text.secondary">
          الأعضاء حسب جهة العمل
        </Typography>
      </Stack>
      <ReactApexChart options={options} series={series} type="pie" height={height} />
    </Box>
  );
}

MembersByEmployerChart.propTypes = {
  height: PropTypes.number
};
