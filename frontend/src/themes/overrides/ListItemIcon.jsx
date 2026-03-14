// ==============================|| OVERRIDES - LIST ITEM ICON ||============================== //

export default function ListItemIcon(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  return {
    MuiListItemIcon: {
      styleOverrides: {
        root: {
          minWidth: 24.0,
          color: varsPalette.text?.primary ?? theme.palette.text?.primary
        }
      }
    }
  };
}
