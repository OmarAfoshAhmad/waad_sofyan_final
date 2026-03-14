import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import ReactApexChart from 'react-apexcharts';
import { useTheme } from '@mui/material/styles';
import { Box, Typography, Stack, Skeleton } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'utils/axios';

// ==============================|| VISITS OVER TIME CHART ||============================== //

export default function VisitsOverTimeChart({ height = 365 }) {
  const theme = useTheme();
  const { primary, secondary } = theme.palette.text;
  const line = theme.palette.divider;

  const { data: chartData, isLoading } = useQuery({
    queryKey: ['visitsOverTime'],
    queryFn: async () => {
      const response = await axios.get('/dashboard/visits-over-time');
      return response.data;
    },
    initialData: {
      months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      visits: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    }
  });

  const [options, setOptions] = useState({
    chart: {
      type: 'area',
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
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.7,
        opacityTo: 0.3,
        stops: [0, 90, 100]
      }
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
    colors: [theme.palette.info.main],
    tooltip: {
      theme: 'light'
    }
  });

  const [series, setSeries] = useState([
    {
      name: 'Visits',
      data: chartData?.visits || []
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
          name: 'الزيارات',
          data: chartData.visits
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
        <Typography variant="h6">Visits Over Time</Typography>
        <Typography variant="caption" color="text.secondary">
          الزيارات عبر الزمن
        </Typography>
      </Stack>
      <ReactApexChart options={options} series={series} type="area" height={height} />
    </Box>
  );
}

VisitsOverTimeChart.propTypes = {
  height: PropTypes.number
};
