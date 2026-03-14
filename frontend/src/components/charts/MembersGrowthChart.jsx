import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import ReactApexChart from 'react-apexcharts';
import { useTheme } from '@mui/material/styles';
import { Box, Typography, Stack, Skeleton } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'utils/axios';

// ==============================|| MEMBERS GROWTH CHART ||============================== //

export default function MembersGrowthChart({ height = 365 }) {
  const theme = useTheme();
  const { primary, secondary } = theme.palette.text;
  const line = theme.palette.divider;

  const { data: chartData, isLoading } = useQuery({
    queryKey: ['membersGrowth'],
    queryFn: async () => {
      const response = await axios.get('/dashboard/members-growth');
      return response.data;
    },
    initialData: {
      months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      members: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    }
  });

  const [options, setOptions] = useState({
    chart: {
      type: 'bar',
      height: height,
      toolbar: {
        show: false
      }
    },
    plotOptions: {
      bar: {
        borderRadius: '0.375rem',
        columnWidth: '45%'
      }
    },
    dataLabels: {
      enabled: false
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
    colors: [theme.palette.success.main],
    tooltip: {
      theme: 'light'
    }
  });

  const [series, setSeries] = useState([
    {
      name: 'Members',
      data: chartData?.members || []
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
          name: 'الأعضاء',
          data: chartData.members
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
        <Typography variant="h6">Members Growth</Typography>
        <Typography variant="caption" color="text.secondary">
          نمو الأعضاء الشهري
        </Typography>
      </Stack>
      <ReactApexChart options={options} series={series} type="bar" height={height} />
    </Box>
  );
}

MembersGrowthChart.propTypes = {
  height: PropTypes.number
};

