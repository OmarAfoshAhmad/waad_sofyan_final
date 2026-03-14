// ==============================|| OVERRIDES - INPUT LABEL ||============================== //

export default function InputLabel(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiInputLabel: {
      styleOverrides: {
        root: {
          color: varsPalette.grey?.[600] ?? theme.palette.grey?.[600]
        },
        outlined: {
          '&.MuiInputLabel-shrink': {
            background: varsPalette.background?.paper ?? theme.palette.background?.paper,
            padding: '0 8px',
            marginLeft: -6
          }
        }
      }
    }
  };
}
