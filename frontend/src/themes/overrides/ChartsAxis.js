// ==============================|| OVERRIDES - CHART AXIS ||============================== //

export default function ChartsAxis(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiChartsAxis: {
      styleOverrides: {
        root: {
          '& .MuiChartsAxis-line': {
            stroke: varsPalette.grey?.[300] ?? theme.palette.grey?.[300],
            strokeWidth: 1
          },
          '& .MuiChartsAxis-tick': {
            stroke: varsPalette.grey?.[300] ?? theme.palette.grey?.[300]
          },
          '&.MuiChartsAxis-directionX .MuiChartsAxis-tick': {
            stroke: varsPalette.grey?.[300] ?? theme.palette.grey?.[300]
          },
          '&.MuiChartsAxis-directionY .MuiChartsAxis-tick': {
            stroke: varsPalette.grey?.[300] ?? theme.palette.grey?.[300]
          },
          '& .MuiChartsAxis-tickLabel': {
            fontSize: '0.75rem',
            fill: varsPalette.text?.secondary ?? theme.palette.text?.secondary
          }
        }
      }
    }
  };
}


