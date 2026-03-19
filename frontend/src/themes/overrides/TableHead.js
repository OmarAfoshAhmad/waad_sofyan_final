// ==============================|| OVERRIDES - TABLE HEAD ||============================== //

export default function TableHead(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiTableHead: {
      styleOverrides: {
        root: {
          backgroundColor: 'var(--tba-th-bg, #E0F2F1)',
          color: 'var(--tba-th-text, #004D50)',
          borderTop: '1px solid',
          borderTopColor: varsPalette.divider ?? theme.palette.divider,
          borderBottom: '2px solid',
          borderBottomColor: 'var(--tba-th-text, #00838F)',
          '& .MuiTableCell-root': {
            color: 'var(--tba-th-text, #004D50)'
          }
        }
      }
    }
  };
}
