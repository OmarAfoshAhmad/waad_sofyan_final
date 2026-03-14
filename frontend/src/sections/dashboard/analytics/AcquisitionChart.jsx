import { useState } from 'react';

// material-ui
import { useTheme } from '@mui/material/styles';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import { BarChart } from '@mui/x-charts';

// project imports
import { withAlpha } from 'utils/colorUtils';

// ==============================|| ANALYTICS - ACQUISITION CHART ||============================== //

export default function AcquisitionChart() {
  const theme = useTheme();

  const xAxisData = [
    '2025-03-26',
    '2025-03-27',
    '2025-03-28',
    '2025-03-29',
    '2025-03-30',
    '2025-03-31',
    '2025-04-01',
    '2025-04-02',
    '2025-04-03',
    '2025-04-04',
    '2025-04-05',
    '2025-04-06',
    '2025-04-07',
    '2025-04-08',
    '2025-04-09',
    '2025-04-10',
    '2025-04-11',
    '2025-04-12'
  ];

  // Safe palette access
  const varsPalette = theme?.vars?.palette || theme.palette || {};
  const primaryVars = varsPalette.primary || theme.palette?.primary || {};
  const greyVars = varsPalette.grey || theme.palette?.grey || {};

  const grey900 = greyVars[900] || '#212121';
  const primaryMain = primaryVars.main || '#1976d2';
  const primary200 = primaryVars[200] || '#90caf9';

  const initialSeries = [
    {
      id: 'Direct',
      label: 'Direct',
      data: [21, 17, 15, 13, 15, 13, 16, 13, 8, 14, 11, 9, 7, 5, 3, 3, 7],
      color: grey900
    },
    {
      id: 'Referral',
      label: 'Referral',
      data: [28, 30, 20, 26, 18, 27, 22, 28, 20, 21, 15, 14, 12, 10, 8, 18, 16],
      color: primaryMain
    },
    {
      id: 'Social',
      label: 'Social',
      data: [50, 51, 60, 54, 53, 48, 55, 40, 44, 42, 44, 44, 42, 40, 42, 32, 16],
      color: primary200
    }
  ];

  // State to manage visibility of each series
  const [seriesVisibility, setSeriesVisibility] = useState({
    Direct: true,
    Referral: true,
    Social: true
  });

  // Function to toggle visibility of a series based on label
  const toggleSeriesVisibility = (seriesLabel) => {
    setSeriesVisibility((prevVisibility) => ({
      ...prevVisibility,
      [seriesLabel]: !prevVisibility[seriesLabel]
    }));
  };

  const [highlightedItem, setHighLightedItem] = useState(null);

  const handleHighLightedSeries = (newHighLightedSeries) => {
    if (newHighLightedSeries !== null) {
      setHighLightedItem((prev) => ({
        ...prev,
        seriesId: newHighLightedSeries
      }));
    }
  };

  return (
    <>
      <BarChart
        hideLegend
        height={230}
        series={initialSeries
          .map((series) => ({
            ...series,
            type: 'bar',
            stack: '1',
            color: withAlpha(series.color, 0.85),
            visible: seriesVisibility[series.label], // Check visibility from state
            highlightScope: { highlight: 'series', fade: 'global' }
          }))
          .filter((series) => series.visible)}
        highlightedItem={highlightedItem}
        xAxis={[{ id: 'acquisition-x-axis', data: xAxisData, scaleType: 'band', position: 'none' }]}
        yAxis={[{ position: 'none' }]}
        axisHighlight={{ x: 'none' }}
        slotProps={{ tooltip: { trigger: 'item' } }}
        margin={{ left: '1.25rem', right: -5, top: '15.0rem', bottom: '7.5rem' }}
        sx={{ '& .MuiBarElement-root:hover': { opacity: 0.6 } }}
      />
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-around', width: '0.0625rem', maxWidth: '15.625rem', ml: '1.0rem', mb: '1.0rem' }}>
        {initialSeries.map((series) => (
          <Stack
            key={series.label}
            direction="row"
            onClick={() => toggleSeriesVisibility(series.label)}
            onMouseEnter={() => handleHighLightedSeries(series.id)}
            onMouseLeave={() => setHighLightedItem(null)}
            sx={{ gap: 1, alignItems: 'center', opacity: seriesVisibility[series.label] ? 1 : 0.45, cursor: 'pointer' }}
          >
            <Box sx={{ height: '0.625rem', width: '0.625rem', borderRadius: '50%', backgroundColor: series.color }} />
            <Typography>{series.label}</Typography>
          </Stack>
        ))}
      </Stack>
    </>
  );
}
