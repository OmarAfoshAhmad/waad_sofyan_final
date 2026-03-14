// project imports
import { withAlpha } from 'utils/colorUtils';

// ==============================|| OVERRIDES - TAB ||============================== //

export default function Tab(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const primaryVars = (varsPalette && varsPalette.primary) || theme.palette.primary || {};
  return {
    MuiTab: {
      styleOverrides: {
        root: {
          minHeight: 46.0,
          color: varsPalette.text?.primary ?? theme.palette.text?.primary,
          borderRadius: 4.0,
          '&:hover': {
            backgroundColor: withAlpha(primaryVars.lighter ?? primaryVars.main, 0.6),
            color: primaryVars.main ?? varsPalette.primary?.main ?? theme.palette.primary?.main
          },
          '&:focus-visible': {
            borderRadius: 4.0,
            outline: `2px solid ${varsPalette.secondary?.dark ?? theme.palette.secondary?.dark ?? primaryVars.dark}`,
            outlineOffset: -3
          }
        }
      }
    }
  };
}
