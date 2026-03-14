// ==============================|| OVERRIDES - CHART TOOLTIP ||============================== //

export default function ChartTooltip(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiChartsTooltip: {
      styleOverrides: {
        container: {
          overflow: 'hidden'
        },
        root: { '& div.MuiChartsTooltip-markContainer': { width: 24.0 } },
        table: {
          borderSpacing: '0 8px',
          '& caption': {
            color: varsPalette.text?.primary ?? theme.palette.text?.primary,
            backgroundColor: varsPalette.background?.default ?? theme.palette.background?.default
          }
        },
        cell: { lineHeight: 1 }
      }
    }
  };
}
